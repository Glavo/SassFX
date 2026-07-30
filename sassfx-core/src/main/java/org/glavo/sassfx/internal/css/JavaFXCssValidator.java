// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.ast.selector.Combinator;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.glavo.sassfx.JavaFXFeature.CSS_TRANSITIONS;
import static org.glavo.sassfx.JavaFXFeature.EXTENDED_BLEND_MODES;
import static org.glavo.sassfx.JavaFXFeature.MULTIPLE_RULES_PER_MEDIA_QUERY;
import static org.glavo.sassfx.JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES;

/// Validates CSS IR against the syntax and property support of a JavaFX release.
///
/// Validation is implemented without loading JavaFX classes. It rejects CSS
/// constructs that JavaFX does not interpret and version-specific declarations
/// whose meaning would otherwise be silently lost.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXCssValidator {
    /// Contains transition properties introduced in JavaFX 23.
    private static final @Unmodifiable Set<String> TRANSITION_PROPERTIES = Set.of(
            "transition",
            "transition-delay",
            "transition-duration",
            "transition-property",
            "transition-timing-function"
    );

    /// Contains blend modes parsed correctly beginning with JavaFX 18.
    private static final @Unmodifiable Set<String> NEW_BLEND_MODES = Set.of(
            "add",
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
            JavaFXTarget compatibility
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
            JavaFXTarget compatibility,
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
            JavaFXTarget compatibility,
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
            if (!compatibility.supports(USER_PREFERENCE_MEDIA_QUERIES)) {
                throw failure(
                        "JavaFX " + compatibility.version()
                                + " CSS does not support @media rules.",
                        mediaRule.span()
                );
            }
            JavaFXMediaQueryValidator.validate(
                    serializeMediaQueries(mediaRule),
                    mediaRule.span(),
                    compatibility
            );
            if (!compatibility.supports(MULTIPLE_RULES_PER_MEDIA_QUERY)
                    && mediaRule.children().stream()
                    .filter(child -> !child.isInvisible())
                    .filter(CssStyleRule.class::isInstance)
                    .skip(1)
                    .findAny()
                    .isPresent()) {
                throw failure(
                        "JavaFX " + compatibility.version()
                                + " CSS cannot apply multiple style rules"
                                + " within one @media rule.",
                        mediaRule.span()
                );
            }
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
    /// JavaFX supports type, universal, class, ID, and pseudo-class selectors
    /// joined by descendant or child combinators. Functional pseudo-class
    /// arguments are limited to JavaFX's identifier-and-string token grammar.
    /// Other CSS selector forms would either fail parsing or silently acquire a
    /// different meaning.
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
            var components = complex.components();
            for (var index = 0; index < components.size(); index++) {
                var component = components.get(index);
                var combinators = component.combinators();
                if (index == components.size() - 1) {
                    if (!combinators.isEmpty()) {
                        throw unsupportedSelector(component.span());
                    }
                } else if (!combinators.isEmpty()
                        && (combinators.size() != 1
                        || combinators.get(0) != Combinator.CHILD)) {
                    throw unsupportedSelector(component.span());
                }
                if (JavaFXSimpleSelector.from(component) == null) {
                    throw unsupportedSelector(component.span());
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
            JavaFXTarget compatibility
    ) {
        JavaFXCssImport.parse(cssImport, compatibility);
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

    /// Validates versioned JavaFX declaration compatibility.
    ///
    /// @param declaration the declaration to validate
    /// @param compatibility the selected JavaFX compatibility level
    private static void validateDeclaration(
            CssDeclaration declaration,
            JavaFXTarget compatibility
    ) {
        var property = declaration.name().value().toLowerCase(Locale.ROOT);
        if (!compatibility.supports(CSS_TRANSITIONS)
                && TRANSITION_PROPERTIES.contains(property)) {
            throw failure(
                    "JavaFX " + compatibility.version()
                            + " CSS does not support property " + property + ".",
                    declaration.name().span()
            );
        }
        if (TRANSITION_PROPERTIES.contains(property)) {
            var value = declarationValueText(declaration).strip();
            var importanceStart = trailingImportanceStart(value);
            if (importanceStart >= 0) {
                value = value.substring(0, importanceStart).stripTrailing();
            }
            JavaFXTransitionValidator.validate(
                    property,
                    value,
                    declaration.value().span(),
                    compatibility
            );
        }
        if (!compatibility.supports(EXTENDED_BLEND_MODES)
                && property.equals("-fx-blend-mode")) {
            var value = normalizedBlendMode(declarationValueText(declaration));
            if (NEW_BLEND_MODES.contains(value)) {
                throw failure(
                        "JavaFX " + compatibility.version()
                                + " CSS does not support -fx-blend-mode value "
                                + value
                                + ".",
                        declaration.value().span()
                );
            }
        }
    }

    /// Normalizes a blend-mode token for legacy JavaFX conflict detection.
    ///
    /// The old JavaFX parser resolves conflicting identifiers as
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
