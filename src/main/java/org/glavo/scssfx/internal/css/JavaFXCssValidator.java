// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.ClassSelector;
import org.glavo.scssfx.internal.ast.selector.Combinator;
import org.glavo.scssfx.internal.ast.selector.IdSelector;
import org.glavo.scssfx.internal.ast.selector.PseudoSelector;
import org.glavo.scssfx.internal.ast.selector.TypeSelector;
import org.glavo.scssfx.internal.ast.selector.UniversalSelector;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/// Validates CSS IR against the syntax and property support of a JavaFX release.
///
/// Validation is implemented without loading JavaFX classes. It rejects CSS
/// constructs that JavaFX does not interpret and version-specific declarations
/// whose meaning would otherwise be silently lost.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXCssValidator {
    /// Contains transition properties introduced after JavaFX 17.
    private static final @Unmodifiable Set<String> TRANSITION_PROPERTIES = Set.of(
            "transition",
            "transition-delay",
            "transition-duration",
            "transition-property",
            "transition-timing-function"
    );

    /// Contains blend modes introduced after JavaFX 17.
    private static final @Unmodifiable Set<String> NEW_BLEND_MODES = Set.of(
            "blue",
            "green",
            "red"
    );

    /// Prevents instantiation.
    private JavaFXCssValidator() {
    }

    /// Validates a stylesheet for the selected JavaFX compatibility level.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param compatibility the JavaFX release whose CSS behavior is targeted
    /// @throws CssSerializeException if the stylesheet contains an unsupported
    /// construct, import condition, media query, property, or property value
    public static void validate(
            CssStylesheet stylesheet,
            JavaFXCompatibility compatibility
    ) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        Objects.requireNonNull(compatibility, "compatibility");
        validateChildren(stylesheet, compatibility, false);
    }

    /// Validates the ordered children of one CSS parent.
    ///
    /// @param parent the parent whose children are validated
    /// @param compatibility the selected JavaFX compatibility level
    /// @param insideStyleRule whether the parent is nested below a style rule
    private static void validateChildren(
            CssParentNode parent,
            JavaFXCompatibility compatibility,
            boolean insideStyleRule
    ) {
        for (var child : parent.children()) {
            validateNode(child, compatibility, insideStyleRule);
        }
    }

    /// Validates one CSS node and recursively validates supported parents.
    ///
    /// @param node the node to validate
    /// @param compatibility the selected JavaFX compatibility level
    /// @param insideStyleRule whether the node is nested below a style rule
    private static void validateNode(
            CssNode node,
            JavaFXCompatibility compatibility,
            boolean insideStyleRule
    ) {
        if (node.isInvisible()) {
            return;
        }
        if (node instanceof CssImport cssImport) {
            if (!(cssImport.parent() instanceof CssStylesheet)) {
                throw failure(
                        "JavaFX CSS supports @import only at the stylesheet root.",
                        cssImport.span()
                );
            }
            validateImport(cssImport, compatibility);
            return;
        }
        if (node instanceof CssDeclaration declaration) {
            if (!(declaration.parent() instanceof CssStyleRule)
                    && !(declaration.parent() instanceof CssFontFace)) {
                throw failure(
                        "JavaFX CSS does not support declarations in this context.",
                        declaration.span()
                );
            }
            validateDeclaration(declaration, compatibility);
            return;
        }
        if (node instanceof CssMediaRule mediaRule) {
            if (compatibility == JavaFXCompatibility.JAVA_FX_17) {
                throw failure(
                        "JavaFX 17 CSS does not support @media rules.",
                        mediaRule.span()
                );
            }
            JavaFXMediaQueryValidator.validate(
                    serializeMediaQueries(mediaRule),
                    mediaRule.span()
            );
            validateChildren(mediaRule, compatibility, insideStyleRule);
            return;
        }
        if (node instanceof CssSupportsRule supportsRule) {
            throw failure(
                    "JavaFX CSS does not support @supports rules.",
                    supportsRule.span()
            );
        }
        if (node instanceof CssUnknownAtRule unknownAtRule) {
            throw failure(
                    "JavaFX CSS does not support @"
                            + unknownAtRule.name()
                            + " rules.",
                    unknownAtRule.span()
            );
        }
        if (node instanceof CssStyleRule styleRule) {
            if (insideStyleRule
                    || !(styleRule.parent() instanceof CssStylesheet)
                    && !(styleRule.parent() instanceof CssMediaRule)) {
                throw failure(
                        "JavaFX CSS does not support native nested style rules.",
                        styleRule.span()
                );
            }
            validateSelector(styleRule);
            validateChildren(styleRule, compatibility, true);
            return;
        }
        if (node instanceof CssFontFace fontFace) {
            if (!(fontFace.parent() instanceof CssStylesheet)) {
                throw failure(
                        "JavaFX CSS supports @font-face only at the stylesheet root.",
                        fontFace.span()
                );
            }
            validateChildren(fontFace, compatibility, insideStyleRule);
        }
    }

    /// Validates the selector subset interpreted by JavaFX.
    ///
    /// JavaFX supports type, universal, class, ID, and non-functional
    /// pseudo-class selectors joined by descendant or child combinators.
    /// Other CSS selector forms would either fail parsing or silently acquire
    /// a different meaning.
    ///
    /// @param rule the visible style rule whose selector is validated
    private static void validateSelector(CssStyleRule rule) {
        for (var complex : rule.selector().value().components()) {
            if (complex.isInvisible() || complex.isBogus()) {
                continue;
            }
            if (!complex.leadingCombinators().isEmpty()) {
                throw unsupportedSelector(complex.span());
            }
            for (var component : complex.components()) {
                for (var combinator : component.combinators()) {
                    if (combinator != Combinator.CHILD) {
                        throw unsupportedSelector(component.span());
                    }
                }
                for (var simple : component.selector().components()) {
                    if (simple instanceof TypeSelector type
                            && type.name().isUnqualified()) {
                        continue;
                    }
                    if (simple instanceof UniversalSelector universal
                            && universal.isUnqualified()) {
                        continue;
                    }
                    if (simple instanceof ClassSelector || simple instanceof IdSelector) {
                        continue;
                    }
                    if (simple instanceof PseudoSelector pseudo
                            && pseudo.isClass()
                            && pseudo.argument() == null) {
                        continue;
                    }
                    throw unsupportedSelector(simple.span());
                }
            }
        }
    }

    /// Creates a failure for a selector outside JavaFX's selector grammar.
    ///
    /// @param span the unsupported selector component
    /// @return the exception to throw
    private static CssSerializeException unsupportedSelector(SourceSpan span) {
        return failure("JavaFX CSS does not support this selector.", span);
    }

    /// Serializes a media-query list for JavaFX grammar validation.
    ///
    /// @param rule the media rule containing the query list
    /// @return the complete comma-separated query-list text
    private static String serializeMediaQueries(CssMediaRule rule) {
        var result = new StringBuilder();
        for (var index = 0; index < rule.queries().size(); index++) {
            if (index != 0) {
                result.append(", ");
            }
            result.append(rule.queries().get(index).toCssString());
        }
        return result.toString();
    }

    /// Validates an import URL and its optional media condition.
    ///
    /// @param cssImport the import to validate
    /// @param compatibility the selected JavaFX compatibility level
    private static void validateImport(
            CssImport cssImport,
            JavaFXCompatibility compatibility
    ) {
        var argument = cssImport.argument();
        var conditionStart = importConditionStart(argument, cssImport.span());
        conditionStart = skipImportTrivia(argument, conditionStart, cssImport.span());
        var condition = argument.substring(conditionStart).strip();
        if (condition.isEmpty()) {
            return;
        }
        if (compatibility == JavaFXCompatibility.JAVA_FX_17) {
            throw failure(
                    "JavaFX 17 CSS supports only unconditional @import rules.",
                    cssImport.span()
            );
        }
        JavaFXMediaQueryValidator.validate(condition, cssImport.span());
    }

    /// Returns the offset immediately after an import's first string or URL token.
    ///
    /// Quoted strings honor CSS escapes. URL functions honor escapes, quoted
    /// substrings, and balanced parentheses so whitespace inside the URL is not
    /// mistaken for the beginning of a media condition.
    ///
    /// @param argument the complete import argument
    /// @param span the import source span used for failures
    /// @return the first offset after the URL token
    /// @throws CssSerializeException if no complete string or URL token is present
    private static int importConditionStart(String argument, SourceSpan span) {
        var start = skipImportTrivia(argument, 0, span);
        if (start >= argument.length()) {
            throw failure("JavaFX CSS requires an @import URL.", span);
        }

        var first = argument.charAt(start);
        if (first == '\'' || first == '"') {
            return quotedTokenEnd(argument, start, first, span);
        }
        if (start + 3 < argument.length()
                && argument.regionMatches(true, start, "url", 0, 3)
                && argument.charAt(start + 3) == '(') {
            return urlTokenEnd(argument, start + 4, span);
        }
        throw failure(
                "JavaFX CSS requires @import to begin with a string or url() token.",
                span
        );
    }

    /// Finds the end of a quoted CSS string token.
    ///
    /// @param text the containing text
    /// @param start the opening quote offset
    /// @param quote the opening quote character
    /// @param span the source span used for failures
    /// @return the offset following the closing quote
    private static int quotedTokenEnd(
            String text,
            int start,
            char quote,
            SourceSpan span
    ) {
        for (var index = start + 1; index < text.length(); index++) {
            var current = text.charAt(index);
            if (current == quote) {
                return index + 1;
            }
            if (current == '\\') {
                index = escapedCodePointEnd(text, index);
            }
        }
        throw failure("JavaFX CSS requires a closed @import string.", span);
    }

    /// Finds the end of a CSS `url(...)` token.
    ///
    /// @param text the containing text
    /// @param start the first offset inside the opening parenthesis
    /// @param span the source span used for failures
    /// @return the offset following the matching closing parenthesis
    private static int urlTokenEnd(String text, int start, SourceSpan span) {
        var depth = 1;
        for (var index = start; index < text.length(); index++) {
            var current = text.charAt(index);
            if (current == '\\') {
                index = escapedCodePointEnd(text, index);
            } else if (current == '\'' || current == '"') {
                index = quotedTokenEnd(text, index, current, span) - 1;
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index + 1;
            }
        }
        throw failure("JavaFX CSS requires a closed @import url() token.", span);
    }

    /// Returns the last offset consumed by one CSS escape.
    ///
    /// A CRLF escape consumes both newline code units.
    ///
    /// @param text the containing text
    /// @param slash the backslash offset
    /// @return the final consumed offset
    private static int escapedCodePointEnd(String text, int slash) {
        if (slash + 1 >= text.length()) {
            return slash;
        }
        if (text.charAt(slash + 1) == '\r'
                && slash + 2 < text.length()
                && text.charAt(slash + 2) == '\n') {
            return slash + 2;
        }
        return slash + 1;
    }

    /// Skips CSS whitespace beginning at the supplied offset.
    ///
    /// @param text the text to inspect
    /// @param start the first candidate offset
    /// @return the first non-whitespace offset or the text length
    private static int skipWhitespace(String text, int start) {
        var index = start;
        while (index < text.length() && isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /// Skips whitespace and block comments between import grammar tokens.
    ///
    /// @param text  the import argument
    /// @param start the first candidate offset
    /// @param span  the import span used for unterminated-comment failures
    /// @return the first non-trivia offset or the text length
    private static int skipImportTrivia(String text, int start, SourceSpan span) {
        var index = start;
        while (true) {
            index = skipWhitespace(text, index);
            if (index + 1 >= text.length()
                    || text.charAt(index) != '/'
                    || text.charAt(index + 1) != '*') {
                return index;
            }
            var end = text.indexOf("*/", index + 2);
            if (end < 0) {
                throw failure("JavaFX CSS requires a closed @import comment.", span);
            }
            index = end + 2;
        }
    }

    /// Returns whether a character is CSS whitespace.
    ///
    /// @param value the character to inspect
    /// @return whether the character is space, tab, line feed, carriage return,
    /// or form feed
    private static boolean isWhitespace(char value) {
        return value == ' '
                || value == '\t'
                || value == '\n'
                || value == '\r'
                || value == '\f';
    }

    /// Validates JavaFX 17 declaration compatibility.
    ///
    /// @param declaration the declaration to validate
    /// @param compatibility the selected JavaFX compatibility level
    private static void validateDeclaration(
            CssDeclaration declaration,
            JavaFXCompatibility compatibility
    ) {
        if (compatibility != JavaFXCompatibility.JAVA_FX_17) {
            return;
        }

        var property = declaration.name().value().toLowerCase(Locale.ROOT);
        if (TRANSITION_PROPERTIES.contains(property)) {
            throw failure(
                    "JavaFX 17 CSS does not support property " + property + ".",
                    declaration.name().span()
            );
        }
        if (property.equals("-fx-blend-mode")) {
            var value = normalizedBlendMode(declarationValueText(declaration));
            if (NEW_BLEND_MODES.contains(value)) {
                throw failure(
                        "JavaFX 17 CSS does not support -fx-blend-mode value "
                                + value
                                + ".",
                        declaration.value().span()
                );
            }
        }
    }

    /// Normalizes a blend-mode token for JavaFX 17 conflict detection.
    ///
    /// The old JavaFX parser resolves the three conflicting identifiers as
    /// colors even when they are quoted or followed by `!important`.
    ///
    /// @param value the emitted declaration value
    /// @return the lowercase unquoted token without an importance suffix
    private static String normalizedBlendMode(String value) {
        var normalized = value.strip();
        var importanceStart = trailingImportanceStart(normalized);
        if (importanceStart >= 0) {
            normalized = normalized.substring(0, importanceStart).stripTrailing();
        }
        if (normalized.length() >= 2) {
            var quote = normalized.charAt(0);
            if ((quote == '\'' || quote == '"')
                    && normalized.charAt(normalized.length() - 1) == quote) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    /// Finds a trailing CSS `!important` suffix.
    ///
    /// @param value the stripped declaration value
    /// @return the suffix start, or `-1` when no suffix is present
    private static int trailingImportanceStart(String value) {
        var importantStart = value.length() - "important".length();
        if (importantStart <= 0
                || !value.regionMatches(
                        true,
                        importantStart,
                        "important",
                        0,
                        "important".length()
                )) {
            return -1;
        }
        var index = importantStart - 1;
        while (index >= 0 && isWhitespace(value.charAt(index))) {
            index--;
        }
        return index >= 0 && value.charAt(index) == '!' ? index : -1;
    }

    /// Returns the CSS text used to inspect a declaration value.
    ///
    /// @param declaration the declaration whose value is inspected
    /// @return the raw value for plain CSS or serialized Sass value text
    private static String declarationValueText(CssDeclaration declaration) {
        if (!declaration.parsedAsSassScript()
                && declaration.value().value() instanceof SassString string) {
            return string.text();
        }
        try {
            return declaration.value().value().toCssString();
        } catch (SassValueException cause) {
            throw new CssSerializeException(
                    "The declaration value cannot be represented in JavaFX CSS.",
                    declaration.value().span(),
                    cause
            );
        }
    }

    /// Creates a CSS serialization failure without an underlying cause.
    ///
    /// @param message the human-readable failure message
    /// @param span the source range responsible for the failure
    /// @return the exception to throw
    private static CssSerializeException failure(String message, SourceSpan span) {
        return new CssSerializeException(message, span, null);
    }
}
