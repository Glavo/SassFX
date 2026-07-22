// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A class selector such as `.foo`.
///
/// @param name the class name without the leading dot
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record ClassSelector(String name, SourceSpan span) implements SimpleSelector {
    /// Creates a class selector.
    public ClassSelector {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    @Override
    public String toCssString() {
        return "." + name;
    }

    @Override
    public ClassSelector addSuffix(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        return new ClassSelector(name + suffix, span);
    }
}
