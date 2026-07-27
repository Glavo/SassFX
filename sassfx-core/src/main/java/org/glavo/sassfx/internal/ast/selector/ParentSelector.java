// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// The parent selector {@code &}, optionally followed by an identifier suffix.
///
/// @param suffix the identifier suffix after {@code &}, or {@code null}
/// @param span   the source span
@ApiStatus.Internal
@NotNullByDefault
public record ParentSelector(
        @Nullable CssIdentifier suffix,
        SourceSpan span
) implements SimpleSelector {
    /// Creates a parent selector.
    public ParentSelector {
        Objects.requireNonNull(span, "span");
    }

    @Override
    public String toCssString() {
        return suffix == null ? "&" : "&" + suffix.toCssString();
    }

    @Override
    public SimpleSelector addSuffix(CssIdentifier extra) {
        Objects.requireNonNull(extra, "extra");
        throw new SassValueException("Parent selector can't have an additional suffix.");
    }
}
