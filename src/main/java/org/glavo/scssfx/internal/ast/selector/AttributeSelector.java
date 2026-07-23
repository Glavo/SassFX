// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Selects elements by the presence or value of an attribute.
///
/// The parsed name, matcher, decoded value, and modifier provide structural
/// data for selector operations. {@code css} retains the exact parsed spelling,
/// including optional whitespace, quote choices, and escapes, for output.
///
/// @param name     the qualified attribute name
/// @param matcher  the value matcher, or {@code null} for presence matching
/// @param value    the decoded attribute value, or {@code null} for presence matching
/// @param modifier the case-sensitivity modifier, or {@code null}
/// @param css      the exact CSS spelling including brackets
/// @param span     the source span
@ApiStatus.Internal
@NotNullByDefault
public record AttributeSelector(
        QualifiedName name,
        @Nullable AttributeMatcher matcher,
        @Nullable String value,
        @Nullable CssIdentifier modifier,
        String css,
        SourceSpan span
) implements SimpleSelector {
    /// Creates an attribute selector.
    ///
    /// @throws IllegalArgumentException if matcher, value, and modifier do not
    ///                                  describe one valid attribute form
    public AttributeSelector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(css, "css");
        if (css.isEmpty()) {
            throw new IllegalArgumentException("css must not be empty");
        }
        Objects.requireNonNull(span, "span");
        if (matcher == null && (value != null || modifier != null)) {
            throw new IllegalArgumentException(
                    "an attribute without a matcher must not have a value or modifier"
            );
        }
        if (matcher != null && value == null) {
            throw new IllegalArgumentException("an attribute matcher requires a value");
        }
    }

    /// Returns whether this selector has the same structured attribute constraint as {@code other}.
    ///
    /// Source spelling differences, such as quoted versus unquoted values or
    /// equivalent escapes, do not affect the comparison because values are
    /// already decoded by the selector parser.
    ///
    /// @param other the attribute selector to compare
    /// @return whether both selectors have equivalent modeled fields
    public boolean hasSameValue(AttributeSelector other) {
        Objects.requireNonNull(other, "other");
        return name.hasSameValue(other.name)
                && matcher == other.matcher
                && Objects.equals(value, other.value)
                && identifiersHaveSameValue(modifier, other.modifier);
    }

    @Override
    public String toCssString() {
        return css;
    }

    @Override
    public SimpleSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        throw new SassValueException("Attribute selector can't have a suffix.");
    }

    /// Returns whether two nullable identifiers have the same decoded value.
    ///
    /// @param first  the first identifier, or {@code null}
    /// @param second the second identifier, or {@code null}
    /// @return whether both values are absent or semantically equal
    private static boolean identifiersHaveSameValue(
            @Nullable CssIdentifier first,
            @Nullable CssIdentifier second
    ) {
        return first == null ? second == null : second != null && first.hasSameValue(second);
    }
}