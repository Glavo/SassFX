// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents a statement in an unevaluated Sass syntax tree.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SassStatement extends SassNode
        permits Stylesheet, StyleRule, SilentComment, LoudComment {
}
