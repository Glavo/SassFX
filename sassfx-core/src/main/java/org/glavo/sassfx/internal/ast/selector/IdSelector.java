// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// An ID selector such as {@code #foo}.
///
/// @param name the ID identifier without the leading hash
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record IdSelector(CssIdentifier name, SourceSpan span) implements SimpleSelector {
    /// Creates an ID selector.
    public IdSelector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }

    /// Creates an ID selector from a decoded identifier.
    ///
    /// @param name the decoded ID name
    /// @param span the source span
    public IdSelector(String name, SourceSpan span) {
        this(CssIdentifier.of(name), span);
    }

    @Override
    public String toCssString() {
        return "#" + name.toCssString();
    }

    @Override
    public IdSelector addSuffix(CssIdentifier suffix) {
        return new IdSelector(
                name.append(Objects.requireNonNull(suffix, "suffix")),
                span
        );
    }
}
