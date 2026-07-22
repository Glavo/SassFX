// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Visits each concrete Sass statement type.
///
/// @param <R> the result returned for each visited statement
@ApiStatus.Internal
@NotNullByDefault
public interface SassStatementVisitor<R> {
    /// Visits a stylesheet root.
    ///
    /// @param statement the statement to visit
    /// @return the visit result
    R visitStylesheet(Stylesheet statement);

    /// Visits a style rule.
    ///
    /// @param statement the statement to visit
    /// @return the visit result
    R visitStyleRule(StyleRule statement);

    /// Visits a property declaration.
    ///
    /// @param statement the statement to visit
    /// @return the visit result
    R visitDeclaration(Declaration statement);

    /// Visits a variable declaration.
    ///
    /// @param statement the statement to visit
    /// @return the visit result
    R visitVariableDeclaration(VariableDeclaration statement);

    /// Visits a silent comment.
    ///
    /// @param statement the statement to visit
    /// @return the visit result
    R visitSilentComment(SilentComment statement);

    /// Visits a loud comment.
    ///
    /// @param statement the statement to visit
    /// @return the visit result
    R visitLoudComment(LoudComment statement);
}
