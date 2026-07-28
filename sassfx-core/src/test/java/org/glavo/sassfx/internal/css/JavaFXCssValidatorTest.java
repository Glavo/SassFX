// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceLocation;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.ast.selector.SelectorList;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies JavaFX-version validation over evaluated CSS IR.
@NotNullByDefault
final class JavaFXCssValidatorTest {
    /// Rejects media rules through JavaFX 24 and accepts preferences in JavaFX 25.
    @Test
    void validatesMediaRulesByVersion() {
        var media = new CssMediaRule(
                CssMediaQuery.parseList("(prefers-color-scheme: dark)"),
                span("@media (prefers-color-scheme: dark)")
        );
        media.addChild(styleRuleWithDeclaration("Pane", "-fx-opacity", "1"));
        var stylesheet = stylesheet(media);

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX24
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX25
                )
        );
    }

    /// Rejects the JavaFX 25 multi-rule media bug and accepts its JavaFX 26 fix.
    @Test
    void validatesMultipleMediaRulesByVersion() {
        var media = new CssMediaRule(
                CssMediaQuery.parseList("(prefers-color-scheme: dark)"),
                span("@media (prefers-color-scheme: dark)")
        );
        media.addChild(styleRuleWithDeclaration("Pane", "-fx-opacity", "1"));
        media.addChild(styleRuleWithDeclaration("Label", "-fx-opacity", "1"));
        var stylesheet = stylesheet(media);

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX25
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX26
                )
        );
    }

    /// Rejects unsupported media-query syntax for JavaFX 27.
    @Test
    void rejectsUnsupportedJavaFX27MediaQuery() {
        var media = new CssMediaRule(
                CssMediaQuery.parseList("screen"),
                span("@media screen")
        );
        media.addChild(styleRuleWithDeclaration("Pane", "-fx-opacity", "1"));

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet(media),
                        JavaFXTarget.JAVAFX27
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
                        JavaFXTarget.JAVAFX17
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX27
                )
        );
    }

    /// Rejects import media conditions through JavaFX 26 and accepts them in JavaFX 27.
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
                        JavaFXTarget.JAVAFX26
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX27
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
                        JavaFXTarget.JAVAFX27
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

        for (var compatibility : JavaFXTarget.values()) {
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

        for (var compatibility : JavaFXTarget.values()) {
            assertThrows(
                    CssSerializeException.class,
                    () -> JavaFXCssValidator.validate(
                            stylesheet(outer),
                            compatibility
                    )
            );
        }
    }

    /// Rejects each transition property through JavaFX 22 and accepts it in JavaFX 23.
    @ParameterizedTest
    @ValueSource(strings = {
            "transition",
            "transition-delay",
            "transition-duration",
            "transition-property",
            "transition-timing-function"
    })
    void validatesTransitionPropertiesByVersion(String property) {
        var value = switch (property) {
            case "transition" -> "-fx-opacity 100ms ease";
            case "transition-delay" -> "0ms";
            case "transition-duration" -> "100ms";
            case "transition-property" -> "-fx-opacity";
            case "transition-timing-function" -> "ease";
            default -> throw new AssertionError("unexpected transition property");
        };
        var stylesheet = stylesheet(
                styleRuleWithDeclaration("Pane", property, value)
        );

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX22
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX23
                )
        );
    }

    /// Accepts every supported transition longhand value shape.
    @Test
    void acceptsTransitionLonghands() {
        assertTransitionAcceptedAll("transition-property", "all");
        assertTransitionAcceptedAll(
                "transition-property",
                "-fx-opacity, \"custom-name\", Foo"
        );
        assertTransitionAcceptedAll("transition-duration", "0ms");
        assertTransitionAcceptedAll(
                "transition-duration",
                "250ms, 1.5s, indefinite, -fx-duration, initial"
        );
        assertTransitionAcceptedAll(
                "transition-delay",
                "-250ms, 0s, 1s, -fx-delay, inherit"
        );
        assertTransitionAcceptedAll(
                "transition-timing-function",
                "linear, ease, ease-in, ease-out, ease-in-out, step-start,"
                        + " step-end, -fx-ease-in, -fx-ease-out, -fx-ease-both"
        );
    }

    /// Rejects malformed longhand layers rather than silently dropping tokens.
    @Test
    void rejectsMalformedTransitionLonghands() {
        assertTransitionRejected("transition-property", "10", JavaFXTarget.JAVAFX27);
        assertTransitionRejected(
                "transition-property",
                "foo,,bar",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected("transition-duration", "0", JavaFXTarget.JAVAFX27);
        assertTransitionRejected(
                "transition-duration",
                "-1ms",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-duration",
                "1px",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-delay",
                "\"1s\"",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "unknown",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "ease ease-in",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "ease,",
                JavaFXTarget.JAVAFX27
        );
    }

    /// Accepts shorthand components in every ordering interpreted by JavaFX.
    @Test
    void acceptsTransitionShorthand() {
        assertTransitionAcceptedAll("transition", "-fx-opacity 100ms linear");
        assertTransitionAcceptedAll(
                "transition",
                "foo 0.3s 0.4s cubic-bezier(0.1, 0.2, 0.3, 0.4)"
        );
        assertTransitionAcceptedAll(
                "transition",
                "0.3s foo cubic-bezier(0.1, 0.2, 0.3, 0.4) 0.4s"
        );
        assertTransitionAcceptedAll(
                "transition",
                "-fx-opacity 100ms, -fx-rotate 200ms ease-out,"
                        + " step-end -fx-scale-x 300ms -25ms"
        );
        assertTransitionAcceptedAll("transition", "100ms ease");
        assertTransitionAcceptedAll("transition", "\"linear()\" 100ms");
    }

    /// Rejects ambiguous, duplicated, empty, or surplus shorthand components.
    @Test
    void rejectsMalformedTransitionShorthand() {
        assertTransitionRejected("transition", "foo bar", JavaFXTarget.JAVAFX27);
        assertTransitionRejected("transition", "ease linear", JavaFXTarget.JAVAFX27);
        assertTransitionRejected("transition", "foo -1ms", JavaFXTarget.JAVAFX27);
        assertTransitionRejected(
                "transition",
                "foo 100ms ease ease-in",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition",
                "foo 100ms 10ms 20ms",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition",
                "foo 100ms ease 10ms ignored",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected("transition", "foo 100ms,", JavaFXTarget.JAVAFX27);
        assertTransitionRejected("transition", "", JavaFXTarget.JAVAFX27);
    }

    /// Applies the JavaFX 23–25 and 26+ cubic Bézier coordinate rules.
    @Test
    void validatesCubicBezierControlPointsByVersion() {
        for (var version = 23; version <= 27; version++) {
            var compatibility = JavaFXTarget.forVersion(version);
            assertTransitionAccepted(
                    "transition-timing-function",
                    "cubic-bezier(0.1, 0.2, 0.9, 0.8)",
                    compatibility
            );
            assertTransitionRejected(
                    "transition-timing-function",
                    "cubic-bezier(-0.1, 0, 0.9, 1)",
                    compatibility
            );
            assertTransitionRejected(
                    "transition-timing-function",
                    "cubic-bezier(0.1, 0, 1.1, 1)",
                    compatibility
            );
            if (version < 26) {
                assertTransitionRejected(
                        "transition-timing-function",
                        "cubic-bezier(0.1, -2, 0.9, 3)",
                        compatibility
                );
            } else {
                assertTransitionAccepted(
                        "transition-timing-function",
                        "cubic-bezier(0.1, -2, 0.9, 3)",
                        compatibility
                );
            }
        }

        assertTransitionRejected(
                "transition-timing-function",
                "cubic-bezier(0, 0, 1)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "cubic-bezier(0, 0px, 1, 1)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "CUBIC-BEZIER(0, 0, 1, 1)",
                JavaFXTarget.JAVAFX27
        );
    }

    /// Validates step positions and constraints required during conversion.
    @Test
    void validatesStepsTimingFunctions() {
        assertTransitionAcceptedAll("transition-timing-function", "steps(3)");
        for (var position : java.util.List.of(
                "jump-start",
                "jump-end",
                "jump-none",
                "jump-both",
                "start",
                "end"
        )) {
            assertTransitionAcceptedAll(
                    "transition-timing-function",
                    "steps(3, " + position + ")"
            );
        }

        assertTransitionRejected(
                "transition-timing-function",
                "steps(0)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "steps(1, jump-none)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "steps(2.5)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "steps(2, END)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "steps(2, end, ignored)",
                JavaFXTarget.JAVAFX27
        );
    }

    /// Applies the JavaFX 26 piecewise-linear easing grammar.
    @Test
    void validatesPiecewiseLinearTimingFunctions() {
        for (var value : java.util.List.of(
                "linear(0, 0.25, 1)",
                "linear(0, 0.25 75%, 1)",
                "linear(0, 0.25 25% 75%, 1)",
                "linear(0, 0.25 25%, 0.25 75%, 1)",
                "linear(0, .1 25%, .75 50%, 1)"
        )) {
            assertTransitionRejected(
                    "transition-timing-function",
                    value,
                    JavaFXTarget.JAVAFX25
            );
            assertTransitionAccepted(
                    "transition-timing-function",
                    value,
                    JavaFXTarget.JAVAFX26
            );
        }

        assertTransitionRejected(
                "transition-timing-function",
                "linear()",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "linear(0, 0.25 0.5, 1)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "LINEAR(0, 1)",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "linear (0, 1)",
                JavaFXTarget.JAVAFX27
        );
    }

    /// Preserves JavaFX's case-insensitive leading global keyword short circuit.
    @Test
    void validatesTransitionGlobalKeywords() {
        for (var property : java.util.List.of(
                "transition",
                "transition-delay",
                "transition-duration",
                "transition-property",
                "transition-timing-function"
        )) {
            for (var value : java.util.List.of("inherit", "INHERIT", "NoNe", "NULL")) {
                assertTransitionAcceptedAll(property, value);
            }
        }

        assertTransitionAcceptedAll("transition", "NoNe, ignored");
        assertTransitionAcceptedAll("transition-duration", "INHERIT, ignored");
        assertTransitionAcceptedAll("transition-duration", "100MS");
        assertTransitionRejected(
                "transition-timing-function",
                "EASE",
                JavaFXTarget.JAVAFX27
        );
        assertTransitionRejected(
                "transition-timing-function",
                "\"ease\"",
                JavaFXTarget.JAVAFX27
        );
    }

    /// Rejects `linear()` timing functions through JavaFX 25 and accepts them
    /// beginning with JavaFX 26.
    @ParameterizedTest
    @ValueSource(strings = {
            "transition",
            "transition-timing-function"
    })
    void validatesLinearTransitionEasingByVersion(String property) {
        var stylesheet = stylesheet(
                styleRuleWithDeclaration(
                        "Pane",
                        property,
                        property.equals("transition")
                                ? "opacity 100ms linear(0, 1)"
                                : "linear(0, 1)"
                )
        );

        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX25
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX26
                )
        );
    }

    /// Rejects conflicting blend modes through JavaFX 17 and accepts them in JavaFX 18.
    @ParameterizedTest
    @ValueSource(strings = {
            "add",
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
                        JavaFXTarget.JAVAFX17
                )
        );
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet,
                        JavaFXTarget.JAVAFX18
                )
        );
    }

    /// Accepts a blend mode supported by JavaFX 17.
    @Test
    void acceptsJavaFX17BlendMode() {
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet(
                                styleRuleWithDeclaration(
                                        "Pane",
                                        "-fx-blend-mode",
                                        "multiply"
                                )
                        ),
                        JavaFXTarget.JAVAFX17
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
    void acceptsJavaFXSelectors(String selector) {
        for (var compatibility : JavaFXTarget.values()) {
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
    void rejectsUnsupportedJavaFXSelectors(String selector) {
        for (var compatibility : JavaFXTarget.values()) {
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

    /// Requires a transition value to be accepted by every supporting release.
    ///
    /// @param property the transition property name
    /// @param value    the declaration value
    private static void assertTransitionAcceptedAll(String property, String value) {
        for (var version = 23; version <= 27; version++) {
            assertTransitionAccepted(
                    property,
                    value,
                    JavaFXTarget.forVersion(version)
            );
        }
    }

    /// Requires a transition value to be accepted by one release.
    ///
    /// @param property      the transition property name
    /// @param value         the declaration value
    /// @param compatibility the selected JavaFX release
    private static void assertTransitionAccepted(
            String property,
            String value,
            JavaFXTarget compatibility
    ) {
        assertDoesNotThrow(
                () -> JavaFXCssValidator.validate(
                        stylesheet(styleRuleWithDeclaration("Pane", property, value)),
                        compatibility
                ),
                property + ": " + value + " for JavaFX " + compatibility.version()
        );
    }

    /// Requires a transition value to be rejected by one release.
    ///
    /// @param property      the transition property name
    /// @param value         the declaration value
    /// @param compatibility the selected JavaFX release
    private static void assertTransitionRejected(
            String property,
            String value,
            JavaFXTarget compatibility
    ) {
        assertThrows(
                CssSerializeException.class,
                () -> JavaFXCssValidator.validate(
                        stylesheet(styleRuleWithDeclaration("Pane", property, value)),
                        compatibility
                ),
                property + ": " + value + " for JavaFX " + compatibility.version()
        );
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
