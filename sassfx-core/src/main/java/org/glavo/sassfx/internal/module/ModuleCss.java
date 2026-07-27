// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.module;

import org.glavo.sassfx.internal.css.CssComment;
import org.glavo.sassfx.internal.css.CssImport;
import org.glavo.sassfx.internal.css.CssNode;
import org.glavo.sassfx.internal.css.CssStylesheet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
        var imports = new ArrayList<CssNode>();
        var css = new ArrayList<CssNode>();
        append(root, imports, css, alreadyEmitted);
        for (var child : imports) {
            result.addChild(child);
        }
        for (var child : css) {
            result.addChild(child);
        }
        return result;
    }

    /// Collects upstream CSS and this module's children in depth-first order.
    ///
    /// Static imports and comments between them are collected separately from
    /// ordinary CSS so imports across the complete module graph precede every
    /// ordinary rule in the combined stylesheet.
    ///
    /// @param module  the module to collect
    /// @param imports static imports and their interleaved comments
    /// @param css     ordinary CSS nodes
    /// @param seen    modules already collected
    private static void append(
            LoadedModule module,
            ArrayList<CssNode> imports,
            ArrayList<CssNode> css,
            IdentityHashMap<LoadedModule, Boolean> seen
    ) {
        if (seen.put(module, Boolean.TRUE) != null) {
            return;
        }
        for (var upstream : module.upstream()) {
            if (!upstream.transitivelyContainsCss()) {
                continue;
            }
            @Nullable List<CssComment> comments =
                    module.preModuleComments().get(upstream);
            if (comments != null) {
                for (var comment : comments) {
                    (css.isEmpty() ? imports : css).add(comment);
                }
            }
            append(upstream, imports, css, seen);
        }
        var children = module.css().children();
        var index = indexAfterImports(children);
        imports.addAll(children.subList(0, index));
        css.addAll(children.subList(index, children.size()));
    }

    /// Returns the index immediately after the last static import in the
    /// stylesheet's initial import-and-comment prefix.
    ///
    /// Leading comments are included only when a later import occurs before the
    /// first ordinary CSS node. Comments following the last such import remain
    /// ordinary CSS.
    ///
    /// @param children the module stylesheet children
    /// @return the first child that belongs to ordinary CSS
    private static int indexAfterImports(List<CssNode> children) {
        var lastImport = -1;
        for (var index = 0; index < children.size(); index++) {
            var child = children.get(index);
            if (child instanceof CssImport) {
                lastImport = index;
            } else if (!(child instanceof CssComment)) {
                break;
            }
        }
        return lastImport + 1;
    }
}
