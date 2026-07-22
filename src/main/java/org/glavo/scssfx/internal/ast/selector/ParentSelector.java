// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// The parent selector `&`, optionally followed by a suffix.
///
/// @param suffix the identifier suffix after `&`, or {@code null}
/// @param span   the source span
@ApiStatus.Internal
@NotNullByDefault
public record ParentSelector(@Nullable String suffix, SourceSpan span) implements SimpleSelector {
    /// Creates a parent selector.
    public ParentSelector {
        if (suffix != null && suffix.isEmpty()) {
            throw new IllegalArgumentException("suffix must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    @Override
    public String toCssString() {
        return suffix == null ? "&" : "&" + suffix;
    }

    @Override
    public SimpleSelector addSuffix(String extra) {
        throw new SassValueException("Parent selector can't have an additional suffix.");
    }
}
