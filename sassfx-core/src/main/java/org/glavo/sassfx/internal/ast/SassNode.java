// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents a node in an unevaluated Sass syntax tree.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SassNode permits
        SassStatement, SassExpression, Interpolation, ArgumentList, ParameterList,
        ContentBlock, SupportsCondition, SassImport {
    /// Returns the source range that produced this node.
    ///
    /// @return the complete source range of the node
    SourceSpan span();
}
