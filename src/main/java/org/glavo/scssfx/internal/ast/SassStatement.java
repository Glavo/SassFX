// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents a statement in an unevaluated Sass syntax tree.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SassStatement extends SassNode
        permits Stylesheet, StyleRule, FontFaceRule, MediaRule, SupportsRule, Declaration, VariableDeclaration, SilentComment, LoudComment,
        IfRule, EachRule, ForRule, WhileRule,
        MixinRule, FunctionRule, IncludeRule, ContentRule, ReturnRule,
        DebugRule, WarnRule, ErrorRule, ImportRule, UseRule, ForwardRule, ExtendRule, AtRootRule,
        UnknownAtRule {
    /// Dispatches this statement to its type-specific visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    <R> R accept(SassStatementVisitor<R> visitor);
}
