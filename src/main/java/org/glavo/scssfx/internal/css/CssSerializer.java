// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.JavaFXCssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Converts CSS IR into plain or JavaFX-targeted CSS text.
///
/// The JavaFX path serializes the same CSS IR without loading JavaFX classes at
/// runtime.
@ApiStatus.Internal
@NotNullByDefault
public final class CssSerializer {
    /// Contains two spaces per indentation level.
    private static final int INDENT_WIDTH = 2;

    /// Prevents instantiation.
    private CssSerializer() {
    }

    /// Serializes a stylesheet according to the standard CSS target options.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target     the public CSS output options
    /// @return the generated CSS text
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static String serialize(CssStylesheet stylesheet, CssTarget target) {
        Objects.requireNonNull(target, "target");
        var css = serialize(stylesheet, target.style());
        if (target.charset() && target.style() == OutputStyle.EXPANDED && containsNonAscii(css)) {
            return "@charset \"UTF-8\";\n" + css;
        }
        return css;
    }

    /// Serializes a stylesheet according to the JavaFX CSS target options.
    ///
    /// The compatibility level is accepted by the public target but does not
    /// alter textual CSS IR serialization.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target     the public JavaFX CSS output options
    /// @return the generated CSS text
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static String serialize(CssStylesheet stylesheet, JavaFXCssTarget target) {
        Objects.requireNonNull(target, "target");
        return serialize(stylesheet, target.style());
    }

    /// Serializes a stylesheet with the selected layout style.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param style      the output layout style
    /// @return the generated CSS text
    /// @throws CssSerializeException if a value cannot be represented in CSS
    private static String serialize(CssStylesheet stylesheet, OutputStyle style) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        Objects.requireNonNull(style, "style");
        var buffer = new StringBuilder();
        switch (style) {
            case EXPANDED -> writeExpandedStylesheet(stylesheet, buffer);
            case COMPRESSED -> writeCompressedStylesheet(stylesheet, buffer);
        }
        return buffer.toString();
    }

    /// Writes the top-level stylesheet children using expanded layout.
    ///
    /// @param stylesheet the CSS root
    /// @param buffer     the output buffer
    private static void writeExpandedStylesheet(CssStylesheet stylesheet, StringBuilder buffer) {
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
            writeExpandedNode(child, buffer, 0);
            previous = child;
        }
        if (previous != null && requiresSemicolon(previous)) {
            buffer.append(';');
        }
    }

    /// Writes one CSS node at the given indentation depth using expanded layout.
    ///
    /// @param node        the node to write
    /// @param buffer      the output buffer
    /// @param indentation the current indentation level
    private static void writeExpandedNode(CssNode node, StringBuilder buffer, int indentation) {
        if (node instanceof CssMediaRule mediaRule) {
            writeExpandedMediaRule(mediaRule, buffer, indentation);
        } else if (node instanceof CssSupportsRule supportsRule) {
            writeExpandedSupportsRule(supportsRule, buffer, indentation);
        } else if (node instanceof CssFontFace fontFace) {
            writeExpandedFontFace(fontFace, buffer, indentation);
        } else if (node instanceof CssStyleRule rule) {
            writeExpandedStyleRule(rule, buffer, indentation);
        } else if (node instanceof CssDeclaration declaration) {
            writeExpandedDeclaration(declaration, buffer, indentation);
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
    private static void writeExpandedStyleRule(
            CssStyleRule rule,
            StringBuilder buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.append(rule.selector().value().toCssString());
        buffer.append(" {");
        writeExpandedChildren(rule, buffer, indentation);
        buffer.append('}');
    }

    /// Writes one expanded media rule.
    ///
    /// @param mediaRule  the rule to write
    /// @param buffer     the destination CSS buffer
    /// @param indentation the current indentation level
    private static void writeExpandedMediaRule(
            CssMediaRule mediaRule,
            StringBuilder buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.append("@media ");
        appendMediaQueries(mediaRule, buffer, false);
        buffer.append(" {");
        writeExpandedChildren(mediaRule, buffer, indentation);
        buffer.append('}');
    }

    /// Writes one expanded supports rule.
    ///
    /// @param supportsRule the rule to write
    /// @param buffer       the destination CSS buffer
    /// @param indentation  the current indentation level
    private static void writeExpandedSupportsRule(
            CssSupportsRule supportsRule,
            StringBuilder buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.append("@supports ");
        buffer.append(supportsRule.condition());
        buffer.append(" {");
        writeExpandedChildren(supportsRule, buffer, indentation);
        buffer.append('}');
    }

    /// Writes one expanded font-face rule.
    ///
    /// @param fontFace    the rule to write
    /// @param buffer      the destination CSS buffer
    /// @param indentation the current indentation level
    private static void writeExpandedFontFace(
            CssFontFace fontFace,
            StringBuilder buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.append("@font-face {");
        writeExpandedChildren(fontFace, buffer, indentation);
        buffer.append('}');
    }

    /// Writes the braced children of a parent node using expanded layout.
    ///
    /// @param parent      the parent whose children are written
    /// @param buffer      the output buffer
    /// @param indentation the parent indentation level
    private static void writeExpandedChildren(
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
            writeExpandedNode(child, buffer, indentation + 1);
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
    private static void writeExpandedDeclaration(
            CssDeclaration declaration,
            StringBuilder buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.append(declaration.name().value());
        buffer.append(':');
        if (declaration.parsedAsSassScript()) {
            buffer.append(' ');
        }
        appendDeclarationValue(declaration, buffer);
    }

    /// Writes all visible top-level nodes using compressed layout.
    ///
    /// @param stylesheet the CSS root
    /// @param buffer     the output buffer
    private static void writeCompressedStylesheet(CssStylesheet stylesheet, StringBuilder buffer) {
        for (var child : stylesheet.children()) {
            if (!isCompressedVisible(child)) {
                continue;
            }
            writeCompressedNode(child, buffer);
        }
    }

    /// Writes one visible CSS node using compressed layout.
    ///
    /// @param node   the node to write
    /// @param buffer the output buffer
    private static void writeCompressedNode(CssNode node, StringBuilder buffer) {
        if (node instanceof CssMediaRule mediaRule) {
            buffer.append("@media");
            if (mediaRule.queries().get(0).startsWithIdentifier()) {
                buffer.append(' ');
            }
            appendMediaQueries(mediaRule, buffer, true);
            buffer.append('{');
            writeCompressedChildren(mediaRule, buffer);
            buffer.append('}');
        } else if (node instanceof CssSupportsRule supportsRule) {
            buffer.append("@supports");
            if (!supportsRule.condition().startsWith("(")) {
                buffer.append(' ');
            }
            buffer.append(supportsRule.condition());
            buffer.append('{');
            writeCompressedChildren(supportsRule, buffer);
            buffer.append('}');
        } else if (node instanceof CssFontFace fontFace) {
            buffer.append("@font-face{");
            writeCompressedChildren(fontFace, buffer);
            buffer.append('}');
        } else if (node instanceof CssStyleRule rule) {
            buffer.append(rule.selector().value().toCssString());
            buffer.append('{');
            writeCompressedChildren(rule, buffer);
            buffer.append('}');
        } else if (node instanceof CssDeclaration declaration) {
            writeCompressedDeclaration(declaration, buffer);
        } else if (node instanceof CssComment comment) {
            if (comment.isPreserved()) {
                buffer.append(comment.text());
            }
        } else {
            throw new IllegalStateException("unsupported CSS node: " + node.getClass().getName());
        }
    }

    /// Appends a media-query list using the selected layout separators.
    ///
    /// @param mediaRule  the rule that owns the queries
    /// @param buffer     the destination CSS buffer
    /// @param compressed whether compressed query spelling is required
    private static void appendMediaQueries(
            CssMediaRule mediaRule,
            StringBuilder buffer,
            boolean compressed
    ) {
        for (var index = 0; index < mediaRule.queries().size(); index++) {
            if (index > 0) {
                buffer.append(compressed ? ',' : ", ");
            }
            var query = mediaRule.queries().get(index);
            buffer.append(compressed ? query.toCompressedCss() : query.toCssString());
        }
    }

    /// Writes visible braced children using compressed layout.
    ///
    /// @param parent the parent whose children are written
    /// @param buffer the output buffer
    private static void writeCompressedChildren(CssParentNode parent, StringBuilder buffer) {
        boolean precedingDeclaration = false;
        for (var child : parent.children()) {
            if (!isCompressedVisible(child)) {
                continue;
            }
            if (precedingDeclaration) {
                buffer.append(';');
            }
            writeCompressedNode(child, buffer);
            precedingDeclaration = child instanceof CssDeclaration;
        }
    }

    /// Writes one declaration using compressed layout.
    ///
    /// @param declaration the declaration
    /// @param buffer      the output buffer
    private static void writeCompressedDeclaration(CssDeclaration declaration, StringBuilder buffer) {
        buffer.append(declaration.name().value());
        buffer.append(':');
        appendDeclarationValue(declaration, buffer);
    }

    /// Returns whether a node contributes to compressed output.
    ///
    /// @param node the node to inspect
    /// @return whether the node must be serialized
    private static boolean isCompressedVisible(CssNode node) {
        if (node instanceof CssComment comment) {
            return comment.isPreserved();
        }
        if (node instanceof CssParentNode parent) {
            for (var child : parent.children()) {
                if (isCompressedVisible(child)) {
                    return true;
                }
            }
            return false;
        }
        return !node.isInvisible();
    }

    /// Appends a declaration value without adding layout whitespace.
    ///
    /// @param declaration the declaration that owns the value
    /// @param buffer      the output buffer
    /// @throws CssSerializeException if a SassScript value cannot be represented in CSS
    private static void appendDeclarationValue(CssDeclaration declaration, StringBuilder buffer) {
        if (!declaration.parsedAsSassScript()) {
            buffer.append(rawValueText(declaration));
            return;
        }
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
