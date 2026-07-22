// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// The universal selector `*`.
///
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record UniversalSelector(SourceSpan span) implements SimpleSelector {
    /// Creates a universal selector.
    public UniversalSelector {
        Objects.requireNonNull(span, "span");
    }

    @Override
    public String toCssString() {
        return "*";
    }

    @Override
    public SimpleSelector addSuffix(String suffix) {
        throw new SassValueException("Universal selector can't have a suffix.");
    }
}
