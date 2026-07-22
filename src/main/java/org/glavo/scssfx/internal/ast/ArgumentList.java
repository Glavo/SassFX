// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.glavo.scssfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Contains the arguments supplied to a callable invocation.
///
/// Named argument keys exclude their dollar signs and use normalized Sass
/// names. Their associated spans cover both the name and value.
///
/// @param positional the positional arguments in source order
/// @param named the named arguments in source order
/// @param namedSpans the source spans for the named arguments in the same order
/// @param rest the first expression followed by an ellipsis, or {@code null}
/// @param keywordRest the second expression followed by an ellipsis, or {@code null}
/// @param span the source range including the surrounding parentheses
@ApiStatus.Internal
@NotNullByDefault
public record ArgumentList(
        @Unmodifiable List<SassExpression> positional,
        @Unmodifiable Map<String, SassExpression> named,
        @Unmodifiable Map<String, SourceSpan> namedSpans,
        @Nullable SassExpression rest,
        @Nullable SassExpression keywordRest,
        SourceSpan span
) implements SassNode {
    /// Creates an immutable argument list.
    ///
    /// @throws IllegalArgumentException if the named argument maps have
    /// different keys or iteration orders, or a keyword rest argument is
    /// present without a first rest argument
    public ArgumentList {
        positional = List.copyOf(positional);
        Objects.requireNonNull(named, "named");
        Objects.requireNonNull(namedSpans, "namedSpans");
        Objects.requireNonNull(span, "span");

        var namedCopy = new LinkedHashMap<String, SassExpression>(named.size());
        for (var entry : named.entrySet()) {
            namedCopy.put(
                    Objects.requireNonNull(entry.getKey(), "named key"),
                    Objects.requireNonNull(entry.getValue(), "named value")
            );
        }
        var spanCopy = new LinkedHashMap<String, SourceSpan>(namedSpans.size());
        for (var entry : namedSpans.entrySet()) {
            spanCopy.put(
                    Objects.requireNonNull(entry.getKey(), "named span key"),
                    Objects.requireNonNull(entry.getValue(), "named span value")
            );
        }
        if (!List.copyOf(namedCopy.keySet()).equals(List.copyOf(spanCopy.keySet()))) {
            throw new IllegalArgumentException(
                    "named and namedSpans must have the same keys in the same order"
            );
        }
        if (keywordRest != null && rest == null) {
            throw new IllegalArgumentException(
                    "keywordRest requires a preceding rest argument"
            );
        }
        named = Collections.unmodifiableMap(namedCopy);
        namedSpans = Collections.unmodifiableMap(spanCopy);
    }

    /// Creates an invocation that passes no arguments.
    ///
    /// @param span the source range including the empty parentheses
    /// @return the empty argument list
    public static ArgumentList empty(SourceSpan span) {
        return new ArgumentList(List.of(), Map.of(), Map.of(), null, null, span);
    }

    /// Returns whether this invocation passes no arguments.
    ///
    /// @return whether no positional, named, or rest argument is present
    public boolean isEmpty() {
        return positional.isEmpty() && named.isEmpty() && rest == null;
    }

    /// Returns a normalized Sass source representation of these arguments.
    ///
    /// @return the arguments surrounded by parentheses
    @Override
    public String toString() {
        var components = new ArrayList<String>(
                positional.size() + named.size() + (rest == null ? 0 : 1)
                        + (keywordRest == null ? 0 : 1)
        );
        for (var argument : positional) {
            components.add(parenthesizeArgument(argument));
        }
        for (var entry : named.entrySet()) {
            components.add("$" + entry.getKey() + ": "
                    + parenthesizeArgument(entry.getValue()));
        }
        if (rest != null) {
            components.add(parenthesizeArgument(rest) + "...");
        }
        if (keywordRest != null) {
            components.add(parenthesizeArgument(keywordRest) + "...");
        }
        return "(" + String.join(", ", components) + ")";
    }

    /// Adds grouping parentheses when a comma list would otherwise be parsed
    /// as multiple arguments.
    ///
    /// @param argument the argument to represent
    /// @return the unambiguous argument source
    private static String parenthesizeArgument(SassExpression argument) {
        return argument instanceof ListExpression list
                && !list.hasBrackets()
                && list.separator() == ListSeparator.COMMA
                && list.contents().size() > 1
                ? "(" + argument + ")"
                : argument.toString();
    }
}
