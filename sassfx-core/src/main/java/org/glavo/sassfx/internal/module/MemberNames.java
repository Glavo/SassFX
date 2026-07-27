// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// Helpers for Sass public/private member naming.
@ApiStatus.Internal
@NotNullByDefault
public final class MemberNames {
    /// Prevents instantiation.
    private MemberNames() {
    }

    /// Returns whether a member name is public and exportable.
    ///
    /// Names beginning with `-` or `_` are private.
    ///
    /// @param name the normalized member name
    /// @return whether the member is public
    public static boolean isPublic(String name) {
        Objects.requireNonNull(name, "name");
        return !name.isEmpty() && name.charAt(0) != '-' && name.charAt(0) != '_';
    }
}
