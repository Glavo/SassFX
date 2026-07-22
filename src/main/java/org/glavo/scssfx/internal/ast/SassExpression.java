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
        MapExpression,
        ColorExpression {
}
