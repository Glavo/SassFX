// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A class selector such as {@code .foo}.
///
/// @param name the class identifier without the leading dot
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record ClassSelector(CssIdentifier name, SourceSpan span) implements SimpleSelector {
    /// Creates a class selector.
    public ClassSelector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }

    /// Creates a class selector from a decoded identifier.
    ///
    /// @param name the decoded class name
    /// @param span the source span
    public ClassSelector(String name, SourceSpan span) {
        this(CssIdentifier.of(name), span);
    }

    @Override
    public String toCssString() {
        return "." + name.toCssString();
    }

    @Override
    public ClassSelector addSuffix(CssIdentifier suffix) {
        return new ClassSelector(
                name.append(Objects.requireNonNull(suffix, "suffix")),
                span
        );
    }
}
