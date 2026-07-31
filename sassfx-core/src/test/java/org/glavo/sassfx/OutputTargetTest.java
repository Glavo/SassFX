// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

import static org.glavo.sassfx.JavaFXFeature.ADVANCED_TRANSITION_EASING;
import static org.glavo.sassfx.JavaFXFeature.CONDITIONAL_STYLESHEET_IMPORTS;
import static org.glavo.sassfx.JavaFXFeature.CSS_TRANSITIONS;
import static org.glavo.sassfx.JavaFXFeature.EXTENDED_BLEND_MODES;
import static org.glavo.sassfx.JavaFXFeature.MULTIPLE_RULES_PER_MEDIA_QUERY;
import static org.glavo.sassfx.JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES;
import static org.glavo.sassfx.JavaFXFeature.VIEWPORT_MEDIA_QUERIES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies output target configuration contracts.
@NotNullByDefault
final class OutputTargetTest {
    /// Parses every canonical target-selector form.
    @Test
    void parsesCanonicalSelectors() {
        assertSame(CssTarget.DEFAULT, OutputTarget.parse("css"));

        for (var version = 8; version <= 27; version++) {
            var javaFXCss = assertInstanceOf(
                    JavaFXCssTarget.class,
                    OutputTarget.parse("css/javafx@" + version)
            );
            assertEquals(version, javaFXCss.javaFXTarget().version());
            assertEquals(OutputStyle.EXPANDED, javaFXCss.style());

            var bss = assertInstanceOf(
                    BssTarget.class,
                    OutputTarget.parse("bss/javafx@" + version)
            );
            assertEquals(version, bss.javaFXTarget().version());
        }
    }

    /// Rejects aliases, malformed versions, and unsupported releases.
    @Test
    void rejectsNoncanonicalSelectors() {
        var selectors = List.of(
                "",
                "CSS",
                "css/javafx",
                "css/javafx@7",
                "css/javafx@28",
                "css/javafx@08",
                "css/javafx@17.0",
                "css/javafx@١٧",
                "bss",
                "BSS/javafx@17"
        );
        for (var selector : selectors) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> OutputTarget.parse(selector),
                    selector
            );
        }
    }

    /// Verifies the standard CSS target defaults.
    @Test
    void providesCssDefaults() {
        OutputTarget<String> target = CssTarget.DEFAULT;
        var css = assertInstanceOf(CssTarget.class, target);

        assertEquals(OutputStyle.EXPANDED, css.style());
        assertEquals(true, css.charset());
    }

    /// Verifies the JavaFX target defaults.
    @Test
    void providesJavaFXDefaults() {
        var target = JavaFXCssTarget.DEFAULT;

        assertEquals(JavaFXTarget.JAVAFX17, target.javaFXTarget());
        assertEquals(OutputStyle.EXPANDED, target.style());
    }

    /// Verifies that compatibility levels select their exact BSS versions.
    @Test
    void mapsCompatibilityToBssVersion() {
        OutputTarget<ByteBuffer> target = BssTarget.DEFAULT;
        var bss = assertInstanceOf(BssTarget.class, target);

        assertEquals(6, bss.bssVersion());
        assertEquals(9, new BssTarget(JavaFXTarget.JAVAFX27).bssVersion());
    }

    /// Verifies every supported JavaFX release and BSS format mapping.
    @Test
    void exposesContinuousJavaFXTargets() {
        var targets = JavaFXTarget.values();
        assertEquals(20, targets.length);

        for (var version = 8; version <= 27; version++) {
            var target = JavaFXTarget.forVersion(version);
            assertSame(targets[version - 8], target);
            assertEquals(version, target.version());
            assertEquals(
                    version == 8 ? 5
                            : version <= 24 ? 6
                            : version == 25 ? 7
                            : version == 26 ? 8 : 9,
                    target.bssVersion()
            );
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> JavaFXTarget.forVersion(7)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> JavaFXTarget.forVersion(28)
        );
    }

    /// Verifies the recorded JavaFX CSS feature milestones.
    @Test
    void exposesFeatureMatrix() {
        assertEquals(
                Set.of(
                        JavaFXFeature.FONT_FACE,
                        JavaFXFeature.UNCONDITIONAL_STYLESHEET_IMPORTS,
                        JavaFXFeature.LEGACY_GRADIENT_SYNTAX,
                        JavaFXFeature.FUNCTIONAL_PSEUDO_CLASSES
                ),
                JavaFXTarget.JAVAFX8.features()
        );
        assertFalse(JavaFXTarget.JAVAFX17.supports(EXTENDED_BLEND_MODES));
        assertTrue(JavaFXTarget.JAVAFX18.supports(EXTENDED_BLEND_MODES));
        assertFalse(JavaFXTarget.JAVAFX22.supports(CSS_TRANSITIONS));
        assertTrue(JavaFXTarget.JAVAFX23.supports(CSS_TRANSITIONS));
        assertFalse(JavaFXTarget.JAVAFX25.supports(ADVANCED_TRANSITION_EASING));
        assertTrue(JavaFXTarget.JAVAFX26.supports(ADVANCED_TRANSITION_EASING));
        assertTrue(JavaFXTarget.JAVAFX25.supports(USER_PREFERENCE_MEDIA_QUERIES));
        assertFalse(JavaFXTarget.JAVAFX25.supports(MULTIPLE_RULES_PER_MEDIA_QUERY));
        assertTrue(JavaFXTarget.JAVAFX26.supports(MULTIPLE_RULES_PER_MEDIA_QUERY));
        assertTrue(JavaFXTarget.JAVAFX26.supports(VIEWPORT_MEDIA_QUERIES));
        assertFalse(JavaFXTarget.JAVAFX26.supports(CONDITIONAL_STYLESHEET_IMPORTS));
        assertTrue(JavaFXTarget.JAVAFX27.supports(CONDITIONAL_STYLESHEET_IMPORTS));
        assertThrows(
                UnsupportedOperationException.class,
                () -> JavaFXTarget.JAVAFX27.features().clear()
        );
    }

    /// Verifies that required target components reject null.
    @Test
    @SuppressWarnings("DataFlowIssue")
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> OutputTarget.parse(null));
        assertThrows(NullPointerException.class, () -> new CssTarget(null, true));
        assertThrows(
                NullPointerException.class,
                () -> new JavaFXCssTarget(null, OutputStyle.EXPANDED)
        );
        assertThrows(
                NullPointerException.class,
                () -> new JavaFXCssTarget(JavaFXTarget.JAVAFX17, null)
        );
        assertThrows(NullPointerException.class, () -> new BssTarget(null));
    }
}
