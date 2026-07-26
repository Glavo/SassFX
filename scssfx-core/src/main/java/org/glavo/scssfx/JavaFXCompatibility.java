// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

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
@NotNullByDefault
public enum JavaFXCompatibility {
    /// Targets JavaFX 8 and BSS version 5.
    JAVAFX8(8, 5),
    /// Targets JavaFX 9 and BSS version 6.
    JAVAFX9(9, 6),
    /// Targets JavaFX 10 and BSS version 6.
    JAVAFX10(10, 6),
    /// Targets JavaFX 11 and BSS version 6.
    JAVAFX11(11, 6),
    /// Targets JavaFX 12 and BSS version 6.
    JAVAFX12(12, 6),
    /// Targets JavaFX 13 and BSS version 6.
    JAVAFX13(13, 6),
    /// Targets JavaFX 14 and BSS version 6.
    JAVAFX14(14, 6),
    /// Targets JavaFX 15 and BSS version 6.
    JAVAFX15(15, 6),
    /// Targets JavaFX 16 and BSS version 6.
    JAVAFX16(16, 6),
    /// Targets JavaFX 17 and BSS version 6.
    JAVAFX17(17, 6),
    /// Targets JavaFX 18 and BSS version 6.
    JAVAFX18(18, 6),
    /// Targets JavaFX 19 and BSS version 6.
    JAVAFX19(19, 6),
    /// Targets JavaFX 20 and BSS version 6.
    JAVAFX20(20, 6),
    /// Targets JavaFX 21 and BSS version 6.
    JAVAFX21(21, 6),
    /// Targets JavaFX 22 and BSS version 6.
    JAVAFX22(22, 6),
    /// Targets JavaFX 23 and BSS version 6.
    JAVAFX23(23, 6),
    /// Targets JavaFX 24 and BSS version 6.
    JAVAFX24(24, 6),
    /// Targets JavaFX 25 and BSS version 7.
    JAVAFX25(25, 7),
    /// Targets JavaFX 26 and BSS version 8.
    JAVAFX26(26, 8),
    /// Targets JavaFX 27 and BSS version 9.
    JAVAFX27(27, 9);

    /// Contains the JavaFX major release number.
    private final int version;

    /// Contains the binary stylesheet format version.
    private final int bssVersion;

    /// Contains the immutable feature set supported by this target.
    private final @Unmodifiable Set<JavaFXFeature> features;

    /// Creates a compatibility level.
    ///
    /// @param version    the JavaFX major release number
    /// @param bssVersion the associated binary stylesheet format version
    JavaFXCompatibility(int version, int bssVersion) {
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
    /// The result describes support in JavaFX itself. A particular SCSSFX
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
    public static JavaFXCompatibility forVersion(int version) {
        if (version < JAVAFX8.version || version > JAVAFX27.version) {
            throw new IllegalArgumentException(
                    "Unsupported JavaFX target " + version + "; expected 8 through 27"
            );
        }
        for (var compatibility : values()) {
            if (compatibility.version == version) {
                return compatibility;
            }
        }
        throw new AssertionError("Missing JavaFX target " + version);
    }
}
