// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Represents the namespace prefix of a type, universal, or attribute selector.
///
/// @param kind the namespace form
/// @param name the decoded named namespace, or {@code null} unless {@code kind}
///             is [SelectorNamespaceKind#NAMED]
@ApiStatus.Internal
@NotNullByDefault
public record SelectorNamespace(
        SelectorNamespaceKind kind,
        @Nullable CssIdentifier name
) {
    /// Contains the default unqualified namespace form.
    private static final SelectorNamespace DEFAULT =
            new SelectorNamespace(SelectorNamespaceKind.DEFAULT, null);

    /// Contains the explicit no-namespace form.
    private static final SelectorNamespace NONE =
            new SelectorNamespace(SelectorNamespaceKind.NONE, null);

    /// Contains the wildcard-namespace form.
    private static final SelectorNamespace ANY =
            new SelectorNamespace(SelectorNamespaceKind.ANY, null);

    /// Creates one selector namespace.
    ///
    /// @throws IllegalArgumentException if named and unnamed state are mixed
    public SelectorNamespace {
        Objects.requireNonNull(kind, "kind");
        if (kind == SelectorNamespaceKind.NAMED && name == null) {
            throw new IllegalArgumentException("a named namespace requires a name");
        }
        if (kind != SelectorNamespaceKind.NAMED && name != null) {
            throw new IllegalArgumentException("only a named namespace may have a name");
        }
    }

    /// Returns the default unqualified namespace form.
    ///
    /// @return the shared default namespace
    public static SelectorNamespace defaultNamespace() {
        return DEFAULT;
    }

    /// Returns the explicit no-namespace form.
    ///
    /// @return the shared no-namespace value
    public static SelectorNamespace noNamespace() {
        return NONE;
    }

    /// Returns the wildcard-namespace form.
    ///
    /// @return the shared wildcard namespace
    public static SelectorNamespace anyNamespace() {
        return ANY;
    }

    /// Returns one named namespace form.
    ///
    /// @param name the decoded namespace identifier
    /// @return a namespace using {@code name}
    public static SelectorNamespace named(CssIdentifier name) {
        return new SelectorNamespace(
                SelectorNamespaceKind.NAMED,
                Objects.requireNonNull(name, "name")
        );
    }

    /// Returns whether this namespace is unqualified.
    ///
    /// @return whether CSS serialization omits a namespace delimiter
    public boolean isDefault() {
        return kind == SelectorNamespaceKind.DEFAULT;
    }

    /// Returns the CSS prefix including its delimiter when present.
    ///
    /// @return the namespace CSS prefix
    public String toCssPrefix() {
        return switch (kind) {
            case DEFAULT -> "";
            case NONE -> "|";
            case ANY -> "*|";
            case NAMED -> Objects.requireNonNull(name, "named namespace").toCssString() + "|";
        };
    }
}
