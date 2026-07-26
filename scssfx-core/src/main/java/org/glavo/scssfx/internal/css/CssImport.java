// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A plain CSS `@import` rule retained for textual output backends.
@ApiStatus.Internal
@NotNullByDefault
public final class CssImport extends AbstractCssNode implements CssNode {
    /// Contains the complete import argument excluding `@import` and the terminator.
    private final String argument;

    /// Creates a CSS import.
    ///
    /// @param argument the nonempty CSS import argument
    /// @param span     the source range associated with the import
    /// @throws IllegalArgumentException if {@code argument} is empty
    public CssImport(String argument, SourceSpan span) {
        super(span);
        this.argument = Objects.requireNonNull(argument, "argument");
        if (argument.isEmpty()) {
            throw new IllegalArgumentException("argument must not be empty");
        }
    }

    /// Returns the CSS import argument.
    ///
    /// @return the text following `@import`
    public String argument() {
        return argument;
    }

    /// Returns false because every import produces output.
    ///
    /// @return {@code false}
    @Override
    public boolean isInvisible() {
        return false;
    }
}
