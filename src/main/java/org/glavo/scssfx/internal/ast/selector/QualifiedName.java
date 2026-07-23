// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Represents an element or attribute name with explicit namespace semantics.
///
/// @param name      the local identifier
/// @param namespace the namespace form for the local identifier
@ApiStatus.Internal
@NotNullByDefault
public record QualifiedName(CssIdentifier name, SelectorNamespace namespace) {
    /// Creates one qualified CSS name.
    public QualifiedName {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(namespace, "namespace");
        }

    /// Creates one name using ordinary unqualified namespace behavior.
    ///
    /// @param name the local identifier
    /// @return an unqualified CSS name
    public static QualifiedName unqualified(CssIdentifier name) {
        return new QualifiedName(
                Objects.requireNonNull(name, "name"),
                SelectorNamespace.defaultNamespace()
        );
    }

    /// Returns whether this name has no explicit namespace prefix.
    ///
    /// @return whether serialization omits a namespace delimiter
    public boolean isUnqualified() {
        return namespace.isDefault();
    }

    /// Returns whether this name has the same namespace and decoded local name.
    ///
    /// @param other the name to compare
    /// @return whether both names have equivalent selector semantics
    public boolean hasSameValue(QualifiedName other) {
        Objects.requireNonNull(other, "other");
        return namespace.equals(other.namespace) && name.hasSameValue(other.name);
    }

    /// Returns the canonical CSS spelling of this qualified name.
    ///
    /// @return the local name with an optional namespace prefix
    public String toCssString() {
        return namespace.toCssPrefix() + name.toCssString();
    }
}
