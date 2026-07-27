// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.evaluate;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.SassValue;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Stores the current value and source identity of one Sass variable export.
///
/// Module export maps and forwarded views share binding instances so assigning
/// through any public module boundary updates every alias of the same variable.
/// Built-in bindings are read-only.
@ApiStatus.Internal
@NotNullByDefault
public final class VariableBinding {
    /// Contains the currently assigned immutable Sass value.
    private SassValue value;

    /// Contains the source range that produced the current value.
    private SourceSpan originSpan;

    /// Records whether Sass source may assign through this binding.
    private final boolean writable;

    /// Creates a writable variable binding.
    ///
    /// @param value the initial immutable Sass value
    /// @param originSpan the source range that produced the initial value
    public VariableBinding(SassValue value, SourceSpan originSpan) {
        this(value, originSpan, true);
    }

    /// Creates a variable binding with an explicit mutability policy.
    ///
    /// @param value the initial immutable Sass value
    /// @param originSpan the source range that produced the initial value
    /// @param writable whether Sass source may assign through the binding
    private VariableBinding(SassValue value, SourceSpan originSpan, boolean writable) {
        this.value = Objects.requireNonNull(value, "value");
        this.originSpan = Objects.requireNonNull(originSpan, "originSpan");
        this.writable = writable;
    }

    /// Creates a read-only binding for a built-in module variable.
    ///
    /// @param value the built-in value
    /// @param originSpan the synthetic source range associated with the value
    /// @return the read-only binding
    public static VariableBinding readOnly(SassValue value, SourceSpan originSpan) {
        return new VariableBinding(value, originSpan, false);
    }

    /// Returns the current variable value.
    ///
    /// @return the current immutable Sass value
    public SassValue value() {
        return value;
    }

    /// Returns the source range that produced the current value.
    ///
    /// @return the current value's origin
    public SourceSpan originSpan() {
        return originSpan;
    }

    /// Replaces the current value and its source origin.
    ///
    /// @param value the newly assigned value
    /// @param originSpan the source range that produced the assigned value
    /// @throws SassValueException if this is a built-in binding
    /// @throws NullPointerException if an argument is {@code null}
    public void assign(SassValue value, SourceSpan originSpan) {
        var assignedValue = Objects.requireNonNull(value, "value");
        var assignedOrigin = Objects.requireNonNull(originSpan, "originSpan");
        if (!writable) {
            throw new SassValueException("Cannot modify built-in variable.");
        }
        this.value = assignedValue;
        this.originSpan = assignedOrigin;
    }
}
