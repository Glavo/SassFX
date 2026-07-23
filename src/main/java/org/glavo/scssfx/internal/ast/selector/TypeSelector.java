// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A type selector such as {@code div} or {@code svg|path}.
///
/// @param name the qualified element name
/// @param span the source span
@ApiStatus.Internal
@NotNullByDefault
public record TypeSelector(QualifiedName name, SourceSpan span) implements SimpleSelector {
    /// Creates a type selector.
    public TypeSelector {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(span, "span");
    }

    /// Creates an unqualified type selector.
    ///
    /// @param name the decoded element name
    /// @param span the source span
    public TypeSelector(String name, SourceSpan span) {
        this(QualifiedName.unqualified(CssIdentifier.of(name)), span);
    }

    @Override
    public String toCssString() {
        return name.toCssString();
    }

    @Override
    public TypeSelector addSuffix(CssIdentifier suffix) {
        Objects.requireNonNull(suffix, "suffix");
        if (!name.isUnqualified()) {
            throw new SassValueException("Namespaced type selector can't have a suffix.");
        }
        return new TypeSelector(
                QualifiedName.unqualified(name.name().append(suffix)),
                span
        );
    }
}
