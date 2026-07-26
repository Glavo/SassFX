// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies JavaFX-version validation over evaluated CSS IR.
@NotNullByDefault
final class JavaFXCssValidatorTest {
    /// Rejects every media rule for JavaFX 17 and accepts a supported JavaFX 27 query.
    @Test
    void validatesMediaRulesByVersion() {
        var media = new CssMediaRule(
                CssMediaQuery.parseList("(min-width: 100px)"),
                span("@media (min-width: 100px)")
        );
        media.addChild(styleRuleWithDeclaration("Pane", "-fx-opacity", "1"));
        var stylesheet = stylesheet(media);

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_17
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_27
                )
        );
    }

    /// Rejects unsupported media-query syntax for JavaFX 27.
    @Test
    void rejectsUnsupportedJavaFx27MediaQuery() {
        var media = new CssMediaRule(
                CssMediaQuery.parseList("screen"),
                span("@media screen")
        );
        media.addChild(styleRuleWithDeclaration("Pane", "-fx-opacity", "1"));

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet(media),
                        JavaFXCompatibility.JAVA_FX_27
                )
        );
    }

    /// Accepts unconditional imports at both compatibility levels.
    @ParameterizedTest
    @ValueSource(strings = {
            "\"theme.css\"",
            "/* before */ \"theme.css\" /* after */",
            "'theme\\' name.css'",
            "URL(\"theme ) ( name.css\")",
            "url(data:image/svg+xml;utf8,<svg\\)>)"
    })
    void acceptsUnconditionalImports(String argument) {
        var stylesheet = stylesheet(new CssImport(argument, span(argument)));

        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_17
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_27
                )
        );
    }

    /// Rejects import media conditions for JavaFX 17 and accepts them for JavaFX 27.
    @ParameterizedTest
    @ValueSource(strings = {
            "\"theme.css\" (prefers-color-scheme: dark)",
            "'theme\\' name.css' (min-width: 100px)",
            "URL(\"theme ) ( name.css\") (orientation: landscape)"
    })
    void validatesConditionalImportsByVersion(String argument) {
        var stylesheet = stylesheet(new CssImport(argument, span(argument)));

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_17
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_27
                )
        );
    }

    /// Rejects an import that does not begin with a complete string or URL token.
    @ParameterizedTest
    @ValueSource(strings = {
            "theme.css",
            "\"theme.css",
            "url(theme.css"
    })
    void rejectsMalformedImportUrl(String argument) {
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet(new CssImport(argument, span(argument))),
                        JavaFXCompatibility.JAVA_FX_27
                )
        );
    }

    /// Rejects supports and opaque at-rules at both compatibility levels.
    @ParameterizedTest
    @ValueSource(strings = {"supports", "unknown"})
    void rejectsUnsupportedAtRules(String kind) {
        CssNode node;
        if (kind.equals("supports")) {
            var supports = new CssSupportsRule("(display: grid)", span("@supports"));
            supports.addChild(styleRuleWithDeclaration("Pane", "-fx-opacity", "1"));
            node = supports;
        } else {
            node = new CssUnknownAtRule("container", "main", true, span("@container"));
        }

        for (var compatibility : JavaFXCompatibility.values()) {
            assertThrows(
                    CssSerializeException.class,
                    () -> JavaFXCssValidator.validate(
                            stylesheet(node),
                            compatibility
                    )
            );
        }
    }

    /// Rejects a style rule retained beneath another style rule.
    @Test
    void rejectsNativeNestedStyleRules() {
        var outer = styleRule(".outer");
        outer.addChild(styleRuleWithDeclaration(".inner", "-fx-opacity", "1"));

        for (var compatibility : JavaFXCompatibility.values()) {
            assertThrows(
                    CssSerializeException.class,
                    () -> JavaFXCssValidator.validate(
                            stylesheet(outer),
                            compatibility
                    )
            );
        }
    }

    /// Rejects each JavaFX transition property for JavaFX 17 but accepts it for JavaFX 27.
    @ParameterizedTest
    @ValueSource(strings = {
            "transition",
            "transition-delay",
            "transition-duration",
            "transition-property",
            "transition-timing-function"
    })
    void validatesTransitionPropertiesByVersion(String property) {
        var stylesheet = stylesheet(
                styleRuleWithDeclaration("Pane", property, "initial")
        );

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_17
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_27
                )
        );
    }

    /// Rejects blend modes added after JavaFX 17 but accepts them for JavaFX 27.
    @ParameterizedTest
    @ValueSource(strings = {
            "red",
            "RED",
            "\"red\"",
            "green !important",
            "'blue' !IMPORTANT"
    })
    void validatesNewBlendModesByVersion(String value) {
        var stylesheet = stylesheet(
                styleRuleWithDeclaration("Pane", "-fx-blend-mode", value)
        );

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_17
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXCompatibility.JAVA_FX_27
                )
        );
    }

    /// Accepts a blend mode supported by JavaFX 17.
    @Test
    void acceptsJavaFx17BlendMode() {
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet(
                                styleRuleWithDeclaration(
                                        "Pane",
                                        "-fx-blend-mode",
                                        "multiply"
                                )
                        ),
                        JavaFXCompatibility.JAVA_FX_17
                )
        );
    }

    /// Accepts selector forms interpreted by JavaFX.
    ///
    /// @param selector the accepted selector
    @ParameterizedTest
    @ValueSource(strings = {
            "Pane",
            "*",
            ".button#main:hover",
            "VBox > .button .label"
    })
    void acceptsJavaFxSelectors(String selector) {
        for (var compatibility : JavaFXCompatibility.values()) {
            assertDoesNotThrow(
                    () -> JavaFXCssValidator.validate(
                            stylesheet(
                                    styleRuleWithDeclaration(
                                            selector,
                                            "-fx-opacity",
                                            "1"
                                    )
                            ),
                            compatibility
                    )
            );
        }
    }

    /// Rejects selectors that JavaFX cannot interpret with CSS semantics.
    ///
    /// @param selector the unsupported selector
    @ParameterizedTest
    @ValueSource(strings = {
            "[disabled]",
            "Pane + Label",
            "Pane ~ Label",
            "Pane::before",
            "Pane:is(.active)",
            "svg|Pane"
    })
    void rejectsUnsupportedJavaFxSelectors(String selector) {
        for (var compatibility : JavaFXCompatibility.values()) {
            assertThrows(
                    CssSerializeException.class,
                    () -> JavaFXCssValidator.validate(
                            stylesheet(
                                    styleRuleWithDeclaration(
                                            selector,
                                            "-fx-opacity",
                                            "1"
                                    )
                            ),
                            compatibility
                    )
            );
        }
    }

    /// Creates a stylesheet containing the supplied nodes in order.
    ///
    /// @param nodes the nodes to append
    /// @return the populated stylesheet
    private static CssStylesheet stylesheet(CssNode... nodes) {
        var stylesheet = new CssStylesheet(span(""));
        for (var node : nodes) {
            stylesheet.addChild(node);
        }
        return stylesheet;
    }

    /// Creates a style rule for one selector.
    ///
    /// @param selectorText the selector source
    /// @return the empty style rule
    private static CssStyleRule styleRule(String selectorText) {
        var selectorSpan = span(selectorText);
        var selector = new CssValue<>(
                SelectorList.parse(selectorText, selectorSpan),
                selectorSpan
        );
        return new CssStyleRule(selector, selectorSpan, true);
    }

    /// Creates a visible style rule containing one raw declaration.
    ///
    /// @param selectorText the selector source
    /// @param name         the property name
    /// @param value        the property value
    /// @return the populated style rule
    private static CssStyleRule styleRuleWithDeclaration(
            String selectorText,
            String name,
            String value
    ) {
        var rule = styleRule(selectorText);
        rule.addChild(declaration(name, value));
        return rule;
    }

    /// Creates a raw CSS declaration.
    ///
    /// @param name the property name
    /// @param value the property value
    /// @return the declaration
    private static CssDeclaration declaration(String name, String value) {
        var declarationSpan = span(name + ": " + value);
        var nameValue = new CssValue<>(name, declarationSpan);
        CssValue<SassValue> propertyValue = new CssValue<>(
                new SassString(value, false),
                declarationSpan
        );
        return new CssDeclaration(
                nameValue,
                propertyValue,
                declarationSpan,
                false
        );
    }

    /// Creates a synthetic source span for text.
    ///
    /// @param text the covered text
    /// @return the source span
    private static SourceSpan span(String text) {
        return new SourceSpan(
                null,
                new SourceLocation(0, 0, 0),
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
