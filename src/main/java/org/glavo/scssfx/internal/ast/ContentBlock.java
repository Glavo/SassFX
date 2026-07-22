// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Contains the content block passed to a mixin include.
///
/// @param parameters the content parameters; this round always uses an empty list
/// @param children   the content statements
/// @param span       the complete content-block span
@ApiStatus.Internal
@NotNullByDefault
public record ContentBlock(
        ParameterList parameters,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassNode {
    /// Creates an immutable content block.
    public ContentBlock {
        Objects.requireNonNull(parameters, "parameters");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }
}
