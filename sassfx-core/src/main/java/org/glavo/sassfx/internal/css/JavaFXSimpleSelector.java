// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.internal.ast.selector.ClassSelector;
import org.glavo.sassfx.internal.ast.selector.ComplexSelectorComponent;
import org.glavo.sassfx.internal.ast.selector.IdSelector;
import org.glavo.sassfx.internal.ast.selector.PseudoSelector;
import org.glavo.sassfx.internal.ast.selector.TypeSelector;
import org.glavo.sassfx.internal.ast.selector.UniversalSelector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Stores one compound selector in the form interpreted by JavaFX CSS.
///
/// JavaFX tokenizes selector identifiers with an ASCII-only grammar and stores
/// functional pseudo-class arguments as a concatenated sequence of identifier
/// and string tokens after removing trivia. Repeated style classes and
/// pseudo-classes are collapsed; the last `dir(...)` argument becomes a
/// normalized direction entry after all ordinary pseudo-classes.
/// [#from(ComplexSelectorComponent)] returns `null` when emitting the source
/// selector would fail or acquire different JavaFX semantics, including when
/// selector serialization cannot retain a line comment's terminator.
///
/// @param typeName      the element name, or `*` for the universal selector
/// @param styleClasses  the distinct style-class names in first-occurrence order
/// @param id            the ID name, or an empty string when absent
/// @param pseudoClasses the distinct ordinary pseudo-class strings in
///                      first-occurrence order, followed by the effective
///                      direction when present
@ApiStatus.Internal
@NotNullByDefault
public record JavaFXSimpleSelector(
        String typeName,
        @Unmodifiable List<String> styleClasses,
        String id,
        @Unmodifiable List<String> pseudoClasses
) {
    /// Creates an immutable JavaFX selector representation.
    public JavaFXSimpleSelector {
        Objects.requireNonNull(typeName, "typeName");
        styleClasses = List.copyOf(styleClasses);
        Objects.requireNonNull(id, "id");
        pseudoClasses = List.copyOf(pseudoClasses);
    }

    /// Converts one compound selector to JavaFX's selector representation.
    ///
    /// A selector is rejected when it contains a namespace, more than one type
    /// or ID, a non-JavaFX identifier, a pseudo-element, an unsupported simple
    /// selector, or a functional pseudo-class argument outside JavaFX's
    /// identifier-and-string token grammar. The returned lists reflect
    /// JavaFX's duplicate removal and special handling of `dir(...)`.
    ///
    /// @param component the compound selector and its trailing combinators
    /// @return the normalized representation, or `null` when JavaFX cannot
    /// interpret the selector without changing its meaning
    public static @Nullable JavaFXSimpleSelector from(
            ComplexSelectorComponent component
    ) {
        Objects.requireNonNull(component, "component");
        var typeName = "*";
        var id = "";
        var typeSpecified = false;
        var idSpecified = false;
        var styleClasses = new ArrayList<String>();
        var pseudoClasses = new ArrayList<String>();
        @Nullable String directionPseudoClass = null;

        for (var simple : component.selector().components()) {
            if (simple instanceof TypeSelector type) {
                var name = type.name().name().value();
                if (!type.name().isUnqualified()
                        || typeSpecified
                        || !JavaFXCssLexer.isIdentifier(name)) {
                    return null;
                }
                typeName = name;
                typeSpecified = true;
            } else if (simple instanceof UniversalSelector universal) {
                if (!universal.isUnqualified() || typeSpecified) {
                    return null;
                }
                typeSpecified = true;
            } else if (simple instanceof ClassSelector styleClass) {
                var name = styleClass.name().value();
                if (!JavaFXCssLexer.isIdentifier(name)) {
                    return null;
                }
                if (!styleClasses.contains(name)) {
                    styleClasses.add(name);
                }
            } else if (simple instanceof IdSelector identifier) {
                var name = identifier.name().value();
                if (idSpecified
                        || !name.equals(identifier.name().toCssString())
                        || !JavaFXCssLexer.isHashName(name)) {
                    return null;
                }
                id = name;
                idSpecified = true;
            } else if (simple instanceof PseudoSelector pseudo) {
                if (pseudo.element()) {
                    return null;
                }
                var name = pseudo.name().value();
                if (!JavaFXCssLexer.isIdentifier(name)) {
                    return null;
                }
                if (pseudo.argument() == null) {
                    if (!pseudoClasses.contains(name)) {
                        pseudoClasses.add(name);
                    }
                    continue;
                }
                var normalized = normalizeFunctionalPseudo(
                        name,
                        pseudo.argument().toCssString()
                );
                if (normalized == null) {
                    return null;
                }
                if (normalized.regionMatches(true, 0, "dir(", 0, 4)) {
                    directionPseudoClass = normalized.equalsIgnoreCase("dir(rtl)")
                            ? "dir(rtl)"
                            : "dir(ltr)";
                } else if (!pseudoClasses.contains(normalized)) {
                    pseudoClasses.add(normalized);
                }
            } else {
                return null;
            }
        }

        if (directionPseudoClass != null) {
            pseudoClasses.add(directionPseudoClass);
        }
        return new JavaFXSimpleSelector(
                typeName,
                styleClasses,
                id,
                pseudoClasses
        );
    }

    /// Normalizes one functional pseudo-class using JavaFX lexer semantics.
    ///
    /// @param name     the decoded pseudo-class name
    /// @param argument the serialized content between parentheses
    /// @return the stored JavaFX pseudo-class string, or `null` when an
    /// unsupported token occurs
    private static @Nullable String normalizeFunctionalPseudo(
            String name,
            String argument
    ) {
        if (name.equals("url")) {
            return null;
        }

        var result = new StringBuilder(name).append('(');
        var index = 0;
        while (index < argument.length()) {
            var triviaEnd = JavaFXCssLexer.triviaEnd(argument, index);
            if (triviaEnd < 0) {
                return null;
            }
            if (triviaEnd > index) {
                index = triviaEnd;
                continue;
            }
            var current = argument.charAt(index);
            if (current == '\'' || current == '"') {
                var end = argument.indexOf(current, index + 1);
                if (end < 0) {
                    return null;
                }
                result.append(argument, index, end + 1);
                index = end + 1;
                continue;
            }

            var identifierEnd = JavaFXCssLexer.identifierEnd(argument, index);
            if (identifierEnd == index) {
                return null;
            }
            result.append(argument, index, identifierEnd);
            index = identifierEnd;
        }
        return result.append(')').toString();
    }

}
