// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.glavo.sassfx.JavaFXFeature.EXTENDED_BLEND_MODES;

/// Parses scalar declaration forms that OpenJFX dispatches before its generic
/// value parser.
///
/// The parser models global declaration keywords, font-smoothing strings,
/// versioned blend-mode parsing, the three stroke enums, and stroke dash
/// arrays. It requires each special value to be complete rather than accepting
/// suffixes that OpenJFX would silently discard.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXScalarParser {
    /// Contains blend-mode identifiers introduced by JavaFX 18.
    private static final @Unmodifiable Set<String> EXTENDED_BLEND_MODE_VALUES =
            Set.of("add", "blue", "green", "red");

    /// Contains every unit accepted by OpenJFX's stroke size parser.
    private static final @Unmodifiable Set<String> SIZE_UNITS = Set.of(
            "%",
            "cm",
            "deg",
            "em",
            "ex",
            "grad",
            "in",
            "mm",
            "pc",
            "pt",
            "px",
            "rad",
            "turn"
    );

    /// Contains the keywords accepted by `-fx-stroke-line-cap`.
    private static final @Unmodifiable Set<String> STROKE_LINE_CAPS = Set.of(
            "butt",
            "round",
            "square"
    );

    /// Contains the keywords accepted by `-fx-stroke-line-join`.
    private static final @Unmodifiable Set<String> STROKE_LINE_JOINS = Set.of(
            "bevel",
            "miter",
            "round"
    );

    /// Contains the keywords accepted by `-fx-stroke-type`.
    private static final @Unmodifiable Set<String> STROKE_TYPES = Set.of(
            "centered",
            "inside",
            "outside"
    );

    /// Prevents instantiation.
    private JavaFXScalarParser() {
    }

    /// Returns whether text names a blend mode introduced in JavaFX 18.
    ///
    /// @param text the quoted or unquoted scalar contents
    /// @return whether the value is one of the four extended blend modes
    public static boolean isExtendedBlendMode(String text) {
        Objects.requireNonNull(text, "text");
        return EXTENDED_BLEND_MODE_VALUES.contains(
                text.toLowerCase(Locale.ROOT)
        );
    }

    /// Parses one declaration when it uses OpenJFX's special scalar dispatch.
    ///
    /// Global keywords are recognized for every property. Other properties
    /// return `null` unless OpenJFX assigns them one of the scalar grammars
    /// modeled by this class.
    ///
    /// @param property the lowercase declaration name
    /// @param text     the complete value without `!important`
    /// @param span     the source range associated with the value
    /// @param compatibility the selected JavaFX release
    /// @return the parsed scalar value, or `null` when the property uses a
    /// different grammar
    /// @throws CssSerializeException if a recognized scalar value is invalid
    /// or contains surplus tokens
    public static @Nullable Value parse(
            String property,
            String text,
            SourceSpan span,
            JavaFXTarget compatibility
    ) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(compatibility, "compatibility");

        @Nullable var globalKeyword = canonicalGlobalKeyword(text, span);
        if (globalKeyword != null) {
            return new GlobalKeyword(globalKeyword);
        }

        return switch (property) {
            case "-fx-font-smoothing-type" ->
                    new StoredString(parseStoredString(text, span));
            case "-fx-blend-mode" -> compatibility.supports(
                    EXTENDED_BLEND_MODES
            )
                    ? new StoredString(parseStoredString(text, span))
                    : parseLegacyBlendString(text, span);
            case "-fx-stroke-line-cap" -> new EnumValue(
                    parseEnumKeyword(
                            text,
                            STROKE_LINE_CAPS,
                            "JavaFX stroke line cap requires butt, round, or square.",
                            span
                    ),
                    "javafx.scene.shape.StrokeLineCap"
            );
            case "-fx-stroke-line-join" -> new EnumValue(
                    parseEnumKeyword(
                            text,
                            STROKE_LINE_JOINS,
                            "JavaFX stroke line join requires bevel, miter, or round.",
                            span
                    ),
                    "javafx.scene.shape.StrokeLineJoin"
            );
            case "-fx-stroke-type" -> new EnumValue(
                    parseEnumKeyword(
                            text,
                            STROKE_TYPES,
                            "JavaFX stroke type requires centered, inside, or outside.",
                            span
                    ),
                    "javafx.scene.shape.StrokeType"
            );
            case "-fx-stroke-dash-array" ->
                    new SizeSequence(parseSizeSequence(text, span));
            default -> null;
        };
    }

    /// Returns the canonical representation of a leading global keyword.
    ///
    /// OpenJFX represents both `none` and `null` as `null`. A leading global
    /// keyword followed by any other token is rejected because OpenJFX would
    /// silently ignore the suffix.
    ///
    /// @param text the complete declaration value
    /// @param span the source range associated with the value
    /// @return `inherit` or `null`, or `null` when the value does not begin
    /// with a global keyword
    /// @throws CssSerializeException if a leading global keyword has a suffix
    private static @Nullable String canonicalGlobalKeyword(
            String text,
            SourceSpan span
    ) {
        var start = JavaFXCssLexer.triviaEnd(text, 0);
        if (start < 0 || start == text.length()) {
            return null;
        }
        var end = JavaFXCssLexer.identifierEnd(text, start);
        if (end == start) {
            return null;
        }
        @Nullable var canonical = switch (
                text.substring(start, end).toLowerCase(Locale.ROOT)
        ) {
            case "inherit" -> "inherit";
            case "none", "null" -> "null";
            default -> null;
        };
        if (canonical == null) {
            return null;
        }
        if (JavaFXCssLexer.triviaEnd(text, end) != text.length()) {
            throw new CssSerializeException(
                    "JavaFX global declaration keywords cannot have surplus tokens.",
                    span,
                    null
            );
        }
        return canonical;
    }

    /// Parses a JavaFX 8–17 blend string while leaving other token kinds to
    /// the generic value parser.
    ///
    /// @param text the complete declaration value
    /// @param span the source range associated with the value
    /// @return the complete string token, or `null` for a generic non-string
    /// value
    /// @throws CssSerializeException if a leading string or identifier has
    /// surplus tokens
    private static @Nullable LegacyString parseLegacyBlendString(
            String text,
            SourceSpan span
    ) {
        var start = JavaFXCssLexer.triviaEnd(text, 0);
        if (start < 0 || start == text.length()) {
            return null;
        }
        var first = text.charAt(start);
        if (first != '\''
                && first != '"'
                && JavaFXCssLexer.identifierEnd(text, start) == start) {
            return null;
        }
        return new LegacyString(parseStoredString(text, span));
    }

    /// Parses one string or identifier stored directly by OpenJFX.
    ///
    /// @param text the complete declaration value
    /// @param span the source range associated with the value
    /// @return the identifier or unquoted string contents
    /// @throws CssSerializeException if the value is not exactly one string or
    /// identifier
    private static String parseStoredString(String text, SourceSpan span) {
        var start = JavaFXCssLexer.triviaEnd(text, 0);
        if (start < 0 || start == text.length()) {
            throw invalidStoredString(span);
        }

        final int end;
        final String value;
        var first = text.charAt(start);
        if (first == '\'' || first == '"') {
            end = JavaFXCssLexer.stringEnd(text, start, first);
            if (end < 0) {
                throw invalidStoredString(span);
            }
            value = text.substring(start + 1, end - 1);
        } else {
            end = JavaFXCssLexer.identifierEnd(text, start);
            if (end == start) {
                throw invalidStoredString(span);
            }
            value = text.substring(start, end);
        }

        if (JavaFXCssLexer.triviaEnd(text, end) != text.length()) {
            throw invalidStoredString(span);
        }
        return value;
    }

    /// Parses one complete case-insensitive stroke enum keyword.
    ///
    /// @param text     the complete declaration value
    /// @param keywords the accepted lowercase enum keywords
    /// @param message  the diagnostic used for an invalid value
    /// @param span     the source range associated with the value
    /// @return the canonical lowercase keyword
    /// @throws CssSerializeException if the value is not one accepted
    /// identifier
    private static String parseEnumKeyword(
            String text,
            Set<String> keywords,
            String message,
            SourceSpan span
    ) {
        var start = JavaFXCssLexer.triviaEnd(text, 0);
        if (start < 0 || start == text.length()) {
            throw new CssSerializeException(message, span, null);
        }
        var end = JavaFXCssLexer.identifierEnd(text, start);
        if (end == start
                || JavaFXCssLexer.triviaEnd(text, end) != text.length()) {
            throw new CssSerializeException(message, span, null);
        }
        var keyword = text.substring(start, end).toLowerCase(Locale.ROOT);
        if (!keywords.contains(keyword)) {
            throw new CssSerializeException(message, span, null);
        }
        return keyword;
    }

    /// Parses one non-empty sequence of JavaFX size tokens.
    ///
    /// @param text the complete declaration value
    /// @param span the source range associated with the value
    /// @return immutable sizes in source order
    /// @throws CssSerializeException if any token is not a JavaFX size
    private static @Unmodifiable List<Size> parseSizeSequence(
            String text,
            SourceSpan span
    ) {
        var sizes = new ArrayList<Size>();
        var index = 0;
        while (index < text.length()) {
            var start = JavaFXCssLexer.triviaEnd(text, index);
            if (start < 0) {
                throw invalidSizeSequence(span);
            }
            if (start == text.length()) {
                break;
            }
            var end = JavaFXCssLexer.numberEnd(text, start);
            if (end <= start) {
                throw invalidSizeSequence(span);
            }
            sizes.add(parseSize(text.substring(start, end), span));
            index = end;
        }
        if (sizes.isEmpty()) {
            throw invalidSizeSequence(span);
        }
        return List.copyOf(sizes);
    }

    /// Parses one JavaFX number token into a finite size.
    ///
    /// @param token the complete number token
    /// @param span  the source range associated with the value
    /// @return the parsed magnitude and lowercase unit
    /// @throws CssSerializeException if the token is non-finite or has an
    /// unsupported unit
    private static Size parseSize(String token, SourceSpan span) {
        var unitStart = token.length();
        while (unitStart > 0) {
            var character = token.charAt(unitStart - 1);
            if (character == '%'
                    || character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z') {
                unitStart--;
            } else {
                break;
            }
        }
        var value = Double.parseDouble(token.substring(0, unitStart));
        @Nullable var unit = unitStart == token.length()
                ? null
                : token.substring(unitStart).toLowerCase(Locale.ROOT);
        if (!Double.isFinite(value)
                || unit != null && !SIZE_UNITS.contains(unit)) {
            throw invalidSizeSequence(span);
        }
        return new Size(value, unit);
    }

    /// Creates the standard failure for a stored-string scalar.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidStoredString(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX font smoothing and blend modes require one string or identifier.",
                span,
                null
        );
    }

    /// Creates the standard failure for a stroke dash array.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidSizeSequence(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX stroke dash arrays require one or more size values.",
                span,
                null
        );
    }

    /// Identifies one parsed special scalar representation.
    @NotNullByDefault
    public sealed interface Value permits
            GlobalKeyword,
            StoredString,
            LegacyString,
            EnumValue,
            SizeSequence {
    }

    /// Stores a canonical JavaFX global declaration keyword.
    ///
    /// @param text `inherit` or `null`
    @NotNullByDefault
    public record GlobalKeyword(String text) implements Value {
        /// Validates one canonical global keyword.
        public GlobalKeyword {
            if (!text.equals("inherit") && !text.equals("null")) {
                throw new IllegalArgumentException(
                        "text must be inherit or null"
                );
            }
        }
    }

    /// Stores the unquoted text retained by an OpenJFX scalar parser.
    ///
    /// @param text the retained string, which may be empty
    @NotNullByDefault
    public record StoredString(String text) implements Value {
        /// Validates one retained string.
        public StoredString {
            Objects.requireNonNull(text, "text");
        }
    }

    /// Stores one blend-mode token routed through JavaFX 8–17's generic
    /// parser.
    ///
    /// The BSS backend must retain the generic parser's lookup and color
    /// recognition rather than serializing this text directly.
    ///
    /// @param text the unquoted scalar text
    @NotNullByDefault
    public record LegacyString(String text) implements Value {
        /// Validates one legacy scalar token.
        public LegacyString {
            Objects.requireNonNull(text, "text");
        }
    }

    /// Stores one canonical stroke enum value and its BSS converter argument.
    ///
    /// @param text      the lowercase enum keyword
    /// @param enumClass the JavaFX enum class name
    @NotNullByDefault
    public record EnumValue(String text, String enumClass) implements Value {
        /// Validates one enum representation.
        public EnumValue {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(enumClass, "enumClass");
        }
    }

    /// Stores one non-empty immutable stroke dash array.
    ///
    /// @param sizes the sizes in source order
    @NotNullByDefault
    public record SizeSequence(@Unmodifiable List<Size> sizes) implements Value {
        /// Copies and validates one size sequence.
        public SizeSequence {
            sizes = List.copyOf(sizes);
            if (sizes.isEmpty()) {
                throw new IllegalArgumentException("sizes must not be empty");
            }
        }
    }

    /// Stores one finite JavaFX scalar size.
    ///
    /// @param value the finite numeric magnitude
    /// @param unit  the lowercase unit, or `null` for a unitless size
    @NotNullByDefault
    public record Size(double value, @Nullable String unit) {
        /// Validates one immutable size.
        public Size {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("value must be finite");
            }
            if (unit != null && !SIZE_UNITS.contains(unit)) {
                throw new IllegalArgumentException("unsupported size unit: " + unit);
            }
        }
    }
}
