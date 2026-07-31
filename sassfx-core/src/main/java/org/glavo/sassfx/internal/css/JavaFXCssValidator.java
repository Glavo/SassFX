// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.ast.selector.Combinator;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.glavo.sassfx.JavaFXFeature.CSS_TRANSITIONS;
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

    /// Validates versioned JavaFX declaration compatibility.
    ///
    /// @param declaration the declaration to validate
    /// @param compatibility the selected JavaFX compatibility level
    private static void validateDeclaration(
            CssDeclaration declaration,
            JavaFXTarget compatibility
    ) {
        var sourceProperty = declaration.name().value();
        if (!JavaFXCssLexer.isIdentifier(sourceProperty)) {
            throw failure(
                    "JavaFX CSS does not support this declaration name.",
                    declaration.name().span()
            );
        }
        var declarationValue = declarationValueText(declaration);
        if (!JavaFXCssLexer.isTokenizableValue(declarationValue)) {
            throw failure(
                    "JavaFX CSS cannot tokenize this declaration value.",
                    declaration.value().span()
            );
        }
        var property = sourceProperty.toLowerCase(Locale.ROOT);
        if (declaration.parent() instanceof CssFontFace) {
            if (property.equals("src")) {
                JavaFXFontFaceParser.parseSources(
                        declarationValue,
                        declaration.value().span()
                );
            } else {
                JavaFXFontFaceParser.storedDescriptorValue(
                        declarationValue,
                        declaration.value().span()
                );
            }
            return;
        }
        var propertyValue = withoutTrailingImportance(declarationValue);
        @Nullable var scalarValue = JavaFXScalarParser.parse(
                property,
                propertyValue,
                declaration.value().span(),
                compatibility
        );
        var globalKeyword = scalarValue
                instanceof JavaFXScalarParser.GlobalKeyword;
        if (validateFontProperty(
                property,
                propertyValue,
                declaration.value().span(),
                globalKeyword
        )) {
            return;
        }
        if (!compatibility.supports(CSS_TRANSITIONS)
                && TRANSITION_PROPERTIES.contains(property)) {
            throw failure(
                    "JavaFX " + compatibility.version()
                            + " CSS does not support property " + property + ".",
                    declaration.name().span()
            );
        }
        if (TRANSITION_PROPERTIES.contains(property)) {
            if (!globalKeyword) {
                JavaFXTransitionValidator.validate(
                        property,
                        propertyValue,
                        declaration.value().span(),
                        compatibility
                );
            }
            return;
        }
        if (globalKeyword) {
            return;
        }
        if (JavaFXFourSidedValueParser.parse(
                property,
                propertyValue,
                declaration.value().span()
        ) != null) {
            return;
        }
        if (scalarValue instanceof JavaFXScalarParser.LegacyString legacy
                && property.equals("-fx-blend-mode")) {
            var value = legacy.text().toLowerCase(Locale.ROOT);
            if (JavaFXScalarParser.isExtendedBlendMode(value)) {
                throw failure(
                        "JavaFX " + compatibility.version()
                                + " CSS does not support -fx-blend-mode value "
                                + value
                                + ".",
                        declaration.value().span()
                );
            }
        }
        if (scalarValue != null) {
            return;
        }
        validateValueFunction(declaration, property, declarationValue);
    }

    /// Validates one property dispatched through OpenJFX's font parsers.
    ///
    /// A complete global keyword bypasses the property-specific parser. The
    /// suffix checks intentionally match OpenJFX's declaration dispatch.
    ///
    /// @param property the normalized declaration name
    /// @param value    the declaration value without `!important`
    /// @param span     the source range associated with the value
    /// @param globalKeyword whether the shared scalar parser found a complete
    /// global keyword
    /// @return whether the property belongs to the JavaFX font family
    private static boolean validateFontProperty(
            String property,
            String value,
            SourceSpan span,
            boolean globalKeyword
    ) {
        var family = property.endsWith("font-family");
        var size = property.endsWith("font-size");
        var style = property.endsWith("font-style");
        var weight = property.endsWith("font-weight");
        var shorthand = property.endsWith("font");
        if (!family && !size && !style && !weight && !shorthand) {
            return false;
        }
        if (globalKeyword) {
            return true;
        }
        if (family) {
            JavaFXFontParser.parseFamily(value, span);
        } else if (size) {
            JavaFXFontParser.parseSize(value, span);
        } else if (style) {
            JavaFXFontParser.parseStyle(value, span);
        } else if (weight) {
            JavaFXFontParser.parseWeight(value, span);
        } else {
            JavaFXFontParser.parseShorthand(value, span);
        }
        return true;
    }

    /// Validates a leading JavaFX value-function token.
    ///
    /// OpenJFX accepts its built-in function-name prefixes and exact
    /// `url(...)` tokens. Other leading function tokens fail declaration
    /// parsing instead of becoming property lookups.
    ///
    /// @param declaration the declaration whose emitted value is inspected
    /// @param property    the normalized declaration name
    /// @param declarationValue the emitted declaration value
    private static void validateValueFunction(
            CssDeclaration declaration,
            String property,
            String declarationValue
    ) {
        var value = declarationValue.strip();
        var importanceStart = trailingImportanceStart(value);
        if (importanceStart >= 0) {
            value = value.substring(0, importanceStart).stripTrailing();
        }
        @Nullable var functionName = JavaFXValueFunction.invocationName(value);
        if (functionName == null
                || functionName.equals("url")
                || property.equals("-fx-border-style")
                && functionName.regionMatches(
                        true,
                        0,
                        "segments",
                        0,
                        "segments".length()
                )
                || JavaFXValueFunction.fromName(functionName) != null) {
            return;
        }
        throw failure(
                "JavaFX CSS does not support value function "
                        + functionName + "().",
                declaration.value().span()
        );
    }

    /// Removes one trailing JavaFX importance token from a declaration value.
    ///
    /// @param value the complete emitted declaration value
    /// @return the value prefix inspected by property-specific parsers
    private static String withoutTrailingImportance(String value) {
        var importanceStart = trailingImportanceStart(value);
        return importanceStart < 0
                ? value
                : value.substring(0, importanceStart);
    }

    /// Finds a trailing CSS `!important` suffix.
    ///
    /// @param value the complete declaration value
    /// @return the suffix start, or `-1` when no suffix is present
    private static int trailingImportanceStart(String value) {
        var candidate = value.lastIndexOf('!');
        while (candidate >= 0) {
            if (JavaFXCssLexer.isTokenizableValue(
                    value.substring(0, candidate)
            )) {
                var importanceEnd = JavaFXCssLexer.importanceEnd(
                        value,
                        candidate
                );
                if (importanceEnd >= 0
                        && JavaFXCssLexer.triviaEnd(
                                value,
                                importanceEnd
                        ) == value.length()) {
                    return candidate;
                }
            }
            candidate = value.lastIndexOf('!', candidate - 1);
        }
        return -1;
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
