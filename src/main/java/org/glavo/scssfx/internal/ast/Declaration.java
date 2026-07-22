// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Represents a CSS property declaration or a Sass nested-property declaration.
///
/// A {@code null} child list identifies a leaf declaration and requires a
/// value. A non-null child list identifies an explicit nested-property block;
/// its value may be absent. Values that were not parsed as SassScript are
/// represented by an unquoted [StringExpression].
///
/// @param name               the unevaluated property name
/// @param value              the property value, or {@code null} for a valueless nested property
/// @param children           the nested property statements, or {@code null} for a leaf declaration
/// @param parsedAsSassScript whether the value was parsed using SassScript grammar
/// @param span               the source range occupied by the declaration, excluding a leaf semicolon
@ApiStatus.Internal
@NotNullByDefault
public record Declaration(
        Interpolation name,
        @Nullable SassExpression value,
        @Nullable @Unmodifiable List<SassStatement> children,
        boolean parsedAsSassScript,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable declaration after validating its structural form.
    ///
    /// @throws IllegalArgumentException if a leaf declaration has no value or
    /// a non-SassScript declaration has children, no value, or a quoted value
    public Declaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
        if (children != null) {
            children = List.copyOf(children);
        } else if (value == null) {
            throw new IllegalArgumentException("a leaf declaration must have a value");
        }
        if (!parsedAsSassScript
                && (children != null
                || !(value instanceof StringExpression string)
                || string.hasQuotes())) {
            throw new IllegalArgumentException(
                    "a non-SassScript declaration must have one unquoted string value"
            );
        }
    }

    /// Creates a leaf declaration whose value is parsed as SassScript.
    ///
    /// @param name  the unevaluated property name
    /// @param value the SassScript value
    /// @param span  the source range excluding the terminating semicolon
    /// @return the leaf declaration
    public static Declaration sassScript(
            Interpolation name,
            SassExpression value,
            SourceSpan span
    ) {
        return new Declaration(name, value, null, true, span);
    }

    /// Creates a leaf declaration whose value retains raw CSS token semantics.
    ///
    /// @param name  the unevaluated property name
    /// @param value the unquoted raw value
    /// @param span  the source range excluding the terminating semicolon
    /// @return the raw declaration
    public static Declaration raw(
            Interpolation name,
            StringExpression value,
            SourceSpan span
    ) {
        return new Declaration(name, value, null, false, span);
    }

    /// Creates a declaration with an explicit nested-property block.
    ///
    /// @param name     the unevaluated property name
    /// @param value    the optional SassScript value preceding the block
    /// @param children the nested property statements
    /// @param span     the source range through the closing brace
    /// @return the nested declaration
    public static Declaration nested(
            Interpolation name,
            @Nullable SassExpression value,
            List<SassStatement> children,
            SourceSpan span
    ) {
        return new Declaration(name, value, children, true, span);
    }

    /// Returns whether this declaration has an explicit nested-property block.
    ///
    /// @return whether [#children()] is non-null
    public boolean hasChildren() {
        return children != null;
    }

    /// Returns a Sass source representation of this declaration.
    ///
    /// @return the property name, value, and optional nested block
    @Override
    public String toString() {
        var result = new StringBuilder().append(name).append(':');
        if (value != null) {
            if (parsedAsSassScript) {
                result.append(' ');
            }
            result.append(value);
        }
        if (children == null) {
            return result.append(';').toString();
        }
        result.append(" {");
        for (var index = 0; index < children.size(); index++) {
            if (index > 0) {
                result.append(' ');
            }
            result.append(children.get(index));
        }
        return result.append('}').toString();
    }
}
