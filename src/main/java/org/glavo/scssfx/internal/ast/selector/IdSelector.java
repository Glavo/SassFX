// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// An ID selector such as `#foo`.
///
/// @param name the ID name without the leading hash
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record IdSelector(String name, SourceSpan span) implements SimpleSelector {
    /// Creates an ID selector.
    public IdSelector {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    @Override
    public String toCssString() {
        return "#" + name;
    }

    @Override
    public IdSelector addSuffix(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return new IdSelector(name + suffix, span);
    }
}
