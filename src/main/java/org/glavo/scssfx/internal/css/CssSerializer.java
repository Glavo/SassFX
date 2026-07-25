// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.css;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.JavaFXCssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.internal.sourcemap.SourceMapBuffer;
import org.glavo.scssfx.internal.sourcemap.SourceMapGenerator;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Converts CSS IR into plain or JavaFX-targeted CSS text.
///
/// The JavaFX path serializes the same CSS IR without loading JavaFX classes at
/// runtime. Optional version-3 source maps are recorded while writing selectors
/// and declaration names/values.
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
        return serialize(stylesheet, target, false).css();
    }

    /// Serializes a stylesheet according to the JavaFX CSS target options.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target     the public JavaFX CSS output options
    /// @return the generated CSS text
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static String serialize(CssStylesheet stylesheet, JavaFXCssTarget target) {
        return serialize(stylesheet, target, false).css();
    }

    /// Serializes a stylesheet with an optional source map.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target     the public CSS output options
    /// @param sourceMap  whether a version-3 source map should be generated
    /// @return the CSS text and optional source map
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            CssTarget target,
            boolean sourceMap
    ) {
        Objects.requireNonNull(target, "target");
        var result = serialize(stylesheet, target.style(), sourceMap);
        if (target.charset()
                && target.style() == OutputStyle.EXPANDED
                && containsNonAscii(result.css())) {
            return new CssSerializeResult(
                    "@charset \"UTF-8\";\n" + result.css(),
                    result.sourceMap()
            );
        }
        return result;
    }

    /// Serializes a stylesheet for JavaFX with an optional source map.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target     the public JavaFX CSS output options
    /// @param sourceMap  whether a version-3 source map should be generated
    /// @return the CSS text and optional source map
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            JavaFXCssTarget target,
            boolean sourceMap
    ) {
        Objects.requireNonNull(target, "target");
        return serialize(stylesheet, target.style(), sourceMap);
    }

    /// Serializes a stylesheet with the selected layout style.
    private static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            OutputStyle style,
            boolean sourceMap
    ) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        Objects.requireNonNull(style, "style");
        var buffer = new SourceMapBuffer(sourceMap);
        switch (style) {
            case EXPANDED -> writeExpandedStylesheet(stylesheet, buffer);
            case COMPRESSED -> writeCompressedStylesheet(stylesheet, buffer);
        }
        return new CssSerializeResult(buffer.css(), SourceMapGenerator.generate(buffer));
    }

    /// Writes the top-level stylesheet children using expanded layout.
    private static void writeExpandedStylesheet(
            CssStylesheet stylesheet,
            SourceMapBuffer buffer
    ) {
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
                // Blank lines are driven by explicit group-end markers (between
                // style rules, etc.). dart-sass does not insert an automatic
                // blank line between a trailing {@code @import} and the first
                // following style rule in expanded output.
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
    private static void writeExpandedNode(
            CssNode node,
            SourceMapBuffer buffer,
            int indentation
    ) {
        if (node instanceof CssImport importRule) {
            writeIndentation(buffer, indentation);
            buffer.forSpan(importRule.span(), () ->
                    buffer.append("@import ").append(importRule.argument()));
        } else if (node instanceof CssMediaRule mediaRule) {
            writeExpandedMediaRule(mediaRule, buffer, indentation);
        } else if (node instanceof CssSupportsRule supportsRule) {
            writeExpandedSupportsRule(supportsRule, buffer, indentation);
        } else if (node instanceof CssUnknownAtRule unknownAtRule) {
            writeExpandedUnknownAtRule(unknownAtRule, buffer, indentation);
        } else if (node instanceof CssFontFace fontFace) {
            writeExpandedFontFace(fontFace, buffer, indentation);
        } else if (node instanceof CssStyleRule rule) {
            writeExpandedStyleRule(rule, buffer, indentation);
        } else if (node instanceof CssDeclaration declaration) {
            writeExpandedDeclaration(declaration, buffer, indentation);
        } else if (node instanceof CssComment comment) {
            if (!isSourceMapComment(comment.text())) {
                writeIndentation(buffer, indentation);
                buffer.forSpan(comment.span(), () -> buffer.append(comment.text()));
            }
        } else {
            throw new IllegalStateException("unsupported CSS node: " + node.getClass().getName());
        }
    }

    /// Writes one expanded opaque at-rule.
    private static void writeExpandedUnknownAtRule(
            CssUnknownAtRule rule,
            SourceMapBuffer buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(rule.span(), () -> {
            buffer.append('@').append(rule.name());
            if (!rule.value().isEmpty()) {
                buffer.append(' ').append(rule.value());
            }
        });
        if (!rule.hasBlock()) {
            return;
        }
        buffer.append(" {");
        // Empty bubbled {@code @keyframes {/**/}} matches dart-sass compact form.
        boolean compactCommentOnly = isKeyframesAtRuleName(rule.name());
        writeExpandedChildren(rule, buffer, indentation, compactCommentOnly);
        buffer.append('}');
    }

    /// Returns whether {@code name} is a keyframes at-rule, ignoring vendor prefixes.
    private static boolean isKeyframesAtRuleName(String name) {
        if ("keyframes".equals(name)) {
            return true;
        }
        if (!name.startsWith("-")) {
            return false;
        }
        int secondDash = name.indexOf('-', 1);
        return secondDash > 1 && "keyframes".equals(name.substring(secondDash + 1));
    }

    /// Writes one expanded style rule.
    private static void writeExpandedStyleRule(
            CssStyleRule rule,
            SourceMapBuffer buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(rule.selector().span(), () ->
                buffer.append(rule.selector().value().toCssString(false)));
        buffer.append(" {");
        writeExpandedChildren(rule, buffer, indentation);
        buffer.append('}');
    }

    /// Writes one expanded media rule.
    private static void writeExpandedMediaRule(
            CssMediaRule mediaRule,
            SourceMapBuffer buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(mediaRule.span(), () -> {
            buffer.append("@media ");
            appendMediaQueries(mediaRule, buffer, false);
        });
        buffer.append(" {");
        writeExpandedChildren(mediaRule, buffer, indentation);
        buffer.append('}');
    }

    /// Writes one expanded supports rule.
    private static void writeExpandedSupportsRule(
            CssSupportsRule supportsRule,
            SourceMapBuffer buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(supportsRule.span(), () -> {
            buffer.append("@supports ");
            buffer.append(supportsRule.condition());
        });
        buffer.append(" {");
        writeExpandedChildren(supportsRule, buffer, indentation);
        buffer.append('}');
    }

    /// Writes one expanded font-face rule.
    private static void writeExpandedFontFace(
            CssFontFace fontFace,
            SourceMapBuffer buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(fontFace.span(), () -> buffer.append("@font-face {"));
        // Bubbled empty font-face with only a comment uses compact spacing
        // ({@code @font-face { /**/ }}), matching dart-sass.
        writeExpandedChildren(fontFace, buffer, indentation, true);
        buffer.append('}');
    }

    /// Writes the braced children of a parent node using expanded layout.
    private static void writeExpandedChildren(
            CssParentNode parent,
            SourceMapBuffer buffer,
            int indentation
    ) {
        writeExpandedChildren(parent, buffer, indentation, false);
    }

    /// Writes the braced children of a parent node using expanded layout.
    ///
    /// When {@code compactCommentOnly} is true and the only visible children are
    /// comments (for example a bubbled {@code @font-face {/**/}} or empty
    /// {@code @keyframes {/**/}}), dart-sass emits a compact single-line form
    /// with spaces around the comment rather than indented multi-line layout.
    private static void writeExpandedChildren(
            CssParentNode parent,
            SourceMapBuffer buffer,
            int indentation,
            boolean compactCommentOnly
    ) {
        var visible = new java.util.ArrayList<CssNode>();
        for (var child : parent.children()) {
            if (!child.isInvisible()) {
                visible.add(child);
            }
        }
        if (compactCommentOnly
                && !visible.isEmpty()
                && visible.stream().allMatch(CssComment.class::isInstance)) {
            buffer.append(' ');
            for (var index = 0; index < visible.size(); index++) {
                if (index > 0) {
                    buffer.append(' ');
                }
                var comment = (CssComment) visible.get(index);
                buffer.forSpan(comment.span(), () -> buffer.append(comment.text()));
            }
            buffer.append(' ');
            return;
        }
        @Nullable CssNode previous = null;
        for (var child : visible) {
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
    private static void writeExpandedDeclaration(
            CssDeclaration declaration,
            SourceMapBuffer buffer,
            int indentation
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(declaration.name().span(), () ->
                buffer.append(declaration.name().value()));
        buffer.append(':');
        if (declaration.parsedAsSassScript()) {
            buffer.append(' ');
        }
        appendDeclarationValue(declaration, buffer);
    }

    /// Writes all visible top-level nodes using compressed layout.
    private static void writeCompressedStylesheet(
            CssStylesheet stylesheet,
            SourceMapBuffer buffer
    ) {
        for (var child : stylesheet.children()) {
            if (!isCompressedVisible(child)) {
                continue;
            }
            writeCompressedNode(child, buffer);
        }
    }

    /// Writes one visible CSS node using compressed layout.
    private static void writeCompressedNode(CssNode node, SourceMapBuffer buffer) {
        if (node instanceof CssImport importRule) {
            buffer.forSpan(importRule.span(), () ->
                    buffer.append("@import ").append(importRule.argument()).append(';'));
        } else if (node instanceof CssMediaRule mediaRule) {
            buffer.forSpan(mediaRule.span(), () -> {
                buffer.append("@media");
                if (mediaRule.queries().get(0).startsWithIdentifier()) {
                    buffer.append(' ');
                }
                appendMediaQueries(mediaRule, buffer, true);
            });
            buffer.append('{');
            writeCompressedChildren(mediaRule, buffer);
            buffer.append('}');
        } else if (node instanceof CssSupportsRule supportsRule) {
            buffer.forSpan(supportsRule.span(), () -> {
                buffer.append("@supports");
                if (!supportsRule.condition().startsWith("(")) {
                    buffer.append(' ');
                }
                buffer.append(supportsRule.condition());
            });
            buffer.append('{');
            writeCompressedChildren(supportsRule, buffer);
            buffer.append('}');
        } else if (node instanceof CssUnknownAtRule unknownAtRule) {
            buffer.forSpan(unknownAtRule.span(), () -> {
                buffer.append('@').append(unknownAtRule.name());
                if (!unknownAtRule.value().isEmpty()) {
                    buffer.append(' ').append(unknownAtRule.value());
                }
            });
            if (unknownAtRule.hasBlock()) {
                buffer.append('{');
                writeCompressedChildren(unknownAtRule, buffer);
                buffer.append('}');
            } else {
                buffer.append(';');
            }
        } else if (node instanceof CssFontFace fontFace) {
            buffer.forSpan(fontFace.span(), () -> buffer.append("@font-face{"));
            writeCompressedChildren(fontFace, buffer);
            buffer.append('}');
        } else if (node instanceof CssStyleRule rule) {
            buffer.forSpan(rule.selector().span(), () ->
                    buffer.append(rule.selector().value().toCssString(false)));
            buffer.append('{');
            writeCompressedChildren(rule, buffer);
            buffer.append('}');
        } else if (node instanceof CssDeclaration declaration) {
            writeCompressedDeclaration(declaration, buffer);
        } else if (node instanceof CssComment comment) {
            if (comment.isPreserved() && !isSourceMapComment(comment.text())) {
                buffer.forSpan(comment.span(), () -> buffer.append(comment.text()));
            }
        } else {
            throw new IllegalStateException("unsupported CSS node: " + node.getClass().getName());
        }
    }

    /// Appends a media-query list using the selected layout separators.
    private static void appendMediaQueries(
            CssMediaRule mediaRule,
            SourceMapBuffer buffer,
            boolean compressed
    ) {
        for (var index = 0; index < mediaRule.queries().size(); index++) {
            if (index > 0) {
                buffer.append(compressed ? "," : ", ");
            }
            var query = mediaRule.queries().get(index);
            buffer.append(compressed ? query.toCompressedCss() : query.toCssString());
        }
    }

    /// Writes visible braced children using compressed layout.
    private static void writeCompressedChildren(
            CssParentNode parent,
            SourceMapBuffer buffer
    ) {
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
    private static void writeCompressedDeclaration(
            CssDeclaration declaration,
            SourceMapBuffer buffer
    ) {
        buffer.forSpan(declaration.name().span(), () ->
                buffer.append(declaration.name().value()));
        buffer.append(':');
        appendDeclarationValue(declaration, buffer);
    }

    /// Returns whether a CSS comment is a source-map or source-URL directive.
    ///
    /// Matches dart-sass serialization: {@code /*# sourceMappingURL=} and
    /// {@code /*# sourceURL=} comments are never emitted.
    ///
    /// @param text the complete comment text including delimiters
    /// @return whether the comment should be dropped from CSS output
    private static boolean isSourceMapComment(String text) {
        return text.startsWith("/*# sourceMappingURL=")
                || text.startsWith("/*# sourceURL=");
    }

    /// Returns whether a node contributes to compressed output.
    private static boolean isCompressedVisible(CssNode node) {
        if (node instanceof CssComment comment) {
            return comment.isPreserved() && !isSourceMapComment(comment.text());
        }
        if (node instanceof CssUnknownAtRule) {
            return true;
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
    private static void appendDeclarationValue(
            CssDeclaration declaration,
            SourceMapBuffer buffer
    ) {
        if (!declaration.parsedAsSassScript()) {
            buffer.forSpan(declaration.value().span(), () ->
                    buffer.append(rawValueText(declaration)));
            return;
        }
        try {
            var css = declaration.value().value().toCssString();
            buffer.forSpan(declaration.value().span(), () -> buffer.append(css));
        } catch (SassValueException cause) {
            throw new CssSerializeException(
                    Objects.requireNonNull(cause.getMessage(), "value failure message"),
                    declaration.value().span(),
                    cause
            );
        }
    }

    /// Returns the raw unquoted CSS text for a non-SassScript declaration.
    private static String rawValueText(CssDeclaration declaration) {
        if (!(declaration.value().value() instanceof SassString string)) {
            throw new IllegalStateException("raw declaration value must be a SassString");
        }
        return string.text();
    }

    /// Returns whether a node must be followed by a semicolon.
    private static boolean requiresSemicolon(CssNode node) {
        return node instanceof CssDeclaration
                || node instanceof CssImport
                || node instanceof CssUnknownAtRule rule && !rule.hasBlock();
    }

    /// Writes indentation spaces for the given depth.
    private static void writeIndentation(SourceMapBuffer buffer, int indentation) {
        buffer.append(" ".repeat(indentation * INDENT_WIDTH));
    }

    /// Returns whether the CSS contains a non-ASCII code unit.
    private static boolean containsNonAscii(String css) {
        for (var index = 0; index < css.length(); index++) {
            if (css.charAt(index) > 0x7F) {
                return true;
            }
        }
        return false;
    }
}
