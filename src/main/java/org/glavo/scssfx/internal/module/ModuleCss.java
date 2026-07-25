// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.internal.css.CssComment;
import org.glavo.scssfx.internal.css.CssImport;
import org.glavo.scssfx.internal.css.CssNode;
import org.glavo.scssfx.internal.css.CssStylesheet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
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
    /// Upstream modules are emitted before their dependents. Loud comments
    /// captured before each `@use` are inserted immediately before the used
    /// module's CSS.
    ///
    /// @param root the entry module
    /// @return the combined CSS stylesheet
    public static CssStylesheet combine(LoadedModule root) {
        return combine(root, new IdentityHashMap<>());
    }

    /// Builds CSS for {@code root}'s graph, skipping modules already present in
    /// {@code alreadyEmitted} and recording newly emitted modules into that map.
    ///
    /// Used when successive {@code @use} rules under one {@code @import} must
    /// share a single CSS copy of common upstream modules (diamond import).
    ///
    /// @param root           the entry module
    /// @param alreadyEmitted modules whose CSS has already been re-emitted
    /// @return the combined CSS stylesheet for newly emitted modules only
    public static CssStylesheet combine(
            LoadedModule root,
            IdentityHashMap<LoadedModule, Boolean> alreadyEmitted
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(alreadyEmitted, "alreadyEmitted");
        var result = new CssStylesheet(root.css().span());
        append(root, result, alreadyEmitted);
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
            if (upstream.transitivelyContainsCss()) {
                @Nullable List<CssComment> comments =
                        module.preModuleComments().get(upstream);
                if (comments != null) {
                    for (var comment : comments) {
                        result.addChild(comment);
                    }
                }
                append(upstream, result, seen);
            }
        }
        for (CssNode child : module.css().children()) {
            if (child instanceof CssImport importRule) {
                result.addImport(importRule);
            } else {
                result.addChild(child);
            }
        }
    }
}
