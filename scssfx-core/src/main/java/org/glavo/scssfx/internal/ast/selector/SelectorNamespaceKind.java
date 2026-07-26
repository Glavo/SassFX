// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast.selector;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Identifies the namespace form used by a qualified CSS selector name.
@ApiStatus.Internal
@NotNullByDefault
public enum SelectorNamespaceKind {
    /// Uses the ordinary unqualified CSS namespace behavior.
    DEFAULT,

    /// Requires that the selected name have no namespace.
    NONE,

    /// Matches the selected name in every namespace.
    ANY,

    /// Matches the selected name in one named namespace.
    NAMED
}
