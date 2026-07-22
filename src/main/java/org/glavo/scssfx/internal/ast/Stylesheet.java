// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Contains the top-level statements of an unevaluated Sass stylesheet.
///
/// @param children the top-level statements in source order
/// @param span the source range covering the complete input
/// @param plainCss whether the input was parsed using plain CSS restrictions
/// @param parseTimeWarnings diagnostics discovered while parsing, in occurrence order
/// @param globalVariables normalized global variable names mapped to their first declaration spans
@ApiStatus.Internal
@NotNullByDefault
public record Stylesheet(
        @Unmodifiable List<SassStatement> children,
        SourceSpan span,
        boolean plainCss,
        @Unmodifiable List<Diagnostic> parseTimeWarnings,
        @Unmodifiable Map<String, SourceSpan> globalVariables
) implements SassStatement {
    /// Creates an immutable stylesheet root.
    public Stylesheet {
        children = List.copyOf(children);
        Objects.requireNonNull(span, "span");
        parseTimeWarnings = List.copyOf(parseTimeWarnings);

        Objects.requireNonNull(globalVariables, "globalVariables");
        var globalsCopy = new LinkedHashMap<String, SourceSpan>(globalVariables.size());
        for (var entry : globalVariables.entrySet()) {
            globalsCopy.put(
                    Objects.requireNonNull(entry.getKey(), "global variable name"),
                    Objects.requireNonNull(entry.getValue(), "global variable span")
            );
        }
        globalVariables = Collections.unmodifiableMap(globalsCopy);
    }

    /// Creates a stylesheet without parse-time warnings or global-variable metadata.
    ///
    /// @param children the top-level statements in source order
    /// @param span the source range covering the complete input
    /// @param plainCss whether the input was parsed using plain CSS restrictions
    public Stylesheet(
            List<SassStatement> children,
            SourceSpan span,
            boolean plainCss
    ) {
        this(children, span, plainCss, List.of(), Map.of());
    }

    /// Dispatches this statement to the stylesheet visitor method.
    ///
    /// @param visitor the visitor that receives this statement
    /// @param <R> the result type produced by the visitor
    /// @return the result returned by the visitor
    @Override
    public <R> R accept(SassStatementVisitor<R> visitor) {
        return visitor.visitStylesheet(this);
    }

    /// Returns the source representation of the top-level statements.
    ///
    /// @return statements separated by one space
    @Override
    public String toString() {
        return String.join(" ", children.stream().map(Object::toString).toList());
    }
}
