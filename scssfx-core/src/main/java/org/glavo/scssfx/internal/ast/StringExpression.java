// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents a quoted or unquoted Sass string literal.
///
/// @param text      the interpolation that produces the string contents
/// @param hasQuotes whether the source string is quoted
@ApiStatus.Internal
@NotNullByDefault
public record StringExpression(Interpolation text, boolean hasQuotes) implements SassExpression {
    /// Creates a string expression.
    public StringExpression {
        Objects.requireNonNull(text, "text");
    }

    /// Dispatches this expression to the string-expression visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassExpressionVisitor<R> visitor) {
        return visitor.visitStringExpression(this);
    }

    /// Creates an unquoted string containing no expression interpolation.
    ///
    /// @param text the plain string contents
    /// @param span the source range occupied by the string
    /// @return the unquoted string expression
    public static StringExpression plain(String text, SourceSpan span) {
        return new StringExpression(Interpolation.plain(text, span), false);
    }

    /// Returns the source range occupied by this string.
    ///
    /// @return the interpolation source range
    @Override
    public SourceSpan span() {
        return text.span();
    }

    /// Returns a Sass source representation of this string.
    ///
    /// @return the string source
    @Override
    public String toString() {
        if (!hasQuotes) {
            return text.toString();
        }

        var plainText = new StringBuilder();
        for (var part : text.parts()) {
            if (part instanceof TextInterpolationPart plain) {
                plainText.append(plain.text());
            }
        }
        var quote = plainText.indexOf("\"") >= 0 && plainText.indexOf("'") < 0
                ? '\''
                : '"';
        var result = new StringBuilder(plainText.length() + 2);
        result.append(quote);
        for (var part : text.parts()) {
            if (part instanceof ExpressionInterpolationPart expression) {
                result.append(expression);
                continue;
            }
            var contents = ((TextInterpolationPart) part).text();
            for (var index = 0; index < contents.length(); index++) {
                var character = contents.charAt(index);
                if (character == '\\' || character == quote) {
                    result.append('\\');
                }
                if (character == '\n' || character == '\r' || character == '\f') {
                    result.append('\\')
                            .append(Integer.toHexString(character))
                            .append(' ');
                } else {
                    result.append(character);
                }
            }
        }
        return result.append(quote).toString();
    }
}
