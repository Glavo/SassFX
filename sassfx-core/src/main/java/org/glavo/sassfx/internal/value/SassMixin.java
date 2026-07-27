// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.glavo.sassfx.internal.callable.Callable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Represents a first-class reference to a callable Sass mixin.
///
/// A reference is bound to the compilation that created it. It may be inspected,
/// compared, and used as a map key, but it cannot be represented as a CSS value.
/// The evaluator verifies the compilation binding immediately before inclusion.
@ApiStatus.Internal
@NotNullByDefault
public final class SassMixin implements SassValue {
    /// Contains the referenced callable implementation.
    private final Callable callable;

    /// Identifies the compilation permitted to include this reference.
    private final Object compilationContext;

    /// Creates a mixin reference for one compilation.
    ///
    /// @param callable           the callable implementation
    /// @param compilationContext the identity token of the creating compilation
    public SassMixin(Callable callable, Object compilationContext) {
        this.callable = Objects.requireNonNull(callable, "callable");
        this.compilationContext = Objects.requireNonNull(compilationContext, "compilationContext");
    }

    /// Returns the callable implementation referenced by this value.
    ///
    /// The caller must validate [#assertCompilationContext(Object)] before
    /// including the returned callable.
    ///
    /// @return the callable implementation
    public Callable callable() {
        return callable;
    }

    /// Verifies that this reference belongs to the supplied compilation.
    ///
    /// @param compilationContext the identity token of the including compilation
    /// @throws SassValueException if the token does not identify this reference's compilation
    public void assertCompilationContext(Object compilationContext) {
        if (this.compilationContext != Objects.requireNonNull(compilationContext, "compilationContext")) {
            throw new SassValueException(this + " does not belong to current compilation.");
        }
    }

    /// Returns this mixin reference.
    ///
    /// @return this mixin reference
    @Override
    public SassMixin assertMixin() {
        return this;
    }

    /// Rejects CSS serialization of this mixin reference.
    ///
    /// @return no value
    /// @throws SassValueException always
    @Override
    public String toCssString() {
        throw new SassValueException(this + " isn't a valid CSS value.");
    }

    /// Returns the inspect representation of this mixin reference.
    ///
    /// @return a {@code get-mixin()} representation with a quoted name
    @Override
    public String toString() {
        return "get-mixin(" + new SassString(callable.name(), true) + ")";
    }

    /// Compares mixin references by their callable implementations.
    ///
    /// Compilation ownership intentionally does not participate in equality so
    /// equivalent references retain Sass map-key semantics.
    ///
    /// @param other the object to compare
    /// @return whether both references wrap equal callable implementations
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || other instanceof SassMixin mixin && callable.equals(mixin.callable);
    }

    /// Returns the callable implementation's hash code.
    ///
    /// @return the hash code used for Sass map keys
    @Override
    public int hashCode() {
        return callable.hashCode();
    }
}