// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Declares a reusable mixin.
///
/// @param originalName the decoded mixin name with underscores retained
/// @param parameters   the accepted parameters
/// @param children     the mixin body statements
/// @param span         the complete rule span
@ApiStatus.Internal
@NotNullByDefault
public record MixinRule(
        String originalName,
        ParameterList parameters,
        @Unmodifiable List<SassStatement> children,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable mixin declaration.
    ///
    /// @throws IllegalArgumentException if {@code originalName} is empty
    public MixinRule {
        Objects.requireNonNull(originalName, "originalName");
        if (originalName.isEmpty()) {
            throw new IllegalArgumentException("originalName must not be empty");
        }
        Objects.requireNonNull(parameters, "parameters");
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the mixin-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitMixinRule(this);
    }

    /// Returns the normalized mixin name used for lookup.
    ///
    /// @return the name with underscores normalized to hyphens
    public String name() {
        return originalName.replace('_', '-');
    }

    /// Returns whether the body contains a `@content` rule.
    ///
    /// @return whether content blocks are accepted
    public boolean hasContent() {
        return containsContent(children);
    }

    /// Recursively searches for a content rule.
    ///
    /// @param statements the statements to inspect
    /// @return whether a content rule is present
    private static boolean containsContent(List<SassStatement> statements) {
        for (var statement : statements) {
            if (statement instanceof ContentRule) {
                return true;
            }
            if (statement instanceof IfRule ifRule) {
                for (var clause : ifRule.clauses()) {
                    if (containsContent(clause.children())) {
                        return true;
                    }
                }
                if (ifRule.lastClause() != null
                        && containsContent(ifRule.lastClause().children())) {
                    return true;
                }
            } else if (statement instanceof EachRule eachRule) {
                if (containsContent(eachRule.children())) {
                    return true;
                }
            } else if (statement instanceof ForRule forRule) {
                if (containsContent(forRule.children())) {
                    return true;
                }
            } else if (statement instanceof WhileRule whileRule) {
                if (containsContent(whileRule.children())) {
                    return true;
                }
            } else if (statement instanceof StyleRule styleRule) {
                if (containsContent(styleRule.children())) {
                    return true;
                }
            } else if (statement instanceof AtRootRule atRootRule) {
                if (containsContent(atRootRule.children())) {
                    return true;
                }
            } else if (statement instanceof MediaRule mediaRule) {
                if (containsContent(mediaRule.children())) {
                    return true;
                }
            } else if (statement instanceof SupportsRule supportsRule) {
                if (containsContent(supportsRule.children())) {
                    return true;
                }
            } else if (statement instanceof Declaration declaration
                    && declaration.children() != null) {
                if (containsContent(declaration.children())) {
                    return true;
                }
            } else if (statement instanceof IncludeRule includeRule
                    && includeRule.content() != null) {
                // dart-sass StatementSearchVisitor walks include content blocks,
                // so `@mixin b { @include meta.apply(...) { @content } }` accepts
                // content even though `@content` is not a direct child of the mixin.
                if (containsContent(includeRule.content().children())) {
                    return true;
                }
            } else if (statement instanceof UnknownAtRule unknown
                    && !unknown.children().isEmpty()) {
                if (containsContent(unknown.children())) {
                    return true;
                }
            }
        }
        return false;
    }
}
