// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents a SassScript expression in an unevaluated syntax tree.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SassExpression extends SassNode permits
        StringExpression,
        NumberExpression,
        BooleanExpression,
        NullExpression,
        VariableExpression,
        ParenthesizedExpression,
        UnaryOperationExpression,
        BinaryOperationExpression,
        ListExpression,
        FunctionExpression,
        InterpolatedFunctionExpression,
        LegacyIfExpression,
        IfExpression,
        MapExpression,
        ColorExpression {
    /// Dispatches this expression to its type-specific visitor method.
    ///
    /// @param visitor the visitor that receives this expression
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    <R> R accept(SassExpressionVisitor<R> visitor);
}
