// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Converts CSS IR into plain CSS text.
///
/// This first implementation supports expanded output only. Compressed style is
/// rejected by the public compiler until a dedicated serializer path exists.
@ApiStatus.Internal
@NotNullByDefault
public final class CssSerializer {
    /// Contains two spaces per indentation level.
    private static final int INDENT_WIDTH = 2;

    /// Prevents instantiation.
    private CssSerializer() {
    }

    /// Serializes a stylesheet according to the CSS target options.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target     the public CSS output options
    /// @return the generated CSS text
    /// @throws CssSerializeException if a value cannot be represented in CSS
    /// @throws IllegalArgumentException if {@code target} requests compressed style
    public static String serialize(CssStylesheet stylesheet, CssTarget target) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        Objects.requireNonNull(target, "target");
        if (target.style() != OutputStyle.EXPANDED) {
            throw new IllegalArgumentException("compressed CSS output is not supported");
        }

        var buffer = new StringBuilder();
        writeStylesheet(stylesheet, buffer);
        var css = buffer.toString();
        if (target.charset() && containsNonAscii(css)) {
            return "@charset \"UTF-8\";\n" + css;
        }
        return css;
    }

    /// Writes the top-level stylesheet children.
    ///
    /// @param stylesheet the CSS root
    /// @param buffer     the output buffer
    private static void writeStylesheet(CssStylesheet stylesheet, StringBuilder buffer) {
        @Nullable CssNode previous = null;
        for (var child : stylesheet.children()) {
            if (child.isInvisible()) {
                continue;
            }
            if (previous != null) {
                if (requiresSemicolon(previous)) {
                    buffer.append(';');
                }
                buffer.append('\n');
                if (previous.isGroupEnd()) {
                    buffer.append('\n');
                }
            }
            writeNode(child, buffer, 0);
            previous = child;
        }
        if (previous != null && requiresSemicolon(previous)) {
            buffer.append(';');
        }
    }

    /// Writes one CSS node at the given indentation depth.
    ///
    /// @param node        the node to write
    /// @param buffer      the output buffer
    /// @param indentation the current indentation level
    private static void writeNode(CssNode node, StringBuilder buffer, int indentation) {
        if (node instanceof CssStyleRule rule) {
            writeStyleRule(rule, buffer, indentation);
        } else if (node instanceof CssDeclaration declaration) {
            writeDeclaration(declaration, buffer, indentation);
        } else if (node instanceof CssComment comment) {
            writeIndentation(buffer, indentation);
            buffer.append(comment.text());
        } else {
            throw new IllegalStateException("unsupported CSS node: " + node.getClass().getName());
        }
    }

    /// Writes one expanded style rule.
    ///
    /// @param rule        the style rule
    /// @param buffer      the output buffer
    /// @param indentation the current indentation level
    private static void writeStyleRule(
            CssStyleRule rule,
            StringBuilder buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.append(rule.selector().value().toCssString());
        buffer.append(" {");
        writeChildren(rule, buffer, indentation);
        buffer.append('}');
    }

    /// Writes the braced children of a parent node.
    ///
    /// @param parent      the parent whose children are written
    /// @param buffer      the output buffer
    /// @param indentation the parent indentation level
    private static void writeChildren(
            CssParentNode parent,
            StringBuilder buffer,
            int indentation
    ) {
        @Nullable CssNode previous = null;
        for (var child : parent.children()) {
            if (child.isInvisible()) {
                continue;
            }
            if (previous != null && requiresSemicolon(previous)) {
                buffer.append(';');
            }
            buffer.append('\n');
            writeNode(child, buffer, indentation + 1);
            previous = child;
        }
        if (previous != null) {
            if (requiresSemicolon(previous)) {
                buffer.append(';');
            }
            buffer.append('\n');
            writeIndentation(buffer, indentation);
        }
    }

    /// Writes one expanded declaration.
    ///
    /// @param declaration the declaration
    /// @param buffer      the output buffer
    /// @param indentation the current indentation level
    private static void writeDeclaration(
            CssDeclaration declaration,
            StringBuilder buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.append(declaration.name().value());
        buffer.append(':');
        if (!declaration.parsedAsSassScript()) {
            buffer.append(rawValueText(declaration));
            return;
        }
        buffer.append(' ');
        try {
            buffer.append(declaration.value().value().toCssString());
        } catch (SassValueException cause) {
            throw new CssSerializeException(
                    Objects.requireNonNull(cause.getMessage(), "value failure message"),
                    declaration.value().span(),
                    cause
            );
        }
    }

    /// Returns the raw unquoted CSS text for a non-SassScript declaration.
    ///
    /// @param declaration the raw declaration
    /// @return the stored string text
    private static String rawValueText(CssDeclaration declaration) {
        if (!(declaration.value().value() instanceof SassString string)) {
            throw new IllegalStateException("raw declaration value must be a SassString");
        }
        return string.text();
    }

    /// Returns whether a node must be followed by a semicolon.
    ///
    /// @param node the preceding node
    /// @return whether a semicolon is required
    private static boolean requiresSemicolon(CssNode node) {
        return node instanceof CssDeclaration;
    }

    /// Writes indentation spaces for the given depth.
    ///
    /// @param buffer      the output buffer
    /// @param indentation the indentation level
    private static void writeIndentation(StringBuilder buffer, int indentation) {
        buffer.append(" ".repeat(indentation * INDENT_WIDTH));
    }

    /// Returns whether the CSS contains a non-ASCII code unit.
    ///
    /// @param css the generated CSS
    /// @return whether a charset prefix may be required
    private static boolean containsNonAscii(String css) {
        for (var index = 0; index < css.length(); index++) {
            if (css.charAt(index) > 0x7F) {
                return true;
            }
        }
        return false;
    }
}
