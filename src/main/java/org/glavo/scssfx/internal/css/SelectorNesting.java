// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/// Performs the first-pass string-level selector nesting used before a full
/// selector AST exists.
///
/// Complex selector grammar, combinator validation, and placeholder handling
/// remain future work. This helper only resolves parent injection for the
/// currently supported plain and parent-reference selectors.
@ApiStatus.Internal
@NotNullByDefault
public final class SelectorNesting {
    /// Prevents instantiation.
    private SelectorNesting() {
    }

    /// Nests {@code child} within {@code parent}.
    ///
    /// When {@code parent} is {@code null}, the trimmed child selector is
    /// returned unchanged. Otherwise each comma-separated parent complex is
    /// combined with each comma-separated child complex. A child that contains
    /// `&` substitutes the parent text for every occurrence; other children are
    /// appended after one space.
    ///
    /// @param parent the resolved parent selector, or {@code null} at the root
    /// @param child  the resolved child selector text
    /// @return the nested selector list text
    public static String nest(@Nullable String parent, String child) {
        var trimmedChild = child.trim();
        if (parent == null) {
            return trimmedChild;
        }

        var parents = splitSelectorList(parent);
        var children = splitSelectorList(trimmedChild);
        var result = new ArrayList<String>(parents.size() * children.size());
        for (var parentComplex : parents) {
            for (var childComplex : children) {
                if (childComplex.indexOf('&') >= 0) {
                    result.add(childComplex.replace("&", parentComplex).trim());
                } else {
                    result.add(parentComplex + " " + childComplex);
                }
            }
        }
        return String.join(", ", result);
    }

    /// Splits a selector list on top-level commas.
    ///
    /// Parentheses and quoted strings suppress comma splitting so argument
    /// lists and attribute values remain intact.
    ///
    /// @param selector the selector list text
    /// @return the trimmed complex selectors
    private static ArrayList<String> splitSelectorList(String selector) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        var parenDepth = 0;
        var quote = '\0';
        for (var index = 0; index < selector.length(); index++) {
            var character = selector.charAt(index);
            if (quote != '\0') {
                current.append(character);
                if (character == '\\' && index + 1 < selector.length()) {
                    current.append(selector.charAt(++index));
                } else if (character == quote) {
                    quote = '\0';
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                current.append(character);
                continue;
            }
            if (character == '(') {
                parenDepth++;
                current.append(character);
                continue;
            }
            if (character == ')' && parenDepth > 0) {
                parenDepth--;
                current.append(character);
                continue;
            }
            if (character == ',' && parenDepth == 0) {
                var complex = current.toString().trim();
                if (!complex.isEmpty()) {
                    result.add(complex);
                }
                current.setLength(0);
                continue;
            }
            current.append(character);
        }
        var complex = current.toString().trim();
        if (!complex.isEmpty()) {
            result.add(complex);
        }
        if (result.isEmpty()) {
            result.add("");
        }
        return result;
    }
}
