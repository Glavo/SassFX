// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.internal.css.CssMediaRule;
import org.glavo.sassfx.internal.css.CssParentNode;
import org.glavo.sassfx.internal.css.CssStyleRule;
import org.glavo.sassfx.internal.css.CssSupportsRule;
import org.glavo.sassfx.internal.css.CssUnknownAtRule;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/// Describes which enclosing CSS parents an `@at-root` rule excludes or includes.
///
/// @param include whether named rules are included rather than excluded
/// @param names   lower-case rule names, including the special names {@code all} and {@code rule}
@ApiStatus.Internal
@NotNullByDefault
public record AtRootQuery(
        boolean include,
        @Unmodifiable Set<String> names
) {
    /// The default query excludes only style rules.
    public static final AtRootQuery DEFAULT = new AtRootQuery(false, Set.of("rule"));

    /// Creates an at-root query.
    public AtRootQuery {
        Objects.requireNonNull(names, "names");
        names = Set.copyOf(names);
    }

    /// Parses an evaluated at-root query body such as {@code with: media} or
    /// {@code without: rule media}.
    ///
    /// The surrounding parentheses are optional and ignored when present.
    ///
    /// @param text the interpolated query text
    /// @return the parsed query
    /// @throws SassValueException if the query grammar is invalid
    public static AtRootQuery parse(String text) {
        Objects.requireNonNull(text, "text");
        var trimmed = text.strip();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).strip();
        }
        var separator = trimmed.indexOf(':');
        if (separator < 0) {
            throw new SassValueException("Expected at-root query.");
        }
        var kind = trimmed.substring(0, separator).strip().toLowerCase(Locale.ROOT);
        var include = switch (kind) {
            case "with" -> true;
            case "without" -> false;
            default -> throw new SassValueException(
                    "Expected \"with\" or \"without\"."
            );
        };
        var rest = trimmed.substring(separator + 1).strip();
        if (rest.isEmpty()) {
            throw new SassValueException("Expected at-rule name.");
        }
        var names = new LinkedHashSet<String>();
        // Names may be bare identifiers or quoted strings
        // ({@code without: "media" supports}).
        var index = 0;
        while (index < rest.length()) {
            while (index < rest.length() && Character.isWhitespace(rest.charAt(index))) {
                index++;
            }
            if (index >= rest.length()) {
                break;
            }
            var character = rest.charAt(index);
            if (character == '"' || character == '\'') {
                var quote = character;
                index++;
                var start = index;
                while (index < rest.length() && rest.charAt(index) != quote) {
                    if (rest.charAt(index) == '\\' && index + 1 < rest.length()) {
                        index += 2;
                    } else {
                        index++;
                    }
                }
                if (index >= rest.length()) {
                    throw new SassValueException("Expected " + quote + ".");
                }
                names.add(rest.substring(start, index).toLowerCase(Locale.ROOT));
                index++; // closing quote
            } else {
                var start = index;
                while (index < rest.length() && !Character.isWhitespace(rest.charAt(index))) {
                    index++;
                }
                names.add(rest.substring(start, index).toLowerCase(Locale.ROOT));
            }
        }
        if (names.isEmpty()) {
            throw new SassValueException("Expected at-rule name.");
        }
        return new AtRootQuery(include, names);
    }

    /// Returns whether style rules are excluded by this query.
    ///
    /// @return whether style-rule parents are skipped
    public boolean excludesStyleRules() {
        return excludesName("rule");
    }

    /// Returns whether an at-rule or special name is excluded.
    ///
    /// @param name the lower-case rule name
    /// @return whether the name is excluded
    public boolean excludesName(String name) {
        Objects.requireNonNull(name, "name");
        var all = names.contains("all");
        var named = names.contains(name);
        return (all || named) != include;
    }

    /// Returns whether one CSS parent is excluded by this query.
    ///
    /// @param parent the candidate parent
    /// @return whether the parent should be omitted from the adjusted path
    public boolean excludes(CssParentNode parent) {
        Objects.requireNonNull(parent, "parent");
        if (parent instanceof CssStyleRule) {
            return excludesStyleRules();
        }
        if (parent instanceof CssMediaRule) {
            return excludesName("media");
        }
        if (parent instanceof CssSupportsRule) {
            return excludesName("supports");
        }
        if (parent instanceof CssUnknownAtRule unknown) {
            return excludesName(unknown.name().toLowerCase(Locale.ROOT));
        }
        return false;
    }
}
