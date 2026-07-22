// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.internal.css.CssNode;
import org.glavo.scssfx.internal.css.CssStylesheet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.IdentityHashMap;
import java.util.Objects;

/// Combines module CSS trees in dependency order.
@ApiStatus.Internal
@NotNullByDefault
public final class ModuleCss {
    /// Prevents instantiation.
    private ModuleCss() {
    }

    /// Builds one stylesheet containing each module's CSS exactly once.
    ///
    /// Upstream modules are emitted before their dependents.
    ///
    /// @param root the entry module
    /// @return the combined CSS stylesheet
    public static CssStylesheet combine(LoadedModule root) {
        Objects.requireNonNull(root, "root");
        var result = new CssStylesheet(root.css().span());
        var seen = new IdentityHashMap<LoadedModule, Boolean>();
        append(root, result, seen);
        return result;
    }

    /// Depth-first emits upstream CSS then this module's children.
    private static void append(
            LoadedModule module,
            CssStylesheet result,
            IdentityHashMap<LoadedModule, Boolean> seen
    ) {
        if (seen.put(module, Boolean.TRUE) != null) {
            return;
        }
        for (var upstream : module.upstream()) {
            append(upstream, result, seen);
        }
        for (CssNode child : module.css().children()) {
            result.addChild(child);
        }
    }
}
