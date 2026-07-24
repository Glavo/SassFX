// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Visits each concrete SassScript expression type.
///
/// @param <R> the result returned for each visited expression
@ApiStatus.Internal
@NotNullByDefault
public interface SassExpressionVisitor<R> {
    /// Visits a string expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitStringExpression(StringExpression expression);

    /// Visits a number expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitNumberExpression(NumberExpression expression);

    /// Visits a boolean expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitBooleanExpression(BooleanExpression expression);

    /// Visits a null expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitNullExpression(NullExpression expression);

    /// Visits a variable reference.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitVariableExpression(VariableExpression expression);

    /// Visits a parenthesized expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitParenthesizedExpression(ParenthesizedExpression expression);

    /// Visits a unary operation expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitUnaryOperationExpression(UnaryOperationExpression expression);

    /// Visits a binary operation expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitBinaryOperationExpression(BinaryOperationExpression expression);

    /// Visits a list expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitListExpression(ListExpression expression);

    /// Visits a statically named function invocation.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitFunctionExpression(FunctionExpression expression);

    /// Visits an interpolated function invocation.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitInterpolatedFunctionExpression(InterpolatedFunctionExpression expression);

    /// Visits a short-circuiting legacy `if()` expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitLegacyIfExpression(LegacyIfExpression expression);

    /// Visits a modern CSS-style `if()` expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitIfExpression(IfExpression expression);

    /// Visits a map expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitMapExpression(MapExpression expression);

    /// Visits a color expression.
    ///
    /// @param expression the expression to visit
    /// @return the visit result
    R visitColorExpression(ColorExpression expression);
}
