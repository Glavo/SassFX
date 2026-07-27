// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.parse;

import org.glavo.scssfx.Diagnostic;
import org.glavo.scssfx.internal.ast.SassExpression;
import org.glavo.scssfx.internal.ast.UseRule;
import org.glavo.scssfx.internal.ast.VariableDeclaration;
import org.glavo.scssfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Provides complete-production parsers used by the interactive SassScript shell.
@ApiStatus.Internal
@NotNullByDefault
public final class SassScriptParser {
    /// Prevents instantiation.
    private SassScriptParser() {
    }

    /// Parses one complete SassScript expression.
    ///
    /// @param source the source containing exactly one expression
    /// @return the expression and its parse-time warnings
    /// @throws ParseException if the source is not one complete expression
    public static Parsed<SassExpression> parseExpression(SourceFile source) {
        var parser = new ScssParser(
                Objects.requireNonNull(source, "source")
        );
        return new Parsed<>(
                parser.parseExpression(),
                parser.parseTimeWarnings()
        );
    }

    /// Parses one complete variable declaration.
    ///
    /// @param source the source containing exactly one variable declaration
    /// @return the declaration and its parse-time warnings
    /// @throws ParseException if the source is not one complete declaration
    public static Parsed<VariableDeclaration> parseVariableDeclaration(
            SourceFile source
    ) {
        var parser = new ScssParser(
                Objects.requireNonNull(source, "source")
        );
        return new Parsed<>(
                parser.parseInteractiveVariableDeclaration(),
                parser.parseTimeWarnings()
        );
    }

    /// Parses one complete {@code @use} rule.
    ///
    /// @param source the source containing exactly one use rule
    /// @return the rule and its parse-time warnings
    /// @throws ParseException if the source is not one complete use rule
    public static Parsed<UseRule> parseUseRule(SourceFile source) {
        var parser = new ScssParser(
                Objects.requireNonNull(source, "source")
        );
        return new Parsed<>(
                parser.parseInteractiveUseRule(),
                parser.parseTimeWarnings()
        );
    }

    /// Reports whether text begins exactly like an unqualified variable
    /// declaration.
    ///
    /// Text after the first declaration colon is ignored. Leading whitespace
    /// and namespaced assignments do not match.
    ///
    /// @param text the complete interactive input line
    /// @return whether the line begins with a dollar-prefixed identifier and colon
    public static boolean isVariableDeclarationLike(String text) {
        var parser = new VariableDeclarationProbe(new SourceFile(
                Objects.requireNonNull(text, "text"),
                null
        ));
        return parser.matches();
    }

    /// Contains one parsed production and its parse-time warnings.
    ///
    /// @param node the parsed AST node
    /// @param warnings warnings produced while parsing the node
    /// @param <T> the parsed node type
    @ApiStatus.Internal
    @NotNullByDefault
    public record Parsed<T>(
            T node,
            @Unmodifiable List<Diagnostic> warnings
    ) {
        /// Creates an immutable parse result.
        public Parsed {
            Objects.requireNonNull(node, "node");
            warnings = List.copyOf(warnings);
        }
    }

    /// Probes only the declaration prefix without parsing its value.
    @NotNullByDefault
    private static final class VariableDeclarationProbe extends Parser {
        /// Creates a prefix probe.
        ///
        /// @param source the interactive source line
        private VariableDeclarationProbe(SourceFile source) {
            super(source);
        }

        /// Reports whether the scanner begins with {@code $name:}.
        ///
        /// @return whether the declaration prefix is present
        private boolean matches() {
            if (!scanner.scan('$') || !lookingAtIdentifier()) {
                return false;
            }
            identifier(false, false);
            whitespace(true);
            return scanner.scan(':');
        }
    }
}
