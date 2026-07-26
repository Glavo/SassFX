// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.oracle;

import javafx.css.CssParser;
import javafx.css.Stylesheet;
import org.glavo.scssfx.BssTarget;
import org.glavo.scssfx.JavaFXCompatibility;
import org.glavo.scssfx.JavaFXCssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Arrays;

/// Verifies SCSSFX JavaFX CSS compatibility against a pinned OpenJFX parser.
///
/// This class belongs to an isolated development source set. It is absent from
/// product runtime and publication classpaths.
@NotNullByDefault
public final class JavaFxCssOracle {
    /// Prevents instantiation.
    private JavaFxCssOracle() {
    }

    /// Runs the fixture matrix for one JavaFX compatibility level.
    ///
    /// @param arguments one argument selecting {@code 17} or {@code 27}
    /// @throws Exception if compilation, oracle parsing, or validation fails
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one JavaFX version argument.");
        }

        var compatibility = switch (arguments[0]) {
            case "17" -> JavaFXCompatibility.JAVAFX17;
            case "27" -> JavaFXCompatibility.JAVAFX27;
            default -> throw new IllegalArgumentException(
                    "Unsupported JavaFX version: " + arguments[0]
            );
        };

        for (var fixture : acceptedFixtures(compatibility)) {
            verifyAccepted(fixture, compatibility);
        }
        for (var fixture : rejectedFixtures(compatibility)) {
            verifyRejected(fixture, compatibility);
        }
    }

    /// Returns fixtures that must compile and parse without JavaFX CSS errors.
    ///
    /// @param compatibility the target compatibility level
    /// @return the immutable accepted fixture list
    private static List<Fixture> acceptedFixtures(JavaFXCompatibility compatibility) {
        var common = List.of(
                new Fixture(
                        "basic",
                        "Pane { -fx-opacity: 0.5; -fx-text-fill: #ff0000; }",
                        Syntax.SCSS
                ),
                new Fixture(
                        "blend-mode",
                        "Pane { -fx-blend-mode: multiply; }",
                        Syntax.SCSS
                )
        );
        if (compatibility == JavaFXCompatibility.JAVAFX17) {
            return common;
        }

        return List.of(
                common.get(0),
                common.get(1),
                new Fixture(
                        "media",
                        """
                                @media (min-width: 600px) and (orientation: landscape) {
                                  Pane { -fx-opacity: 1; }
                                }
                                """,
                        Syntax.SCSS
                ),
                new Fixture(
                        "transition",
                        """
                                Pane {
                                  transition: -fx-opacity 100ms linear;
                                  -fx-blend-mode: red;
                                }
                                """,
                        Syntax.SCSS
                )
        );
    }

    /// Returns fixtures that the compatibility validator must reject.
    ///
    /// @param compatibility the target compatibility level
    /// @return the immutable rejected fixture list
    private static List<Fixture> rejectedFixtures(JavaFXCompatibility compatibility) {
        var common = List.of(
                new Fixture(
                        "supports",
                        "@supports (display: grid) { Pane { -fx-opacity: 1; } }",
                        Syntax.SCSS
                ),
                new Fixture(
                        "unknown-at-rule",
                        "@keyframes pulse { from { -fx-opacity: 0; } }",
                        Syntax.SCSS
                ),
                new Fixture(
                        "native-nesting",
                        ".parent { .child { -fx-opacity: 1; } }",
                        Syntax.CSS
                )
        );
        if (compatibility == JavaFXCompatibility.JAVAFX27) {
            return List.of(
                    common.get(0),
                    common.get(1),
                    common.get(2),
                    new Fixture(
                            "media-type",
                            "@media screen and (min-width: 600px) { Pane { -fx-opacity: 1; } }",
                            Syntax.SCSS
                    ),
                    new Fixture(
                            "unknown-media-feature",
                            "@media (hover) { Pane { -fx-opacity: 1; } }",
                            Syntax.SCSS
                    )
            );
        }

        return List.of(
                common.get(0),
                common.get(1),
                common.get(2),
                new Fixture(
                        "media",
                        "@media (min-width: 600px) { Pane { -fx-opacity: 1; } }",
                        Syntax.SCSS
                ),
                new Fixture(
                        "conditional-import",
                        "@import \"theme.css\" (prefers-color-scheme: dark);",
                        Syntax.SCSS
                ),
                new Fixture(
                        "transition",
                        "Pane { transition-duration: 100ms; }",
                        Syntax.SCSS
                ),
                new Fixture(
                        "blend-mode-color-name",
                        "Pane { -fx-blend-mode: red; }",
                        Syntax.SCSS
                )
        );
    }

    /// Compiles one fixture and requires the OpenJFX parser to report no errors.
    ///
    /// @param fixture       the accepted fixture
    /// @param compatibility the target compatibility level
    /// @throws Exception if compilation or parsing fails
    private static void verifyAccepted(
            Fixture fixture,
            JavaFXCompatibility compatibility
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
        if (compatibility == JavaFXCompatibility.JAVAFX27
                && fixture.name().equals("media")) {
            verifyBss(fixture, css, compatibility);
        }
    }

    /// Compares SCSSFX BSS output with the pinned OpenJFX writer.
    ///
    /// @param fixture       the source fixture
    /// @param css           the compiled JavaFX CSS
    /// @param compatibility the selected JavaFX release
    /// @throws Exception if either writer fails or their bytes differ
    private static void verifyBss(
            Fixture fixture,
            String css,
            JavaFXCompatibility compatibility
    ) throws Exception {
        var directory = Files.createTempDirectory("scssfx-javafx-oracle-");
        var source = directory.resolve("fixture.css");
        var destination = directory.resolve("fixture.bss");
        try {
            Files.writeString(source, css);
            Stylesheet.convertToBinary(source.toFile(), destination.toFile());
            var expected = Files.readAllBytes(destination);
            var actual = remainingBytes(new SassCompiler().compile(
                    SassSource.fromString(fixture.source(), fixture.syntax()),
                    new BssTarget(compatibility)
            ).output());
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError(
                        fixture.name() + " BSS differs from OpenJFX output."
                );
            }
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(source);
            Files.deleteIfExists(directory);
        }
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
            JavaFXCompatibility compatibility
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
            JavaFXCompatibility compatibility
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
