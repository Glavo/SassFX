// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.oracle;

import javafx.css.CssParser;
import javafx.css.Stylesheet;
import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/// Verifies SassFX JavaFX CSS compatibility against a pinned OpenJFX parser.
///
/// This class belongs to an isolated development source set. It is absent from
/// product runtime and publication classpaths.
@NotNullByDefault
public final class JavaFxCssOracle {
    /// Prevents instantiation.
    private JavaFxCssOracle() {
    }

    /// Runs the fixture matrix for one JavaFX release target.
    ///
    /// @param arguments one argument selecting a configured oracle version
    /// @throws Exception if compilation, oracle parsing, or validation fails
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one JavaFX version argument.");
        }

        var target = switch (arguments[0]) {
            case "17" -> JavaFXTarget.JAVAFX17;
            case "18" -> JavaFXTarget.JAVAFX18;
            case "23" -> JavaFXTarget.JAVAFX23;
            case "25" -> JavaFXTarget.JAVAFX25;
            case "26" -> JavaFXTarget.JAVAFX26;
            case "27" -> JavaFXTarget.JAVAFX27;
            default -> throw new IllegalArgumentException(
                    "Unsupported JavaFX version: " + arguments[0]
            );
        };

        verifyVersionedParserSemantics(target);
        for (var fixture : acceptedFixtures(target)) {
            verifyAccepted(fixture, target);
        }
        for (var fixture : rejectedFixtures(target)) {
            verifyRejected(fixture, target);
        }
        verifyScalarUrlBss(target);
        if (target.supports(org.glavo.sassfx.JavaFXFeature.CSS_TRANSITIONS)) {
            verifyTransitionBssLimitation(target);
        }
        if (target == JavaFXTarget.JAVAFX27) {
            verifyImportedBss(target);
        }
    }

    /// Returns fixtures that must compile and parse without JavaFX CSS errors.
    ///
    /// @param target the selected JavaFX release
    /// @return the immutable accepted fixture list
    private static @Unmodifiable List<Fixture> acceptedFixtures(JavaFXTarget target) {
        var fixtures = new ArrayList<Fixture>();
        fixtures.add(new Fixture(
                "basic",
                "Pane { -fx-opacity: 0.5; -fx-text-fill: #ff0000; }",
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "blend-mode",
                "Pane { -fx-blend-mode: multiply; }",
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "effects",
                """
                        Pane {
                          -fx-effect: dropshadow(gaussian, rgba(18, 52, 86, 0.3), 12px, 25%, -2px, 3em);
                        }
                        Text {
                          -fx-effect: innershadow(two-pass-box, -fx-shadow-color, -fx-radius, 0.4, 2px, -3px);
                        }
                        Region {
                          -fx-effect: dropshadow(one-pass-box, derive(#123456, 10%), 1em, 0, 0, 1px);
                        }
                        Label {
                          -fx-effect: innershadow(three-pass-box, hsba(240, 100%, 50%, 0.5), 8px, 0.2, 1px, 2px);
                        }
                """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "duration-scalars",
                """
                        Tooltip {
                          -fx-show-delay: 250ms;
                          -fx-show-duration: 1.5s;
                          -fx-hide-delay: indefinite;
                        }
                        Spinner {
                          -fx-initial-delay: 300ms;
                          -fx-repeat-delay: 60ms;
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "quoted-strings",
                """
                        Label {
                          -fx-ellipsis-string: "More";
                          -fx-prompt-text: 'Type here';
                          -fx-shape: "M 0 0 L 1 1 Z";
                          -fx-custom-string: "";
                          -fx-custom-true: "TrUe";
                          -fx-custom-false: 'FALSE';
                          -fx-custom-duration: "INDEFINITE";
                          -fx-custom-infinity: "Infinity";
                          -fx-custom-color: "red";
                          -fx-custom-hex-color: "#123456";
                          -fx-font-family: "Oracle Font";
                          -fx-keyword-font-family: indefinite;
                          -fx-quoted-keyword-font-family: "true";
                          -fx-empty-font-family: "";
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "generic-size-sequence",
                """
                        Pane {
                          -fx-custom-single-size: 1px;
                          -fx-custom-sizes:
                              1 2% 3em 4ex 5px 6cm 7mm 8in 9pt 10pc
                              11deg 12grad 13rad 14turn;
                          -fx-custom-mixed-sizes: -1.5em 0 2turn;
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "font-size-keywords",
                """
                        Pane {
                          -fx-a-font-size: inherit;
                          -fx-b-font-size: xx-small;
                          -fx-c-font-size: x-small;
                          -fx-d-font-size: small;
                          -fx-e-font-size: medium;
                          -fx-f-font-size: large;
                          -fx-g-font-size: x-large;
                          -fx-h-font-size: xx-large;
                          -fx-i-font-size: smaller;
                          -fx-j-font-size: larger;
                          -fx-font: italic large/medium "Example Sans";
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "region-geometry",
                """
                        Pane {
                          -fx-background-color: #123456, rgba(18, 52, 86, 0.3);
                          -fx-background-insets: 1px 2px, 3px;
                          -fx-background-radius: 4px 5px / 6px 7px, 8%;
                          -fx-opaque-insets: 9px 10px 11px 12px;
                          -fx-border-color: #123456 red green blue, #abcdef;
                          -fx-border-insets: 13px 14px, 15px;
                          -fx-border-radius: 16px 17px / 18px 19px, 20%;
                          -fx-border-width: 1px 2px 3px 4px, 5%;
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "region-images",
                """
                        Pane {
                          -fx-background-image: url("image.png"), url(second.png);
                          -fx-background-position: left 10px top 20%, right 5px bottom 6px, center bottom;
                          -fx-background-repeat: repeat-x, space no-repeat, round;
                          -fx-background-size: 25px auto, auto 30%, cover;
                          -fx-border-image-source: url("border.png");
                          -fx-border-image-insets: 1px 2px 3px 4px, -fx-image-inset;
                          -fx-border-image-repeat: repeat-x, space round, stretch;
                          -fx-border-image-slice: 10% fill, -fx-image-slice fill, 20% 30% 40% 50%;
                          -fx-border-image-width: 1px 2% 3 4px, auto -fx-image-width 6px 7%, -fx-other-width;
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "region-paints-and-styles",
                """
                        Pane {
                          -fx-base: #123456;
                          -fx-background-color:
                              linear-gradient(to right bottom, reflect, red, #123456 25%, rgba(18, 52, 86, 0.3) 75%, blue),
                              radial-gradient(focus-angle 45deg, focus-distance 20%, center 30% 40%, radius 50%, repeat, red 0%, green 50%, blue 100%),
                              image-pattern("image.png", 1px, 2px, 3px, 4px, false),
                              repeating-image-pattern("tile.png"),
                              -fx-base;
                          -fx-border-color:
                              linear-gradient(from 0% 0% to 100% 100%, repeat, red 0%, blue 100%)
                              image-pattern("border.png", 5%, 6%, 7%, 8%, true)
                              radial-gradient(radius 20px, red, blue)
                              #123456;
                          -fx-border-style:
                              solid dashed dotted hidden,
                              segments(1px, 2px, 3px) phase 4px outside line-join miter 5px line-cap square,
                              dashed centered line-join bevel line-cap butt;
                        }
                        Empty {
                          -fx-border-style: none;
                        }
                        Lookup {
                          -fx-border-style:
                              segments(-fx-dash-a, -fx-dash-b)
                              phase -fx-phase
                              outside
                              line-join miter -fx-miter
                              line-cap round;
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "region-references",
                """
                        Shape {
                          -fx-fill: region("#glyph");
                        }
                        Pane {
                          -fx-background-color:
                              region(".source"),
                              region("#badge"),
                              REGION(""),
                              regionXYZ(".escaped\\6f"),
                              region(".first", ".ignored"),
                              region(".series" ".ignored");
                        }
                        """,
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "legacy-gradients",
                """
                        LegacyLinear {
                          -fx-background-color:
                              linear (0%,0%) to (100%,100%) stops (0.0,red) (0.5,rgba(0, 255, 0, 0.5)) (1.0,blue) repeat;
                        }
                        LegacyRadial {
                          -fx-background-color:
                              radial focus-angle 45deg focus-distance 20% center (30%,40%) 50% stops (0.0,red) (0.5,green) (1.0,blue) no-cycle;
                        }
                        LegacyLookup {
                          -fx-background-color:
                              linear (-fx-start-x,-fx-start-y) to (-fx-end-x,-fx-end-y)
                              stops (-fx-stop-offset,-fx-base) (1.0,blue);
                        }
                        LegacyLadder {
                          -fx-fill:
                              ladder -fx-base stops
                              (0.0,black) (1.0,derive(-fx-base, 20%));
                          -fx-stroke:
                              ladder #123456 stops (0.5,white);
                        }
                        """,
                Syntax.SCSS
        ));
        if (target.supports(org.glavo.sassfx.JavaFXFeature.EXTENDED_BLEND_MODES)) {
            fixtures.add(new Fixture(
                    "extended-blend-mode",
                    "Pane { -fx-blend-mode: red; }",
                    Syntax.SCSS
            ));
        }
        if (target.supports(org.glavo.sassfx.JavaFXFeature.CSS_TRANSITIONS)) {
            fixtures.add(new Fixture(
                    "transition-basic",
                    """
                            Pane {
                              transition-property: -fx-opacity, -fx-rotate;
                              transition-duration: 100ms, 0.25s;
                              transition-delay: -10ms, 0s;
                              transition-timing-function: cubic-bezier(0.1, 0.2, 0.9, 0.8), steps(3, jump-both);
                              transition: -fx-opacity 100ms ease-in, -fx-rotate 250ms ease-out 20ms;
                            }
                            """,
                    Syntax.SCSS
            ));
        }
        if (target.supports(
                org.glavo.sassfx.JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES
        )) {
            fixtures.add(new Fixture(
                    "media-preferences",
                    """
                            @media (prefers-color-scheme: dark)
                                    and (prefers-reduced-motion) {
                              Pane { -fx-opacity: 1; }
                            }
                            """,
                    Syntax.SCSS
            ));
        }
        if (target.supports(
                org.glavo.sassfx.JavaFXFeature.MULTIPLE_RULES_PER_MEDIA_QUERY
        )) {
            fixtures.add(new Fixture(
                    "media-multiple-rules",
                    """
                            @media (prefers-color-scheme: dark) {
                              Pane { -fx-opacity: 1; }
                              Label { -fx-opacity: 0.75; }
                            }
                            """,
                    Syntax.SCSS
            ));
        }
        if (target.supports(org.glavo.sassfx.JavaFXFeature.VIEWPORT_MEDIA_QUERIES)) {
            fixtures.add(new Fixture(
                    "media-viewport",
                    """
                            @media (400px <= width < 800px)
                                    and (orientation: landscape) {
                              Pane { -fx-opacity: 1; }
                            }
                            """,
                    Syntax.SCSS
            ));
        }
        if (target.supports(
                org.glavo.sassfx.JavaFXFeature.ADVANCED_TRANSITION_EASING
        )) {
            fixtures.add(new Fixture(
                    "transition-advanced",
                    """
                            Pane {
                              transition-timing-function: cubic-bezier(0.1, -2, 0.9, 3);
                              transition: -fx-opacity 100ms linear(0, .1 25%, .75 50%, 1);
                            }
                            """,
                    Syntax.SCSS
            ));
        }
        if (target.supports(org.glavo.sassfx.JavaFXFeature.PLATFORM_MEDIA_FEATURE)) {
            fixtures.add(new Fixture(
                    "media-platform",
                    "@media (-fx-platform: windows) { Pane { -fx-opacity: 1; } }",
                    Syntax.SCSS
            ));
        }
        if (target == JavaFXTarget.JAVAFX27) {
            fixtures.add(new Fixture(
                    "font-shorthand",
                    """
                            Pane {
                              -fx-font: italic small-caps bold 14px/18px "Example Sans";
                            }
                            """,
                    Syntax.SCSS
            ));
            fixtures.add(new Fixture(
                    "paint-functions",
                    """
                            Pane {
                              -fx-text-fill: derive(#336699, -15%);
                              -fx-fill: ladder(-fx-base, black 0%, white 100%);
                              -fx-background-color: linear-gradient(derive(-fx-base, 20%), ladder(-fx-background, red 0%, white 100%));
                            }
                            """,
                    Syntax.SCSS
            ));
        }
        return List.copyOf(fixtures);
    }

    /// Returns fixtures that the compatibility validator must reject.
    ///
    /// @param target the selected JavaFX release
    /// @return the immutable rejected fixture list
    private static @Unmodifiable List<Fixture> rejectedFixtures(JavaFXTarget target) {
        var fixtures = new ArrayList<Fixture>();
        fixtures.add(new Fixture(
                "supports",
                "@supports (display: grid) { Pane { -fx-opacity: 1; } }",
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "unknown-at-rule",
                "@keyframes pulse { from { -fx-opacity: 0; } }",
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "native-nesting",
                ".parent { .child { -fx-opacity: 1; } }",
                Syntax.CSS
        ));
        fixtures.add(new Fixture(
                "media-type",
                "@media screen and (min-width: 600px) { Pane { -fx-opacity: 1; } }",
                Syntax.SCSS
        ));
        fixtures.add(new Fixture(
                "unknown-media-feature",
                "@media (hover) { Pane { -fx-opacity: 1; } }",
                Syntax.SCSS
        ));
        if (!target.supports(org.glavo.sassfx.JavaFXFeature.EXTENDED_BLEND_MODES)) {
            fixtures.add(new Fixture(
                    "extended-blend-mode",
                    "Pane { -fx-blend-mode: red; }",
                    Syntax.SCSS
            ));
        }
        if (!target.supports(org.glavo.sassfx.JavaFXFeature.CSS_TRANSITIONS)) {
            fixtures.add(new Fixture(
                    "transition-basic",
                    "Pane { transition-duration: 100ms; }",
                    Syntax.SCSS
            ));
        } else if (!target.supports(
                org.glavo.sassfx.JavaFXFeature.ADVANCED_TRANSITION_EASING
        )) {
            fixtures.add(new Fixture(
                    "transition-advanced",
                    "Pane { transition-timing-function: linear(0, 1); }",
                    Syntax.SCSS
            ));
        }
        if (!target.supports(
                org.glavo.sassfx.JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES
        )) {
            fixtures.add(new Fixture(
                    "media-preferences",
                    "@media (prefers-color-scheme: dark) { Pane { -fx-opacity: 1; } }",
                    Syntax.SCSS
            ));
        }
        if (target.supports(
                org.glavo.sassfx.JavaFXFeature.USER_PREFERENCE_MEDIA_QUERIES
        ) && !target.supports(
                org.glavo.sassfx.JavaFXFeature.MULTIPLE_RULES_PER_MEDIA_QUERY
        )) {
            fixtures.add(new Fixture(
                    "media-multiple-rules",
                    """
                            @media (prefers-color-scheme: dark) {
                              Pane { -fx-opacity: 1; }
                              Label { -fx-opacity: 0.75; }
                            }
                            """,
                    Syntax.SCSS
            ));
        }
        if (!target.supports(org.glavo.sassfx.JavaFXFeature.VIEWPORT_MEDIA_QUERIES)) {
            fixtures.add(new Fixture(
                    "media-viewport",
                    "@media (min-width: 600px) { Pane { -fx-opacity: 1; } }",
                    Syntax.SCSS
            ));
        }
        if (!target.supports(org.glavo.sassfx.JavaFXFeature.PLATFORM_MEDIA_FEATURE)) {
            fixtures.add(new Fixture(
                    "media-platform",
                    "@media (-fx-platform: windows) { Pane { -fx-opacity: 1; } }",
                    Syntax.SCSS
            ));
        }
        if (!target.supports(
                org.glavo.sassfx.JavaFXFeature.CONDITIONAL_STYLESHEET_IMPORTS
        )) {
            fixtures.add(new Fixture(
                    "conditional-import",
                    "@import \"theme.css\" (prefers-color-scheme: dark);",
                    Syntax.SCSS
            ));
        }
        return List.copyOf(fixtures);
    }

    /// Compiles one fixture and requires the OpenJFX parser to report no errors.
    ///
    /// @param fixture       the accepted fixture
    /// @param compatibility the target compatibility level
    /// @throws Exception if compilation or parsing fails
    private static void verifyAccepted(
            Fixture fixture,
            JavaFXTarget compatibility
    ) throws Exception {
        var css = compile(fixture, compatibility);
        CssParser.errorsProperty().clear();
        var stylesheet = new CssParser().parse(css);
        if (!CssParser.errorsProperty().isEmpty()) {
            throw new AssertionError(
                    fixture.name() + " produced JavaFX parser errors: "
                            + CssParser.errorsProperty()
            );
        }
        if (stylesheet.getRules().isEmpty()) {
            throw new AssertionError(fixture.name() + " produced no JavaFX rules.");
        }
        if (fixture.name().equals("media-multiple-rules")
                && stylesheet.getRules().size() != 2) {
            throw new AssertionError(
                    "media-multiple-rules produced "
                            + stylesheet.getRules().size()
                            + " JavaFX rules instead of 2."
            );
        }
        var compareBss = switch (fixture.name()) {
            case "effects",
                 "duration-scalars",
                 "quoted-strings",
                 "generic-size-sequence",
                 "font-size-keywords",
                 "region-geometry",
                 "region-images",
                 "region-paints-and-styles",
                 "region-references",
                 "legacy-gradients",
                 "extended-blend-mode",
                 "media-preferences",
                 "media-multiple-rules",
                 "media-viewport",
                 "media-platform",
                 "font-shorthand",
                 "paint-functions" -> true;
            default -> false;
        };
        if (compareBss) {
            verifyBss(fixture, css, compatibility);
        }
    }

    /// Compares SassFX BSS output with the pinned OpenJFX writer.
    ///
    /// @param fixture       the source fixture
    /// @param css           the compiled JavaFX CSS
    /// @param compatibility the selected JavaFX release
    /// @throws Exception if either writer fails or their bytes differ
    private static void verifyBss(
            Fixture fixture,
            String css,
            JavaFXTarget compatibility
    ) throws Exception {
        var directory = createOracleDirectory("sassfx-javafx-oracle-");
        var source = directory.resolve("fixture.css");
        var destination = directory.resolve("fixture.bss");
        try {
            Files.writeString(source, css);
            Stylesheet.convertToBinary(source.toFile(), destination.toFile());
            var expected = Files.readAllBytes(destination);
            var actual = remainingBytes(new SassCompiler().compile(
                    SassSource.fromString(
                            fixture.source(),
                            fixture.syntax(),
                            source.toUri()
                    ),
                    new BssTarget(compatibility)
            ).output());
            requireBssVersion(fixture.name(), expected, compatibility.bssVersion());
            requireBssVersion(fixture.name(), actual, compatibility.bssVersion());
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError(
                        fixture.name() + " BSS differs from OpenJFX output at byte "
                                + Arrays.mismatch(expected, actual)
                                + "; expected="
                                + Base64.getEncoder().encodeToString(expected)
                                + "; actual="
                                + Base64.getEncoder().encodeToString(actual)
                );
            }
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }

    /// Compares a resolved conditional import with OpenJFX BSS version 9.
    ///
    /// @param compatibility the JavaFX 27 compatibility level
    /// @throws Exception if filesystem compilation or byte comparison fails
    private static void verifyImportedBss(
            JavaFXTarget compatibility
    ) throws Exception {
        var directory = createOracleDirectory("sassfx-javafx-import-oracle-");
        var source = directory.resolve("fixture.css");
        var imported = directory.resolve("theme.css");
        var nested = directory.resolve("nested.css");
        var destination = directory.resolve("fixture.bss");
        @org.jetbrains.annotations.Nullable String binaryCss =
                System.getProperty("binary.css");
        try {
            System.setProperty("binary.css", "false");
            Files.writeString(
                    imported,
                    "ImportedPane { -fx-opacity: 0.75; }"
            );
            Files.writeString(
                    source,
                    """
                            @import "theme.css" (prefers-color-scheme: dark);
                            RootPane { -fx-opacity: 0.5; }
                            """
            );
            CssParser.errorsProperty().clear();
            Stylesheet.convertToBinary(source.toFile(), destination.toFile());
            if (!CssParser.errorsProperty().isEmpty()) {
                throw new AssertionError(
                        "conditional import produced JavaFX parser errors: "
                                + CssParser.errorsProperty()
                );
            }
            var expected = Files.readAllBytes(destination);
            var actual = remainingBytes(new SassCompiler().compile(
                    SassSource.fromFile(source),
                    new BssTarget(compatibility)
            ).output());
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError(
                        "conditional import BSS differs from OpenJFX output."
                );
            }

            Files.writeString(
                    nested,
                    "NestedPane { -fx-opacity: 0.25; }"
            );
            Files.writeString(
                    imported,
                    """
                            @import "nested.css" (prefers-reduced-motion);
                            ImportedPane { -fx-opacity: 0.75; }
                            """
            );
            CssParser.errorsProperty().clear();
            Stylesheet.convertToBinary(source.toFile(), destination.toFile());
            if (!CssParser.errorsProperty().isEmpty()) {
                throw new AssertionError(
                        "nested import produced JavaFX parser errors: "
                                + CssParser.errorsProperty()
                );
            }
            expected = Files.readAllBytes(destination);
            actual = remainingBytes(new SassCompiler().compile(
                    SassSource.fromFile(source),
                    new BssTarget(compatibility)
            ).output());
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError(
                        "nested import BSS differs from OpenJFX output."
                );
            }
        } finally {
            if (binaryCss == null) {
                System.clearProperty("binary.css");
            } else {
                System.setProperty("binary.css", binaryCss);
            }
            Files.deleteIfExists(destination);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(imported);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }

    /// Compares scalar and fill URL declarations with the selected OpenJFX writer.
    ///
    /// Both writers read the same file so the URL converter receives an
    /// identical stylesheet base URL.
    ///
    /// @param compatibility the selected JavaFX release
    /// @throws Exception if filesystem compilation or byte comparison fails
    private static void verifyScalarUrlBss(
            JavaFXTarget compatibility
    ) throws Exception {
        var directory = createOracleDirectory("sassfx-javafx-url-oracle-");
        var source = directory.resolve("fixture.css");
        var destination = directory.resolve("fixture.bss");
        try {
            Files.writeString(
                    source,
                    """
                            Pane {
                              -fx-graphic: url("icon.png");
                              -fx-fill: url("fill.png");
                            }
                            """
            );
            Stylesheet.convertToBinary(source.toFile(), destination.toFile());
            var expected = Files.readAllBytes(destination);
            var actual = remainingBytes(new SassCompiler().compile(
                    SassSource.fromFile(source),
                    new BssTarget(compatibility)
            ).output());
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError(
                        "scalar and fill URL BSS differs from OpenJFX output at byte "
                                + Arrays.mismatch(expected, actual)
                                + "; expected="
                                + Base64.getEncoder().encodeToString(expected)
                                + "; actual="
                                + Base64.getEncoder().encodeToString(actual)
                );
            }
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }

    /// Verifies the upstream transition BSS deserialization gap.
    ///
    /// OpenJFX writes the transition-specific converter names, but its binary
    /// converter registry does not reconstruct them. SassFX relies on this
    /// check when rejecting transition declarations from BSS output.
    ///
    /// @param target the selected JavaFX release
    /// @throws Exception if conversion, loading, compilation, or cleanup fails
    private static void verifyTransitionBssLimitation(
            JavaFXTarget target
    ) throws Exception {
        var directory = createOracleDirectory("sassfx-javafx-transition-oracle-");
        var source = directory.resolve("fixture.css");
        var destination = directory.resolve("fixture.bss");
        try {
            Files.writeString(
                    source,
                    """
                            Pane {
                              transition-duration: 100ms;
                              transition-delay: 10ms;
                              transition-timing-function: ease-in;
                              transition: -fx-opacity 100ms linear;
                            }
                            """
            );
            CssParser.errorsProperty().clear();
            Stylesheet.convertToBinary(source.toFile(), destination.toFile());
            if (!CssParser.errorsProperty().isEmpty()) {
                throw new AssertionError(
                        "transition BSS fixture produced JavaFX parser errors: "
                                + CssParser.errorsProperty()
                );
            }

            Stylesheet loaded;
            try (var input = Files.newInputStream(destination)) {
                loaded = Stylesheet.loadBinary(input);
            }
            if (loaded.getRules().size() != 1
                    || loaded.getRules().get(0).getDeclarations().size() != 4) {
                throw new AssertionError(
                        "transition BSS fixture did not retain its declarations."
                );
            }
            for (var declaration : loaded.getRules().get(0).getDeclarations()) {
                if (declaration.getParsedValue().getConverter() != null) {
                    throw new AssertionError(
                            declaration.getProperty()
                                    + " unexpectedly retained a BSS converter."
                    );
                }
            }
            try {
                new SassCompiler().compile(
                        SassSource.fromString(
                                Files.readString(source),
                                Syntax.SCSS
                        ),
                        new BssTarget(target)
                );
            } catch (SassCompilationException expected) {
                return;
            }
            throw new AssertionError(
                    "SassFX unexpectedly emitted transition BSS for JavaFX "
                            + target.version()
            );
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
    }

    /// Verifies parser semantics at the JavaFX 18 and JavaFX 23 boundaries.
    ///
    /// These checks inspect parsed values because older JavaFX parsers may
    /// retain unsupported declarations without reporting a syntax error.
    ///
    /// @param target the selected JavaFX release
    private static void verifyVersionedParserSemantics(JavaFXTarget target) {
        CssParser.errorsProperty().clear();
        var blendStylesheet = new CssParser().parse(
                "Pane { -fx-blend-mode: red; }"
        );
        @Nullable Object blendValue = blendStylesheet.getRules().isEmpty()
                || blendStylesheet.getRules().get(0).getDeclarations().isEmpty()
                ? null
                : blendStylesheet.getRules().get(0)
                .getDeclarations().get(0)
                .getParsedValue().getValue();
        var parsesExtendedBlendMode = blendValue instanceof String string
                && string.equalsIgnoreCase("red");
        if (parsesExtendedBlendMode != target.supports(
                org.glavo.sassfx.JavaFXFeature.EXTENDED_BLEND_MODES
        )) {
            throw new AssertionError(
                    "JavaFX " + target.version()
                            + " reported an unexpected red blend-mode representation: "
                            + blendValue
            );
        }

        CssParser.errorsProperty().clear();
        var transitionStylesheet = new CssParser().parse(
                "Pane { transition: -fx-opacity 100ms linear 10ms; }"
        );
        @Nullable Object transitionConverter = null;
        if (!transitionStylesheet.getRules().isEmpty()
                && !transitionStylesheet.getRules().get(0)
                .getDeclarations().isEmpty()) {
            transitionConverter = transitionStylesheet.getRules().get(0)
                    .getDeclarations().get(0)
                    .getParsedValue().getConverter();
        }
        var parsesTransition = transitionConverter != null
                && transitionConverter.getClass().getName()
                .contains("TransitionDefinitionConverter");
        if (parsesTransition != target.supports(
                org.glavo.sassfx.JavaFXFeature.CSS_TRANSITIONS
        )) {
            throw new AssertionError(
                    "JavaFX " + target.version()
                            + " reported an unexpected transition converter: "
                            + transitionConverter
            );
        }
    }

    /// Requires a BSS document to declare the expected format version.
    ///
    /// @param fixtureName the fixture used to produce the document
    /// @param bytes       the complete BSS document
    /// @param expected    the expected unsigned format version
    private static void requireBssVersion(
            String fixtureName,
            byte @Unmodifiable [] bytes,
            int expected
    ) {
        if (bytes.length < 2) {
            throw new AssertionError(fixtureName + " produced a truncated BSS document.");
        }
        var actual = (Byte.toUnsignedInt(bytes[0]) << 8)
                | Byte.toUnsignedInt(bytes[1]);
        if (actual != expected) {
            throw new AssertionError(
                    fixtureName + " produced BSS version " + actual
                            + " instead of " + expected + "."
            );
        }
    }

    /// Creates an oracle directory inside the project-writable build tree.
    ///
    /// @param prefix the temporary directory prefix
    /// @return the newly created directory
    /// @throws Exception if the build directory cannot be created
    private static Path createOracleDirectory(String prefix) throws Exception {
        var root = Path.of("build", "tmp", "javafx-oracle").toAbsolutePath();
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix);
    }

    /// Copies all remaining BSS bytes without changing the source buffer.
    ///
    /// @param buffer the read-only compiler output
    /// @return a newly allocated byte array
    private static byte[] remainingBytes(@Unmodifiable ByteBuffer buffer) {
        var copy = buffer.duplicate();
        var result = new byte[copy.remaining()];
        copy.get(result);
        return result;
    }

    /// Requires one fixture to fail before JavaFX CSS text is emitted.
    ///
    /// @param fixture       the rejected fixture
    /// @param compatibility the target compatibility level
    /// @throws Exception if reading the fixture source fails
    private static void verifyRejected(
            Fixture fixture,
            JavaFXTarget compatibility
    ) throws Exception {
        try {
            compile(fixture, compatibility);
        } catch (SassCompilationException expected) {
            return;
        }
        throw new AssertionError(fixture.name() + " unexpectedly compiled.");
    }

    /// Compiles one fixture to compressed JavaFX CSS.
    ///
    /// @param fixture       the source fixture
    /// @param compatibility the target compatibility level
    /// @return the generated JavaFX CSS
    /// @throws Exception if reading or compilation fails
    private static String compile(
            Fixture fixture,
            JavaFXTarget compatibility
    ) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(fixture.source(), fixture.syntax()),
                new JavaFXCssTarget(compatibility, OutputStyle.COMPRESSED)
        ).output();
    }

    /// Describes one in-memory compatibility fixture.
    ///
    /// @param name   the diagnostic fixture name
    /// @param source the stylesheet source
    /// @param syntax the source syntax
    @NotNullByDefault
    private record Fixture(String name, String source, Syntax syntax) {
    }
}
