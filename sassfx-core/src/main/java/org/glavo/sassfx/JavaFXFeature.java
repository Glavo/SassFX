// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies a versioned JavaFX CSS or BSS capability relevant to SassFX.
///
/// A feature records availability in the JavaFX platform. Output backends may
/// expose a smaller subset while their serializers are being implemented.
@NotNullByDefault
public enum JavaFXFeature {
    /// Supports `@font-face` rules in CSS and BSS.
    FONT_FACE(8),

    /// Supports unconditional stylesheet `@import` rules.
    UNCONDITIONAL_STYLESHEET_IMPORTS(8),

    /// Supports the deprecated token-series linear and radial gradient syntax.
    LEGACY_GRADIENT_SYNTAX(8),

    /// Correctly parses the `add`, `red`, `green`, and `blue` blend modes.
    EXTENDED_BLEND_MODES(18),

    /// Supports CSS transition properties.
    CSS_TRANSITIONS(23),

    /// Supports `prefers-color-scheme`, `prefers-reduced-motion`,
    /// `prefers-reduced-transparency`, `prefers-reduced-data`, and
    /// `-fx-prefers-persistent-scrollbars` media queries.
    USER_PREFERENCE_MEDIA_QUERIES(25),

    /// Correctly applies multiple style rules within one `@media` rule.
    MULTIPLE_RULES_PER_MEDIA_QUERY(26),

    /// Supports `width`, `height`, `aspect-ratio`, `orientation`, and
    /// `display-mode` media queries, including min/max and range syntax.
    VIEWPORT_MEDIA_QUERIES(26),

    /// Supports the `linear()` transition timing function and CSS-compliant
    /// out-of-range y control points in `cubic-bezier()`.
    ADVANCED_TRANSITION_EASING(26),

    /// Supports the `-fx-supports-conditional-feature` media feature.
    CONDITIONAL_MEDIA_FEATURE(27),

    /// Supports the `-fx-platform` media feature.
    PLATFORM_MEDIA_FEATURE(27),

    /// Supports media conditions on stylesheet `@import` rules.
    CONDITIONAL_STYLESHEET_IMPORTS(27);

    /// Contains the first JavaFX release providing the feature.
    private final int introducedVersion;

    /// Creates a feature with its first supported release.
    ///
    /// @param introducedVersion the first supporting JavaFX major release
    JavaFXFeature(int introducedVersion) {
        this.introducedVersion = introducedVersion;
    }

    /// Returns the first JavaFX release that supports the feature.
    ///
    /// @return the JavaFX major release number
    public int introducedVersion() {
        return introducedVersion;
    }
}
