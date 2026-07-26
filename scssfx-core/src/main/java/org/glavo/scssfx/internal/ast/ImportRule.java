// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Declares one legacy Sass `@import` rule.
///
/// @param imports the import arguments in source order
/// @param span    the complete rule span
@ApiStatus.Internal
@NotNullByDefault
public record ImportRule(
        @Unmodifiable List<SassImport> imports,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable import rule.
    ///
    /// @throws IllegalArgumentException if {@code imports} is empty
    public ImportRule {
        imports = List.copyOf(imports);
        if (imports.isEmpty()) {
            throw new IllegalArgumentException("imports must not be empty");
        }
        Objects.requireNonNull(span, "span");
    }

    /// Dispatches this statement to the import-rule visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R>     the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitImportRule(this);
    }
}
