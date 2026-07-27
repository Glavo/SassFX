// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.glavo.scssfx.internal.value.color.ColorSpace;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies a public Sass color space.
///
/// Each value corresponds to a color space accepted by Sass's Color Level 4
/// value model. Channel units and ranges follow Sass semantics; color channels
/// may be outside the nominal gamut, while alpha must be between zero and one.
@NotNullByDefault
public enum SassColorSpace {
    /// The legacy RGB space whose channels use the range 0 through 255.
    RGB("rgb", ColorSpace.RGB),

    /// The legacy HSL space.
    HSL("hsl", ColorSpace.HSL),

    /// The legacy HWB space.
    HWB("hwb", ColorSpace.HWB),

    /// The nonlinear sRGB space whose channels use the range 0 through 1.
    SRGB("srgb", ColorSpace.SRGB),

    /// The linear-light sRGB space.
    SRGB_LINEAR("srgb-linear", ColorSpace.SRGB_LINEAR),

    /// The Display P3 space.
    DISPLAY_P3("display-p3", ColorSpace.DISPLAY_P3),

    /// The linear-light Display P3 space.
    DISPLAY_P3_LINEAR("display-p3-linear", ColorSpace.DISPLAY_P3_LINEAR),

    /// The A98 RGB space.
    A98_RGB("a98-rgb", ColorSpace.A98_RGB),

    /// The ProPhoto RGB space.
    PROPHOTO_RGB("prophoto-rgb", ColorSpace.PROPHOTO_RGB),

    /// The Rec. 2020 RGB space.
    REC2020("rec2020", ColorSpace.REC2020),

    /// The CIE XYZ space using the D65 white point.
    XYZ_D65("xyz", ColorSpace.XYZ_D65),

    /// The CIE XYZ space using the D50 white point.
    XYZ_D50("xyz-d50", ColorSpace.XYZ_D50),

    /// The CIE Lab space.
    LAB("lab", ColorSpace.LAB),

    /// The CIE LCH space.
    LCH("lch", ColorSpace.LCH),

    /// The OKLab space.
    OKLAB("oklab", ColorSpace.OKLAB),

    /// The OKLCH space.
    OKLCH("oklch", ColorSpace.OKLCH);

    /// Contains the CSS spelling of this space.
    private final String cssName;

    /// Contains the evaluator representation of this space.
    private final ColorSpace internal;

    /// Creates a public color-space value.
    ///
    /// @param cssName the canonical CSS spelling
    /// @param internal the evaluator representation
    SassColorSpace(String cssName, ColorSpace internal) {
        this.cssName = cssName;
        this.internal = internal;
    }

    /// Returns the canonical CSS spelling of this color space.
    ///
    /// `XYZ_D65` uses the CSS alias `xyz`.
    ///
    /// @return the lowercase CSS name
    public String cssName() {
        return cssName;
    }

    /// Returns whether this is one of Sass's legacy RGB, HSL, or HWB spaces.
    ///
    /// @return whether legacy color accessors apply to this space
    public boolean isLegacy() {
        return internal.isLegacy();
    }

    /// Returns the evaluator representation used by the internal value bridge.
    ///
    /// @return the internal color space
    @org.jetbrains.annotations.ApiStatus.Internal
    public Object bridgeToInternal() {
        return internal;
    }

    /// Converts an evaluator color space to its public representation.
    ///
    /// @param space the evaluator color space
    /// @return the corresponding public space
    @org.jetbrains.annotations.ApiStatus.Internal
    public static SassColorSpace bridgeFromInternal(Object space) {
        if (!(space instanceof ColorSpace internalSpace)
                || internalSpace == ColorSpace.LMS) {
            throw new IllegalArgumentException(
                    "space must be a public Sass color space"
            );
        }
        return switch (internalSpace) {
            case RGB -> RGB;
            case HSL -> HSL;
            case HWB -> HWB;
            case SRGB -> SRGB;
            case SRGB_LINEAR -> SRGB_LINEAR;
            case DISPLAY_P3 -> DISPLAY_P3;
            case DISPLAY_P3_LINEAR -> DISPLAY_P3_LINEAR;
            case A98_RGB -> A98_RGB;
            case PROPHOTO_RGB -> PROPHOTO_RGB;
            case REC2020 -> REC2020;
            case XYZ_D65 -> XYZ_D65;
            case XYZ_D50 -> XYZ_D50;
            case LAB -> LAB;
            case LCH -> LCH;
            case OKLAB -> OKLAB;
            case OKLCH -> OKLCH;
            case LMS -> throw new AssertionError("internal LMS space escaped");
        };
    }
}
