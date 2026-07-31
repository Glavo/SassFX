// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Parses the JavaFX font property grammar shared by CSS and BSS output.
///
/// OpenJFX recognizes font properties by suffix. Font shorthand values contain
/// one family token and one size, optionally preceded by style, small-caps, and
/// weight identifiers and optionally separated from a discarded line-height
/// token by `/`. Leading global keywords with surplus terms are rejected
/// because OpenJFX would silently ignore the remainder. This class reproduces
/// the safe grammar without loading JavaFX.
@ApiStatus.Internal
@NotNullByDefault
public final class JavaFXFontParser {
    /// Contains generic family names normalized by OpenJFX.
    private static final @Unmodifiable Set<String> GENERIC_FAMILIES = Set.of(
            "inherit",
            "serif",
            "sans-serif",
            "cursive",
            "fantasy",
            "monospace"
    );

    /// Contains font-size keywords and their OpenJFX percentage values.
    private static final @Unmodifiable Map<String, Double> FONT_SIZE_KEYWORDS =
            Map.ofEntries(
                    Map.entry("xx-small", 60.0),
                    Map.entry("x-small", 75.0),
                    Map.entry("small", 80.0),
                    Map.entry("smaller", 80.0),
                    Map.entry("inherit", 100.0),
                    Map.entry("medium", 100.0),
                    Map.entry("large", 120.0),
                    Map.entry("larger", 120.0),
                    Map.entry("x-large", 150.0),
                    Map.entry("xx-large", 200.0)
            );

    /// Contains units accepted by OpenJFX's font-size parser.
    private static final @Unmodifiable Set<String> FONT_SIZE_UNITS = Set.of(
            "%",
            "em",
            "ex",
            "px",
            "cm",
            "mm",
            "in",
            "pt",
            "pc",
            "deg",
            "grad",
            "rad",
            "turn"
    );

    /// Prevents instantiation.
    private JavaFXFontParser() {
    }

    /// Returns whether a complete value is a JavaFX global keyword.
    ///
    /// Global keywords bypass property-specific font parsing in OpenJFX.
    /// Leading and trailing JavaFX trivia is ignored.
    ///
    /// @param text the declaration value without `!important`
    /// @param span the source range associated with the value
    /// @return whether the value is exactly `inherit`, `none`, or `null`
    /// @throws CssSerializeException if the value cannot be tokenized as a font value
    public static boolean isGlobalKeyword(String text, SourceSpan span) {
        var tokens = tokenize(text, span);
        if (tokens.size() != 1 || tokens.get(0).type() != TokenType.IDENTIFIER) {
            return false;
        }
        return isGlobalKeyword(tokens.get(0).text());
    }

    /// Parses one JavaFX font-family value.
    ///
    /// Generic family names are normalized to lowercase even when quoted.
    /// Other strings retain their quotes because OpenJFX persists the complete
    /// lexer token in BSS.
    ///
    /// @param text the declaration value without `!important`
    /// @param span the source range associated with the value
    /// @return the exact family text persisted by OpenJFX
    /// @throws CssSerializeException if the value is not one identifier or string token
    public static String parseFamily(String text, SourceSpan span) {
        var tokens = tokenize(text, span);
        if (tokens.size() != 1) {
            throw invalidFamily(span);
        }
        return family(tokens.get(0), span);
    }

    /// Parses one JavaFX font-size value.
    ///
    /// Unitless numbers use pixels. Font-size keywords are expanded to the
    /// percentage values stored by OpenJFX.
    ///
    /// @param text the declaration value without `!important`
    /// @param span the source range associated with the value
    /// @return the finite size stored by OpenJFX
    /// @throws CssSerializeException if the value is not one supported size
    public static Size parseSize(String text, SourceSpan span) {
        var tokens = tokenize(text, span);
        if (tokens.size() != 1) {
            throw invalidSize(span);
        }
        @Nullable var size = fontSize(tokens.get(0));
        if (size == null) {
            throw invalidSize(span);
        }
        return size;
    }

    /// Parses one JavaFX font-style value.
    ///
    /// @param text the declaration value without `!important`
    /// @param span the source range associated with the value
    /// @return `REGULAR`, `ITALIC`, or the inherited marker
    /// @throws CssSerializeException if the value is not one supported identifier
    public static String parseStyle(String text, SourceSpan span) {
        var tokens = tokenize(text, span);
        if (tokens.size() != 1 || tokens.get(0).type() != TokenType.IDENTIFIER) {
            throw invalidStyle(span);
        }
        @Nullable var style = fontStyle(tokens.get(0).text());
        if (style == null) {
            throw invalidStyle(span);
        }
        return style;
    }

    /// Parses one JavaFX font-weight value.
    ///
    /// @param text the declaration value without `!important`
    /// @param span the source range associated with the value
    /// @return the JavaFX `FontWeight` enum spelling stored in BSS
    /// @throws CssSerializeException if the value is not a named or exact numeric weight
    public static String parseWeight(String text, SourceSpan span) {
        var tokens = tokenize(text, span);
        if (tokens.size() != 1) {
            throw invalidWeight(span);
        }
        @Nullable var weight = fontWeight(tokens.get(0));
        if (weight == null) {
            throw invalidWeight(span);
        }
        return weight;
    }

    /// Parses one JavaFX font shorthand.
    ///
    /// OpenJFX checks an optional line height only for a size-token shape and
    /// then discards it. The returned value therefore contains only family,
    /// size, weight, and style, matching the four persisted converter slots.
    ///
    /// @param text the declaration value without `!important`
    /// @param span the source range associated with the value
    /// @return the four retained shorthand components
    /// @throws CssSerializeException if the value does not follow JavaFX's grammar
    public static Font parseShorthand(String text, SourceSpan span) {
        var tokens = tokenize(text, span);
        if (tokens.size() < 2) {
            throw invalidShorthand(span);
        }
        if (tokens.get(0).type() == TokenType.IDENTIFIER
                && isGlobalKeyword(tokens.get(0).text())) {
            // OpenJFX would return the first global token and silently ignore
            // the rest of the shorthand.
            throw invalidShorthand(span);
        }

        var family = family(tokens.get(tokens.size() - 1), span);
        var sizeIndex = tokens.size() - 2;
        if (sizeIndex >= 2
                && tokens.get(sizeIndex - 1).type() == TokenType.SLASH) {
            if (!isSizeToken(tokens.get(sizeIndex))) {
                throw invalidShorthand(span);
            }
            sizeIndex -= 2;
        }

        @Nullable var size = fontSize(tokens.get(sizeIndex));
        if (size == null) {
            throw invalidShorthand(span);
        }

        @Nullable String weight = null;
        @Nullable String style = null;
        var sawVariant = false;
        for (var index = sizeIndex - 1; index >= 0; index--) {
            var token = tokens.get(index);
            if (token.type() != TokenType.IDENTIFIER) {
                throw invalidShorthand(span);
            }
            var normalized = token.text().toLowerCase(Locale.ROOT);
            @Nullable var parsedStyle = fontStyle(normalized);
            if (style == null && parsedStyle != null) {
                style = parsedStyle;
            } else if (!sawVariant && normalized.equals("small-caps")) {
                sawVariant = true;
            } else if (weight == null) {
                @Nullable var parsedWeight = namedFontWeight(normalized);
                if (parsedWeight == null) {
                    throw invalidShorthand(span);
                }
                weight = parsedWeight;
            } else {
                throw invalidShorthand(span);
            }
        }
        return new Font(family, size, weight, style);
    }

    /// Returns the stored family representation for one token.
    ///
    /// @param token the candidate family token
    /// @param span  the source range associated with the value
    /// @return the normalized generic name or original token text
    /// @throws CssSerializeException if the token is not a family token
    private static String family(Token token, SourceSpan span) {
        if (token.type() != TokenType.IDENTIFIER
                && token.type() != TokenType.STRING) {
            throw invalidFamily(span);
        }
        var unquoted = token.type() == TokenType.STRING
                ? token.text().substring(1, token.text().length() - 1)
                : token.text();
        var normalized = unquoted.toLowerCase(Locale.ROOT);
        return GENERIC_FAMILIES.contains(normalized)
                ? normalized
                : token.text();
    }

    /// Parses one token as a font size.
    ///
    /// @param token the candidate size token
    /// @return the normalized size, or `null` when invalid
    private static @Nullable Size fontSize(Token token) {
        if (token.type() == TokenType.IDENTIFIER) {
            @Nullable var percentage = FONT_SIZE_KEYWORDS.get(
                    token.text().toLowerCase(Locale.ROOT)
            );
            return percentage == null ? null : new Size(percentage, "%");
        }
        if (token.type() != TokenType.NUMBER) {
            return null;
        }
        return numericSize(token.text());
    }

    /// Returns whether a token passes OpenJFX's discarded line-height check.
    ///
    /// @param token the candidate token
    /// @return whether it is an identifier or supported numeric size
    private static boolean isSizeToken(Token token) {
        if (token.type() == TokenType.IDENTIFIER) {
            return true;
        }
        if (token.type() != TokenType.NUMBER) {
            return false;
        }
        return numericSize(token.text()) != null;
    }

    /// Returns the canonical font posture for one identifier.
    ///
    /// @param text the identifier text
    /// @return the stored posture spelling, or `null` when unsupported
    private static @Nullable String fontStyle(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "normal" -> "REGULAR";
            case "italic", "oblique" -> "ITALIC";
            case "inherit" -> "inherit";
            default -> null;
        };
    }

    /// Returns the canonical font weight for one token.
    ///
    /// @param token the candidate identifier or number
    /// @return the stored weight spelling, or `null` when unsupported
    private static @Nullable String fontWeight(Token token) {
        if (token.type() == TokenType.IDENTIFIER) {
            return namedFontWeight(token.text());
        }
        if (token.type() != TokenType.NUMBER) {
            return null;
        }
        return switch (token.text()) {
            case "100" -> "THIN";
            case "200" -> "EXTRA_LIGHT";
            case "300" -> "LIGHT";
            case "400" -> "NORMAL";
            case "500" -> "MEDIUM";
            case "600" -> "SEMI_BOLD";
            case "700" -> "BOLD";
            case "800" -> "EXTRA_BOLD";
            case "900" -> "BLACK";
            default -> null;
        };
    }

    /// Returns the canonical font weight for one identifier.
    ///
    /// @param text the identifier text
    /// @return the stored weight spelling, or `null` when unsupported
    private static @Nullable String namedFontWeight(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "inherit", "normal" -> "NORMAL";
            case "bold", "bolder" -> "BOLD";
            case "lighter" -> "LIGHT";
            default -> null;
        };
    }

    /// Parses one numeric token into a finite value and unit.
    ///
    /// @param text the complete JavaFX number token
    /// @return the parsed size, or `null` for a non-finite number
    private static @Nullable Size numericSize(String text) {
        var unitStart = text.length();
        while (unitStart > 0) {
            var character = text.charAt(unitStart - 1);
            if (character == '%'
                    || character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z') {
                unitStart--;
            } else {
                break;
            }
        }
        var value = Double.parseDouble(text.substring(0, unitStart));
        if (!Double.isFinite(value)) {
            return null;
        }
        @Nullable var unit = unitStart == text.length()
                ? null
                : text.substring(unitStart).toLowerCase(Locale.ROOT);
        return isFontSizeUnit(unit) ? new Size(value, unit) : null;
    }

    /// Returns whether a unit belongs to OpenJFX's font-size token set.
    ///
    /// @param unit the lowercase unit, or `null` for a unitless number
    /// @return whether the unit is accepted for font sizes
    private static boolean isFontSizeUnit(@Nullable String unit) {
        return unit == null || FONT_SIZE_UNITS.contains(unit);
    }

    /// Returns whether an identifier is a JavaFX global keyword.
    ///
    /// @param text the identifier text
    /// @return whether OpenJFX short-circuits property-specific parsing
    private static boolean isGlobalKeyword(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "inherit", "none", "null" -> true;
            default -> false;
        };
    }

    /// Tokenizes a complete value using the JavaFX font grammar subset.
    ///
    /// @param text the declaration value without `!important`
    /// @param span the source range associated with the value
    /// @return immutable tokens without trivia
    /// @throws CssSerializeException if an unsupported token is encountered
    private static @Unmodifiable List<Token> tokenize(
            String text,
            SourceSpan span
    ) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(span, "span");
        var tokens = new ArrayList<Token>();
        var index = 0;
        while (index < text.length()) {
            var triviaEnd = JavaFXCssLexer.triviaEnd(text, index);
            if (triviaEnd < 0) {
                throw invalidToken(span);
            }
            if (triviaEnd > index) {
                index = triviaEnd;
                continue;
            }

            var character = text.charAt(index);
            if (character == '\'' || character == '"') {
                var end = JavaFXCssLexer.stringEnd(
                        text,
                        index,
                        character
                );
                if (end < 0) {
                    throw invalidToken(span);
                }
                tokens.add(new Token(TokenType.STRING, text.substring(index, end)));
                index = end;
                continue;
            }

            var identifierEnd = JavaFXCssLexer.identifierEnd(text, index);
            if (identifierEnd > index) {
                tokens.add(new Token(
                        TokenType.IDENTIFIER,
                        text.substring(index, identifierEnd)
                ));
                index = identifierEnd;
                continue;
            }

            var numberEnd = JavaFXCssLexer.numberEnd(text, index);
            if (numberEnd > index) {
                tokens.add(new Token(
                        TokenType.NUMBER,
                        text.substring(index, numberEnd)
                ));
                index = numberEnd;
                continue;
            }
            if (numberEnd < 0) {
                throw invalidToken(span);
            }

            if (character == '/') {
                tokens.add(new Token(TokenType.SLASH, "/"));
                index++;
                continue;
            }
            throw invalidToken(span);
        }
        return List.copyOf(tokens);
    }

    /// Creates the standard failure for an invalid font family.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidFamily(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX font family requires one identifier or string.",
                span,
                null
        );
    }

    /// Creates the standard failure for an invalid font size.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidSize(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX font size requires one size or font-size keyword.",
                span,
                null
        );
    }

    /// Creates the standard failure for an invalid font style.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidStyle(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX font style requires normal, italic, oblique, or inherit.",
                span,
                null
        );
    }

    /// Creates the standard failure for an invalid font weight.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidWeight(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX font weight requires a named weight or an exact 100-to-900 value.",
                span,
                null
        );
    }

    /// Creates the standard failure for an invalid font shorthand.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidShorthand(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX font shorthand requires optional style, small-caps, and"
                        + " weight identifiers followed by a size, optional"
                        + " line height, and one font family.",
                span,
                null
        );
    }

    /// Creates the standard failure for an unsupported font token.
    ///
    /// @param span the source range associated with the invalid value
    /// @return the source-associated failure
    private static CssSerializeException invalidToken(SourceSpan span) {
        return new CssSerializeException(
                "JavaFX cannot tokenize this font value.",
                span,
                null
        );
    }

    /// Stores one finite JavaFX size.
    ///
    /// @param value the finite numeric magnitude
    /// @param unit  the lowercase JavaFX unit, or `null` for a unitless number
    @NotNullByDefault
    public record Size(double value, @Nullable String unit) {
        /// Validates one immutable size.
        public Size {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("value must be finite");
            }
            if (!isFontSizeUnit(unit)) {
                throw new IllegalArgumentException("unsupported font-size unit: " + unit);
            }
        }
    }

    /// Stores the four font shorthand components retained by OpenJFX.
    ///
    /// @param family the normalized or token-preserving family text
    /// @param size   the normalized font size
    /// @param weight the JavaFX font-weight spelling, or `null`
    /// @param style  the JavaFX font-posture spelling, or `null`
    @NotNullByDefault
    public record Font(
            String family,
            Size size,
            @Nullable String weight,
            @Nullable String style
    ) {
        /// Validates retained font components.
        public Font {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(size, "size");
        }
    }

    /// Identifies tokens used by JavaFX font properties.
    @NotNullByDefault
    private enum TokenType {
        /// Identifies an ASCII JavaFX identifier.
        IDENTIFIER,

        /// Identifies a legacy quoted string.
        STRING,

        /// Identifies a number with an optional JavaFX unit.
        NUMBER,

        /// Identifies the line-height separator.
        SLASH
    }

    /// Stores one JavaFX font token.
    ///
    /// @param type the token kind
    /// @param text the complete token text
    @NotNullByDefault
    private record Token(TokenType type, String text) {
        /// Validates one immutable token.
        private Token {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(text, "text");
        }
    }
}
