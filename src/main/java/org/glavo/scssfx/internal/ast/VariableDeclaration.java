// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Defines or assigns a Sass variable.
///
/// The normalized [#name()] is used for Sass lookup, while [#nameSpan()]
/// retains the exact source spelling including the leading dollar sign. A
/// declaration in another module may not use `!global`.
///
/// @param namespace the module namespace, or {@code null} for an unqualified declaration
/// @param name the normalized variable name without its dollar sign
/// @param expression the value assigned to the variable
/// @param isGuarded whether the assignment has the `!default` flag
/// @param isGlobal whether the assignment has the `!global` flag
/// @param comment the silent comment immediately preceding the declaration, or {@code null}
/// @param nameSpan the source range occupied by the variable name, including its dollar sign
/// @param namespaceSpan the source range occupied by the namespace, or {@code null}
/// @param span the source range occupied by the declaration, excluding a terminating semicolon
@ApiStatus.Internal
@NotNullByDefault
public record VariableDeclaration(
        @Nullable String namespace,
        String name,
        SassExpression expression,
        boolean isGuarded,
        boolean isGlobal,
        @Nullable SilentComment comment,
        SourceSpan nameSpan,
        @Nullable SourceSpan namespaceSpan,
        SourceSpan span
) implements SassStatement {
    /// Creates an immutable variable declaration after validating its source ranges.
    ///
    /// @throws IllegalArgumentException if a name or namespace is empty, a
    /// namespaced declaration is global, the namespace span does not match the
    /// namespace's presence, or a child span is inconsistent with the complete span
    public VariableDeclaration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(nameSpan, "nameSpan");
        Objects.requireNonNull(span, "span");

        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (namespace != null && namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        if (namespace != null && isGlobal) {
            throw new IllegalArgumentException(
                    "a variable in another module must not be assigned with !global"
            );
        }
        if ((namespace == null) != (namespaceSpan == null)) {
            throw new IllegalArgumentException(
                    "namespaceSpan must be present exactly when namespace is present"
            );
        }
        if (!contains(span, nameSpan)) {
            throw new IllegalArgumentException("nameSpan must be contained by span");
        }
        if (!nameSpan.text().startsWith("$") || nameSpan.text().length() == 1) {
            throw new IllegalArgumentException(
                    "nameSpan must include a variable name after the leading dollar sign"
            );
        }
        if (!contains(span, expression.span())) {
            throw new IllegalArgumentException("expression span must be contained by span");
        }
        if (nameSpan.end().offset() > expression.span().start().offset()) {
            throw new IllegalArgumentException("nameSpan must not overlap the expression span");
        }
        var colonOffset = declarationColonOffset(span, nameSpan);
        if (colonOffset < 0
                || span.start().offset() + colonOffset >= expression.span().start().offset()) {
            throw new IllegalArgumentException(
                    "span must contain a colon between nameSpan and the expression span"
            );
        }

        if (namespaceSpan == null) {
            if (span.start().offset() != nameSpan.start().offset()) {
                throw new IllegalArgumentException(
                        "an unqualified declaration must begin with nameSpan"
                );
            }
        } else {
            if (!contains(span, namespaceSpan)) {
                throw new IllegalArgumentException("namespaceSpan must be contained by span");
            }
            if (namespaceSpan.text().isEmpty()) {
                throw new IllegalArgumentException("namespaceSpan must not be empty");
            }
            if (span.start().offset() != namespaceSpan.start().offset()) {
                throw new IllegalArgumentException(
                        "a qualified declaration must begin with namespaceSpan"
                );
            }
            var dotOffset = namespaceSpan.end().offset();
            if (dotOffset + 1 != nameSpan.start().offset()
                    || span.text().charAt(dotOffset - span.start().offset()) != '.') {
                throw new IllegalArgumentException(
                        "namespaceSpan and nameSpan must be separated by one dot"
                );
            }
        }
    }

    /// Returns the variable name exactly as written before the assignment colon.
    ///
    /// A qualified declaration includes its namespace. Trailing ASCII
    /// whitespace between the name and colon is omitted.
    ///
    /// @return the source spelling including the leading dollar sign and optional namespace
    public String originalName() {
        var end = declarationColonOffset(span, nameSpan);
        while (end > 0 && isAsciiWhitespace(span.text().charAt(end - 1))) {
            end--;
        }
        return span.text().substring(0, end);
    }

    /// Returns a normalized Sass source representation of this declaration.
    ///
    /// Parse-time flags and the preceding comment are intentionally omitted,
    /// matching the upstream Sass AST representation.
    ///
    /// @return the optional namespace, normalized variable name, value, and semicolon
    @Override
    public String toString() {
        return (namespace == null ? "" : namespace + ".")
                + "$" + name + ": " + expression + ";";
    }

    /// Returns whether an inner span is a text-consistent subrange of an outer span.
    ///
    /// @param outer the prospective containing span
    /// @param inner the prospective contained span
    /// @return whether URLs, offsets, and captured source text are consistent
    private static boolean contains(SourceSpan outer, SourceSpan inner) {
        if (!Objects.equals(outer.url(), inner.url())
                || inner.start().offset() < outer.start().offset()
                || inner.end().offset() > outer.end().offset()) {
            return false;
        }

        var relativeStart = inner.start().offset() - outer.start().offset();
        return outer.text().regionMatches(relativeStart, inner.text(), 0, inner.text().length());
    }

    /// Locates the assignment colon following a declaration's variable-name range.
    ///
    /// @param declarationSpan the complete declaration range
    /// @param variableNameSpan the variable-name range including the dollar sign
    /// @return the colon offset relative to {@code declarationSpan}, or {@code -1} if absent
    private static int declarationColonOffset(
            SourceSpan declarationSpan,
            SourceSpan variableNameSpan
    ) {
        var relativeNameEnd = variableNameSpan.end().offset()
                - declarationSpan.start().offset();
        return declarationSpan.text().indexOf(':', relativeNameEnd);
    }

    /// Returns whether a character is Sass ASCII whitespace.
    ///
    /// @param character the character to inspect
    /// @return whether the character is space, tab, line feed, form feed, or carriage return
    private static boolean isAsciiWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\n'
                || character == '\f'
                || character == '\r';
    }
}
