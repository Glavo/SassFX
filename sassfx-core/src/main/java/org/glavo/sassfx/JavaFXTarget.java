// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/// Selects the JavaFX release targeted by a stylesheet.
///
/// Textual JavaFX CSS targets use the selected release to reject syntax or
/// declarations that its CSS parser does not support. BSS targets additionally
/// use it to select the corresponding binary stylesheet format version.
///
/// Each constant documents changes represented by SassFX's CSS and BSS
/// capability model. A statement that a release introduces no relevant change
/// does not imply that every JavaFX CSS implementation detail is unchanged.
///
/// This type selects the JavaFX platform release for multiple output backends;
/// it is not an [OutputTarget] itself. [JavaFXCssTarget] and [BssTarget]
/// combine this release target with the configuration of a concrete backend.
///
/// JavaFX 23 through 27 can parse transition declarations from textual CSS,
/// but their BSS implementation cannot deserialize the associated duration,
/// interpolator, and transition-definition converters. [BssTarget] therefore
/// rejects transition declarations for those releases instead of producing a
/// binary stylesheet that JavaFX cannot load.
@NotNullByDefault
public enum JavaFXTarget {
    /// Targets JavaFX 8 and BSS version 5.
    ///
    /// This baseline supports `@font-face`, unconditional `@import`, legacy
    /// token-series gradients, and the internal
    /// `com.sun.javafx.css.converters` names used by BSS v5.
    JAVAFX8(8, 5),
    /// Targets JavaFX 9 and BSS version 6.
    ///
    /// BSS v6 moves converter names to the public `javafx.css.converter`
    /// package.
    JAVAFX9(9, 6),
    /// Targets JavaFX 10 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 9.
    JAVAFX10(10, 6),
    /// Targets JavaFX 11 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 10.
    JAVAFX11(11, 6),
    /// Targets JavaFX 12 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 11.
    JAVAFX12(12, 6),
    /// Targets JavaFX 13 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 12.
    JAVAFX13(13, 6),
    /// Targets JavaFX 14 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 13.
    JAVAFX14(14, 6),
    /// Targets JavaFX 15 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 14.
    JAVAFX15(15, 6),
    /// Targets JavaFX 16 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 15.
    JAVAFX16(16, 6),
    /// Targets JavaFX 17 and BSS version 6.
    ///
    /// This release introduces no new modeled capability. JavaFX 17 cannot set
    /// the `add`, `red`, `green`, or `blue` blend modes from CSS.
    JAVAFX17(17, 6),
    /// Targets JavaFX 18 and BSS version 6.
    ///
    /// This release fixes CSS parsing for the `add`, `red`, `green`, and
    /// `blue` blend modes.
    JAVAFX18(18, 6),
    /// Targets JavaFX 19 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 18.
    JAVAFX19(19, 6),
    /// Targets JavaFX 20 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 19.
    JAVAFX20(20, 6),
    /// Targets JavaFX 21 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 20.
    JAVAFX21(21, 6),
    /// Targets JavaFX 22 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 21.
    JAVAFX22(22, 6),
    /// Targets JavaFX 23 and BSS version 6.
    ///
    /// This release adds the `transition` shorthand and the
    /// `transition-property`, `transition-duration`, `transition-delay`, and
    /// `transition-timing-function` longhands to textual JavaFX CSS.
    JAVAFX23(23, 6),
    /// Targets JavaFX 24 and BSS version 6.
    ///
    /// This release introduces no SassFX-modeled CSS or BSS capability change
    /// from JavaFX 23.
    JAVAFX24(24, 6),
    /// Targets JavaFX 25 and BSS version 7.
    ///
    /// JavaFX 25 adds user-preference media queries, and BSS v7 serializes
    /// them. Its CSS parser does not accept multiple style rules within one
    /// `@media` block.
    JAVAFX25(25, 7),
    /// Targets JavaFX 26 and BSS version 8.
    ///
    /// JavaFX 26 adds `width`, `height`, and `aspect-ratio` media features,
    /// including min/max and range syntax, as well as `orientation` and
    /// `display-mode`; BSS v8 serializes these queries. This release also
    /// accepts multiple style rules within one `@media` block, adds the
    /// `linear()` transition timing function, and permits `cubic-bezier()` y
    /// control points outside `[0, 1]` while continuing to constrain x control
    /// points.
    JAVAFX26(26, 8),
    /// Targets JavaFX 27 and BSS version 9.
    ///
    /// JavaFX 27 adds media conditions to stylesheet `@import` rules. BSS v9
    /// preserves those conditions and the imported stylesheet structure. This
    /// release also adds the `-fx-supports-conditional-feature` and
    /// `-fx-platform` media features.
    JAVAFX27(27, 9);

    /// Contains the JavaFX major release number.
    private final int version;

    /// Contains the binary stylesheet format version.
    private final int bssVersion;

    /// Contains the immutable feature set supported by this target.
    private final @Unmodifiable Set<JavaFXFeature> features;

    /// Creates a JavaFX release target.
    ///
    /// @param version    the JavaFX major release number
    /// @param bssVersion the associated binary stylesheet format version
    JavaFXTarget(int version, int bssVersion) {
        this.version = version;
        this.bssVersion = bssVersion;
        var supported = EnumSet.noneOf(JavaFXFeature.class);
        Arrays.stream(JavaFXFeature.values())
                .filter(feature -> version >= feature.introducedVersion())
                .forEach(supported::add);
        this.features = Set.copyOf(supported);
    }

    /// Returns the JavaFX major release number.
    ///
    /// @return a value from `8` through `27`
    public int version() {
        return version;
    }

    /// Returns the binary stylesheet format version.
    ///
    /// @return a value from `5` through `9`
    public int bssVersion() {
        return bssVersion;
    }

    /// Returns whether the targeted JavaFX release supports a feature.
    ///
    /// The result describes support in JavaFX itself. A particular SassFX
    /// output backend may support only a subset of the platform features.
    ///
    /// @param feature the feature to test
    /// @return whether the feature is available in this target
    public boolean supports(JavaFXFeature feature) {
        return features.contains(Objects.requireNonNull(feature, "feature"));
    }

    /// Returns all recorded features supported by the targeted release.
    ///
    /// @return an immutable feature set
    public @Unmodifiable Set<JavaFXFeature> features() {
        return features;
    }

    /// Returns the target for a JavaFX major release.
    ///
    /// @param version the JavaFX major release number
    /// @return the corresponding target
    /// @throws IllegalArgumentException if the version is outside the supported
    /// range from `8` through `27`
    public static JavaFXTarget forVersion(int version) {
        if (version < JAVAFX8.version || version > JAVAFX27.version) {
            throw new IllegalArgumentException(
                    "Unsupported JavaFX target " + version + "; expected 8 through 27"
            );
        }
        for (var target : values()) {
            if (target.version == version) {
                return target;
            }
        }
        throw new AssertionError("Missing JavaFX target " + version);
    }
}
