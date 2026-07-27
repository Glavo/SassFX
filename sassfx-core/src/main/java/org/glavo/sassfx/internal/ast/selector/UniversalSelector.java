// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast.selector;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A universal selector such as {@code *} or {@code svg|*}.
///
/// @param namespace the namespace form for the universal selector
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record UniversalSelector(
        SelectorNamespace namespace,
        SourceSpan span
) implements SimpleSelector {
    /// Creates a universal selector.
    public UniversalSelector {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(span, "span");
    }

    /// Creates an unqualified universal selector.
    ///
    /// @param span the source span
    public UniversalSelector(SourceSpan span) {
        this(SelectorNamespace.defaultNamespace(), span);
    }

    /// Returns whether this selector has no explicit namespace prefix.
    ///
    /// @return whether serialization omits a namespace delimiter
    public boolean isUnqualified() {
        return namespace.isDefault();
    }

    @Override
    public String toCssString() {
        return namespace.toCssPrefix() + "*";
    }

    @Override
    public SimpleSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        throw new SassValueException("Universal selector can't have a suffix.");
    }
}
