// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.internal.sourcemap.SourceMapBuffer;
import org.glavo.sassfx.internal.sourcemap.SourceMapGenerator;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.Map;
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
        return serialize(stylesheet, target, sourceMap, Map.of());
    }

    /// Serializes a stylesheet with source URL substitutions.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target the public CSS output options
    /// @param sourceMap whether a version-3 source map should be generated
    /// @param sourceMapUrls alternate source URLs keyed by canonical URL
    /// @return the CSS text and optional source map
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            CssTarget target,
            boolean sourceMap,
            @Unmodifiable Map<URI, URI> sourceMapUrls
    ) {
        return serialize(
                stylesheet,
                target,
                sourceMap,
                sourceMapUrls,
                false,
                Map.of(),
                null
        );
    }

    /// Serializes a stylesheet with complete source-map input metadata.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target the public CSS output options
    /// @param sourceMap whether a version-3 source map should be generated
    /// @param sourceMapUrls alternate source URLs keyed by canonical URL
    /// @param sourceMapIncludeSources whether original sources are embedded
    /// @param sourceContents original text keyed by canonical source URL
    /// @param stdinContents URL-less root source text, or {@code null}
    /// @return the CSS text and optional source map
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            CssTarget target,
            boolean sourceMap,
            @Unmodifiable Map<URI, URI> sourceMapUrls,
            boolean sourceMapIncludeSources,
            @Unmodifiable Map<URI, String> sourceContents,
            @Nullable String stdinContents
    ) {
        Objects.requireNonNull(target, "target");
        var result = serialize(
                stylesheet,
                target.style(),
                sourceMap,
                false,
                sourceMapUrls,
                sourceMapIncludeSources,
                sourceContents,
                stdinContents,
                ""
        );
        if (!target.charset() || !containsNonAscii(result.css())) {
            return result;
        }
        var prefix = charsetPrefix(target.style());
        return sourceMap
                ? serialize(
                        stylesheet,
                        target.style(),
                        true,
                        false,
                        sourceMapUrls,
                        sourceMapIncludeSources,
                        sourceContents,
                        stdinContents,
                        prefix
                )
                : new CssSerializeResult(prefix + result.css(), null);
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
        return serialize(stylesheet, target, sourceMap, Map.of());
    }

    /// Serializes a JavaFX stylesheet with source URL substitutions.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target the JavaFX CSS output options
    /// @param sourceMap whether a version-3 source map should be generated
    /// @param sourceMapUrls alternate source URLs keyed by canonical URL
    /// @return the CSS text and optional source map
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            JavaFXCssTarget target,
            boolean sourceMap,
            @Unmodifiable Map<URI, URI> sourceMapUrls
    ) {
        return serialize(
                stylesheet,
                target,
                sourceMap,
                sourceMapUrls,
                false,
                Map.of(),
                null
        );
    }

    /// Serializes a JavaFX stylesheet with complete source-map input metadata.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target the JavaFX CSS output options
    /// @param sourceMap whether a version-3 source map should be generated
    /// @param sourceMapUrls alternate source URLs keyed by canonical URL
    /// @param sourceMapIncludeSources whether original sources are embedded
    /// @param sourceContents original text keyed by canonical source URL
    /// @param stdinContents URL-less root source text, or {@code null}
    /// @return the CSS text and optional source map
    /// @throws CssSerializeException if a value cannot be represented in CSS
    public static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            JavaFXCssTarget target,
            boolean sourceMap,
            @Unmodifiable Map<URI, URI> sourceMapUrls,
            boolean sourceMapIncludeSources,
            @Unmodifiable Map<URI, String> sourceContents,
            @Nullable String stdinContents
    ) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        Objects.requireNonNull(target, "target");
        JavaFXCssValidator.validate(stylesheet, target.javaFXTarget());
        return serialize(
                stylesheet,
                target.style(),
                sourceMap,
                true,
                sourceMapUrls,
                sourceMapIncludeSources,
                sourceContents,
                stdinContents,
                ""
        );
    }

    /// Serializes a stylesheet with the selected layout and grammar profile.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param style      the output layout
    /// @param sourceMap  whether source-map entries are recorded
    /// @param javaFX     whether JavaFX-required token separators are emitted
    /// @param sourceMapUrls alternate source URLs keyed by canonical URL
    /// @param sourceMapIncludeSources whether original sources are embedded
    /// @param sourceContents original text keyed by canonical source URL
    /// @param stdinContents URL-less root source text, or {@code null}
    /// @param prefix text emitted before the first mapped node
    /// @return the serialized CSS and optional source map
    private static CssSerializeResult serialize(
            CssStylesheet stylesheet,
            OutputStyle style,
            boolean sourceMap,
            boolean javaFX,
            @Unmodifiable Map<URI, URI> sourceMapUrls,
            boolean sourceMapIncludeSources,
            @Unmodifiable Map<URI, String> sourceContents,
            @Nullable String stdinContents,
            String prefix
    ) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(prefix, "prefix");
        var buffer = new SourceMapBuffer(
                sourceMap,
                sourceMapUrls,
                sourceMapIncludeSources,
                sourceContents,
                stdinContents
        );
        buffer.append(prefix);
        switch (style) {
            case EXPANDED -> writeExpandedStylesheet(stylesheet, buffer, javaFX);
            case COMPRESSED -> writeCompressedStylesheet(stylesheet, buffer, javaFX);
        }
        return new CssSerializeResult(buffer.css(), SourceMapGenerator.generate(buffer));
    }

    /// Returns the charset marker used by a non-ASCII output style.
    ///
    /// @param style the selected output style
    /// @return an expanded charset declaration or compressed UTF-8 BOM
    private static String charsetPrefix(OutputStyle style) {
        return style == OutputStyle.EXPANDED
                ? "@charset \"UTF-8\";\n"
                : "\uFEFF";
    }

    /// Writes the top-level stylesheet children using expanded layout.
    private static void writeExpandedStylesheet(
            CssStylesheet stylesheet,
            SourceMapBuffer buffer,
            boolean javaFX
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
                if (isTrailingComment(child, previous)) {
                    // Same-line trailing comment after a statement (dart-sass).
                    buffer.append(' ');
                    writeExpandedComment((CssComment) child, buffer, 0, false);
                } else {
                    buffer.append('\n');
                    // Blank lines are driven by explicit group-end markers (between
                    // style rules, etc.). dart-sass does not insert an automatic
                    // blank line between a trailing {@code @import} and the first
                    // following style rule in expanded output.
                    if (previous.isGroupEnd()) {
                        buffer.append('\n');
                    }
                    writeExpandedNode(child, buffer, 0, javaFX);
                }
            } else {
                writeExpandedNode(child, buffer, 0, javaFX);
            }
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
            int indentation,
            boolean javaFX
    ) {
        if (node instanceof CssImport importRule) {
            writeIndentation(buffer, indentation);
            buffer.forSpan(importRule.span(), () ->
                    buffer.append("@import ").append(importRule.argument()));
        } else if (node instanceof CssMediaRule mediaRule) {
            writeExpandedMediaRule(mediaRule, buffer, indentation, javaFX);
        } else if (node instanceof CssSupportsRule supportsRule) {
            writeExpandedSupportsRule(supportsRule, buffer, indentation, javaFX);
        } else if (node instanceof CssUnknownAtRule unknownAtRule) {
            writeExpandedUnknownAtRule(unknownAtRule, buffer, indentation, javaFX);
        } else if (node instanceof CssFontFace fontFace) {
            writeExpandedFontFace(fontFace, buffer, indentation, javaFX);
        } else if (node instanceof CssStyleRule rule) {
            writeExpandedStyleRule(rule, buffer, indentation, javaFX);
        } else if (node instanceof CssDeclaration declaration) {
            writeExpandedDeclaration(declaration, buffer, indentation, javaFX);
        } else if (node instanceof CssComment comment) {
            writeExpandedComment(comment, buffer, indentation, true);
        } else {
            throw new IllegalStateException("unsupported CSS node: " + node.getClass().getName());
        }
    }

    /// Writes one expanded loud comment.
    ///
    /// @param comment     the comment node
    /// @param buffer      the serialization buffer
    /// @param indentation indentation depth when {@code writeIndent} is true
    /// @param writeIndent whether to emit leading indentation
    private static void writeExpandedComment(
            CssComment comment,
            SourceMapBuffer buffer,
            int indentation,
            boolean writeIndent
    ) {
        if (isSourceMapComment(comment.text())) {
            return;
        }
        if (writeIndent) {
            writeIndentation(buffer, indentation);
        }
        buffer.forSpan(comment.span(), () -> {
            // Match dart-sass: multi-line comments are re-indented relative to the
            // least-indented continuation line and the current CSS indentation.
            @Nullable Integer minimum = minimumContinuationIndentation(comment.text());
            if (minimum == null) {
                buffer.append(comment.text());
                return;
            }
            int columnHint = comment.span() != null
                    ? comment.span().start().column()
                    : indentation * INDENT_WIDTH;
            int sourceMinimum = Math.min(minimum, columnHint);
            writeWithIndent(buffer, comment.text(), sourceMinimum, indentation);
        });
    }

    /// Returns the indentation of the least-indented non-empty continuation line.
    ///
    /// @param text multi-line CSS text
    /// @return the column minimum, {@code -1} when newlines exist but none are
    /// indented, or {@code null} when {@code text} has no newlines
    private static @Nullable Integer minimumContinuationIndentation(String text) {
        var firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            return null;
        }
        @Nullable Integer min = null;
        var index = firstNewline + 1;
        while (index <= text.length()) {
            var lineStart = index;
            while (index < text.length()) {
                var character = text.charAt(index);
                if (character != ' ' && character != '\t') {
                    break;
                }
                index++;
            }
            if (index >= text.length()) {
                break;
            }
            if (text.charAt(index) == '\n') {
                index++;
                continue;
            }
            var column = index - lineStart;
            min = min == null ? column : Math.min(min, column);
            while (index < text.length() && text.charAt(index) != '\n') {
                index++;
            }
            if (index < text.length()) {
                index++;
            }
        }
        return min == null ? -1 : min;
    }

    /// Writes {@code text}, replacing {@code minimumIndentation} with the current
    /// expanded CSS indentation for each non-empty line after the first.
    ///
    /// @param buffer              the output buffer
    /// @param text                the multi-line text
    /// @param minimumIndentation  source indentation to strip from continuations
    /// @param indentation         current CSS indentation depth
    private static void writeWithIndent(
            SourceMapBuffer buffer,
            String text,
            int minimumIndentation,
            int indentation
    ) {
        var firstNewline = text.indexOf('\n');
        if (firstNewline < 0) {
            buffer.append(text);
            return;
        }
        buffer.append(text.substring(0, firstNewline));
        var index = firstNewline + 1;
        while (true) {
            var lineStart = index;
            var newlines = 1;
            while (true) {
                if (index >= text.length()) {
                    buffer.append(' ');
                    return;
                }
                var character = text.charAt(index);
                if (character == ' ' || character == '\t') {
                    index++;
                    continue;
                }
                if (character == '\n') {
                    lineStart = index + 1;
                    newlines++;
                    index++;
                    continue;
                }
                break;
            }
            for (var count = 0; count < newlines; count++) {
                buffer.append('\n');
            }
            writeIndentation(buffer, indentation);
            var contentStart = lineStart + Math.max(0, minimumIndentation);
            if (contentStart > text.length()) {
                contentStart = text.length();
            }
            var lineEnd = contentStart;
            while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') {
                lineEnd++;
            }
            if (contentStart < lineEnd) {
                buffer.append(text.substring(contentStart, lineEnd));
            }
            if (lineEnd >= text.length()) {
                return;
            }
            index = lineEnd + 1;
        }
    }

    /// Writes one expanded opaque at-rule.
    private static void writeExpandedUnknownAtRule(
            CssUnknownAtRule rule,
            SourceMapBuffer buffer,
            int indentation,
            boolean javaFX
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
        writeExpandedChildren(
                rule,
                buffer,
                indentation,
                compactCommentOnly,
                javaFX
        );
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
            int indentation,
            boolean javaFX
    ) {
        writeIndentation(buffer, indentation);
        // Preserve source line breaks between selector complexes; indent the
        // continuation at the same depth as the first complex (dart-sass).
        int indentSpaces = indentation * 2;
        buffer.forSpan(rule.selector().span(), () ->
                buffer.append(rule.selector().value().toCssString(false, indentSpaces)));
        buffer.append(" {");
        writeExpandedChildren(rule, buffer, indentation, javaFX);
        buffer.append('}');
    }

    /// Writes one expanded media rule.
    private static void writeExpandedMediaRule(
            CssMediaRule mediaRule,
            SourceMapBuffer buffer,
            int indentation,
            boolean javaFX
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(mediaRule.span(), () -> {
            buffer.append("@media ");
            appendMediaQueries(mediaRule, buffer, false);
        });
        buffer.append(" {");
        writeExpandedChildren(mediaRule, buffer, indentation, javaFX);
        buffer.append('}');
    }

    /// Writes one expanded supports rule.
    private static void writeExpandedSupportsRule(
            CssSupportsRule supportsRule,
            SourceMapBuffer buffer,
            int indentation,
            boolean javaFX
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(supportsRule.span(), () -> {
            buffer.append("@supports ");
            buffer.append(supportsRule.condition());
        });
        buffer.append(" {");
        writeExpandedChildren(supportsRule, buffer, indentation, javaFX);
        buffer.append('}');
    }

    /// Writes one expanded font-face rule.
    private static void writeExpandedFontFace(
            CssFontFace fontFace,
            SourceMapBuffer buffer,
            int indentation,
            boolean javaFX
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(fontFace.span(), () -> buffer.append("@font-face {"));
        // Bubbled empty font-face with only a comment uses compact spacing
        // ({@code @font-face { /**/ }}), matching dart-sass.
        writeExpandedChildren(fontFace, buffer, indentation, true, javaFX);
        buffer.append('}');
    }

    /// Writes the braced children of a parent node using expanded layout.
    private static void writeExpandedChildren(
            CssParentNode parent,
            SourceMapBuffer buffer,
            int indentation,
            boolean javaFX
    ) {
        writeExpandedChildren(parent, buffer, indentation, false, javaFX);
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
            boolean compactCommentOnly,
            boolean javaFX
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
        @Nullable CssNode prePrevious = null;
        for (var child : visible) {
            if (previous != null && requiresSemicolon(previous)) {
                buffer.append(';');
            }
            if (previous != null && isTrailingComment(child, previous)) {
                buffer.append(' ');
                writeExpandedComment((CssComment) child, buffer, indentation + 1, false);
            } else if (previous == null && isTrailingComment(child, parent)) {
                // First visible child is a trailing comment of the parent open brace.
                buffer.append(' ');
                writeExpandedComment((CssComment) child, buffer, indentation + 1, false);
            } else {
                buffer.append('\n');
                writeExpandedNode(child, buffer, indentation + 1, javaFX);
            }
            prePrevious = previous;
            previous = child;
        }
        if (previous != null) {
            if (requiresSemicolon(previous)) {
                buffer.append(';');
            }
            // Sole trailing comment of the parent stays on the opening line
            // ({@code a { /**/ }}), matching dart-sass.
            if (prePrevious == null && isTrailingComment(previous, parent)) {
                buffer.append(' ');
            } else {
                buffer.append('\n');
                writeIndentation(buffer, indentation);
            }
        }
    }

    /// Returns whether {@code node} is a trailing comment after {@code previous}.
    ///
    /// Matches dart-sass {@code _isTrailingComment}: same-line sibling comments
    /// are written after the preceding semicolon without a line break.
    ///
    /// @param node     the candidate comment
    /// @param previous the preceding sibling, or the parent when {@code node} is first
    /// @return whether expanded layout should keep the comment on the same line
    private static boolean isTrailingComment(CssNode node, CssNode previous) {
        if (!(node instanceof CssComment)) {
            return false;
        }
        var nodeSpan = node.span();
        var previousSpan = previous.span();
        @Nullable var nodeUrl = nodeSpan.url();
        @Nullable var previousUrl = previousSpan.url();
        if (!Objects.equals(nodeUrl, previousUrl)) {
            return false;
        }
        if (!spanContains(previousSpan, nodeSpan)) {
            return nodeSpan.start().line() == previousSpan.end().line();
        }
        // Comment nested inside the previous node's source range (common for the
        // first child after a parent open brace): compare against the line of the
        // last '{' before the comment.
        int searchFrom = nodeSpan.start().offset() - previousSpan.start().offset() - 1;
        if (searchFrom < 0) {
            return false;
        }
        String previousText = previousSpan.text();
        int endOffset = Math.min(searchFrom, previousText.length() - 1);
        int brace = previousText.lastIndexOf('{', endOffset);
        if (brace < 0) {
            brace = 0;
        }
        // Approximate the line of that brace within previousText.
        int braceLine = previousSpan.start().line();
        for (var index = 0; index < brace && index < previousText.length(); index++) {
            if (previousText.charAt(index) == '\n') {
                braceLine++;
            }
        }
        return nodeSpan.start().line() == braceLine;
    }

    /// Returns whether {@code outer} fully covers {@code inner} by offset.
    private static boolean spanContains(org.glavo.sassfx.SourceSpan outer, org.glavo.sassfx.SourceSpan inner) {
        return outer.start().offset() <= inner.start().offset()
                && inner.end().offset() <= outer.end().offset();
    }

    /// Writes one expanded declaration.
    private static void writeExpandedDeclaration(
            CssDeclaration declaration,
            SourceMapBuffer buffer,
            int indentation,
            boolean javaFX
    ) {
        writeIndentation(buffer, indentation);
        buffer.forSpan(declaration.name().span(), () ->
                buffer.append(declaration.name().value()));
        buffer.append(':');
        if (declaration.parsedAsSassScript()) {
            buffer.append(' ');
        }
        appendDeclarationValue(
                declaration,
                buffer,
                indentation,
                false,
                javaFX
        );
    }

    /// Writes all visible top-level nodes using compressed layout.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param buffer     the output and source-map buffer
    /// @param javaFX     whether JavaFX-required token separators are emitted
    private static void writeCompressedStylesheet(
            CssStylesheet stylesheet,
            SourceMapBuffer buffer,
            boolean javaFX
    ) {
        for (var child : stylesheet.children()) {
            if (!isCompressedVisible(child)) {
                continue;
            }
            writeCompressedNode(child, buffer, javaFX);
        }
    }

    /// Writes one visible CSS node using compressed layout.
    ///
    /// @param node   the visible node
    /// @param buffer the output and source-map buffer
    /// @param javaFX whether JavaFX-required token separators are emitted
    private static void writeCompressedNode(
            CssNode node,
            SourceMapBuffer buffer,
            boolean javaFX
    ) {
        if (node instanceof CssImport importRule) {
            buffer.forSpan(importRule.span(), () ->
                    buffer.append("@import ").append(importRule.argument()).append(';'));
        } else if (node instanceof CssMediaRule mediaRule) {
            buffer.forSpan(mediaRule.span(), () -> {
                buffer.append("@media");
                if (javaFX || mediaRule.queries().get(0).startsWithIdentifier()) {
                    buffer.append(' ');
                }
                appendMediaQueries(mediaRule, buffer, true);
            });
            buffer.append('{');
            writeCompressedChildren(mediaRule, buffer, javaFX);
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
            writeCompressedChildren(supportsRule, buffer, javaFX);
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
                writeCompressedChildren(unknownAtRule, buffer, javaFX);
                buffer.append('}');
            } else {
                buffer.append(';');
            }
        } else if (node instanceof CssFontFace fontFace) {
            buffer.forSpan(fontFace.span(), () -> buffer.append("@font-face{"));
            writeCompressedChildren(fontFace, buffer, javaFX);
            if (javaFX) {
                // OpenJFX's font-face parser needs a final descriptor separator
                // before the closing brace or it consumes the following rule.
                buffer.append(';');
            }
            buffer.append('}');
        } else if (node instanceof CssStyleRule rule) {
            buffer.forSpan(rule.selector().span(), () ->
                    buffer.append(rule.selector().value().toCssString(false)));
            buffer.append('{');
            writeCompressedChildren(rule, buffer, javaFX);
            buffer.append('}');
        } else if (node instanceof CssDeclaration declaration) {
            writeCompressedDeclaration(declaration, buffer, javaFX);
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
    ///
    /// @param parent the parent whose visible children are written
    /// @param buffer the output and source-map buffer
    /// @param javaFX whether JavaFX-required token separators are emitted
    private static void writeCompressedChildren(
            CssParentNode parent,
            SourceMapBuffer buffer,
            boolean javaFX
    ) {
        boolean precedingDeclaration = false;
        for (var child : parent.children()) {
            if (!isCompressedVisible(child)) {
                continue;
            }
            if (precedingDeclaration) {
                buffer.append(';');
            }
            writeCompressedNode(child, buffer, javaFX);
            precedingDeclaration = child instanceof CssDeclaration;
        }
    }

    /// Writes one declaration using compressed layout.
    private static void writeCompressedDeclaration(
            CssDeclaration declaration,
            SourceMapBuffer buffer,
            boolean javaFX
    ) {
        buffer.forSpan(declaration.name().span(), () ->
                buffer.append(declaration.name().value()));
        buffer.append(':');
        appendDeclarationValue(declaration, buffer, 0, true, javaFX);
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
    ///
    /// @param declaration the declaration
    /// @param buffer      the serialization buffer
    /// @param indentation expanded indentation depth of the declaration
    /// @param compressed  whether compressed layout is active
    /// @param javaFX      whether JavaFX-specific legacy syntax is restored
    private static void appendDeclarationValue(
            CssDeclaration declaration,
            SourceMapBuffer buffer,
            int indentation,
            boolean compressed,
            boolean javaFX
    ) {
        if (!declaration.parsedAsSassScript()) {
            // Raw CSS custom-property / declaration values keep author whitespace;
            // expanded mode reindents multi-line values, and values that end only
            // in newlines become a single trailing space (dart-sass).
            buffer.forSpan(declaration.value().span(), () ->
                    writeRawDeclarationValue(declaration, buffer, indentation, compressed));
            return;
        }
        try {
            var value = declaration.value().value();
            @Nullable var legacyGradient = javaFX
                    ? JavaFXLegacyGradient.serialize(value)
                    : null;
            var css = legacyGradient != null
                    ? legacyGradient
                    : value.toCssString(true, compressed);
            buffer.forSpan(declaration.value().span(), () -> buffer.append(css));
        } catch (SassValueException cause) {
            throw new CssSerializeException(
                    Objects.requireNonNull(cause.getMessage(), "value failure message"),
                    declaration.value().span(),
                    cause
            );
        }
    }

    /// Appends a raw (non-SassScript) declaration value.
    ///
    /// @param declaration the declaration whose value is raw text
    /// @param buffer      the serialization buffer
    /// @param indentation expanded indentation depth of the declaration
    /// @param compressed  whether compressed folding should be used
    private static void writeRawDeclarationValue(
            CssDeclaration declaration,
            SourceMapBuffer buffer,
            int indentation,
            boolean compressed
    ) {
        String value = rawValueText(declaration);
        if (compressed) {
            writeFoldedValue(value, buffer);
            return;
        }
        writeReindentedValue(value, declaration, buffer, indentation);
    }

    /// Writes a raw value with newlines folded to spaces (compressed CSS).
    private static void writeFoldedValue(String value, SourceMapBuffer buffer) {
        for (var index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\n') {
                buffer.append(' ');
                while (index + 1 < value.length()
                        && isAsciiWhitespace(value.charAt(index + 1))) {
                    index++;
                }
            } else if (ch != '\r') {
                buffer.append(ch);
            }
        }
    }

    /// Writes a raw value with multi-line indentation normalized for expanded CSS.
    private static void writeReindentedValue(
            String value,
            CssDeclaration declaration,
            SourceMapBuffer buffer,
            int indentation
    ) {
        @Nullable Integer minimum = minimumIndentation(value);
        if (minimum == null) {
            buffer.append(value);
            return;
        }
        if (minimum < 0) {
            // Newlines with no following indented content: collapse to one space.
            buffer.append(trimAsciiRightPreserveEscaped(value));
            buffer.append(' ');
            return;
        }
        int nameColumn = declaration.name().span().start().column();
        int effectiveMinimum = nameColumn > 0
                ? Math.min(minimum, nameColumn)
                : minimum;
        writeWithIndent(value, effectiveMinimum, buffer, indentation);
    }

    /// Returns the indentation of the least-indented non-empty line after the first.
    ///
    /// @param text the raw declaration value
    /// @return {@code null} when there is no newline; {@code -1} when there are
    /// newlines but no indented non-empty line; otherwise the column count
    private static @Nullable Integer minimumIndentation(String text) {
        int index = 0;
        while (index < text.length() && text.charAt(index) != '\n') {
            index++;
        }
        if (index >= text.length()) {
            return null;
        }
        // Ends on a newline with nothing after the first line.
        if (index == text.length() - 1 || onlyTrailingNewlines(text, index)) {
            return -1;
        }
        index++; // past first \n
        @Nullable Integer min = null;
        while (index < text.length()) {
            int column = 0;
            while (index < text.length()) {
                char ch = text.charAt(index);
                if (ch != ' ' && ch != '\t') {
                    break;
                }
                column++;
                index++;
            }
            if (index >= text.length()) {
                break;
            }
            if (text.charAt(index) == '\n') {
                index++;
                continue;
            }
            min = min == null ? column : Math.min(min, column);
            while (index < text.length() && text.charAt(index) != '\n') {
                index++;
            }
            if (index < text.length()) {
                index++;
            }
        }
        return min == null ? -1 : min;
    }

    /// Returns whether {@code text} from {@code fromNewline} is only newlines.
    private static boolean onlyTrailingNewlines(String text, int fromNewline) {
        for (var index = fromNewline; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch != '\n' && ch != '\r') {
                return false;
            }
        }
        return true;
    }

    /// Writes multi-line text, replacing the minimum source indent with serializer indent.
    ///
    /// Matches dart-sass {@code _writeWithIndent}: the first line is written as-is;
    /// later non-empty lines are reindented to {@code currentIndentation}, and
    /// trailing whitespace-only tails become a single space.
    private static void writeWithIndent(
            String text,
            int minimumIndentation,
            SourceMapBuffer buffer,
            int currentIndentation
    ) {
        int index = 0;
        // First line as-is until newline.
        while (index < text.length()) {
            char ch = text.charAt(index++);
            if (ch == '\n') {
                break;
            }
            if (ch != '\r') {
                buffer.append(ch);
            }
        }
        while (true) {
            // After a newline (or at end). Scan for the next non-empty line.
            int lineStart = index;
            int newlines = 1;
            while (true) {
                if (index >= text.length()) {
                    buffer.append(' ');
                    return;
                }
                char ch = text.charAt(index);
                if (ch == ' ' || ch == '\t') {
                    index++;
                    continue;
                }
                if (ch == '\n') {
                    index++;
                    lineStart = index;
                    newlines++;
                    continue;
                }
                if (ch == '\r') {
                    index++;
                    if (index < text.length() && text.charAt(index) == '\n') {
                        index++;
                    }
                    lineStart = index;
                    newlines++;
                    continue;
                }
                break;
            }
            for (var n = 0; n < newlines; n++) {
                buffer.append('\n');
            }
            // Same depth as the declaration name line (dart-sass _writeIndentation).
            writeIndentation(buffer, currentIndentation);
            // Keep relative indent beyond the shared minimum (dart-sass
            // substring(lineStart + minimumIndentation)). Only clamp when a line
            // is less indented than the recorded minimum.
            int contentStart = lineStart + minimumIndentation;
            if (contentStart > text.length()) {
                contentStart = text.length();
            }
            if (contentStart > index) {
                // Line was less indented than the minimum; start at non-whitespace.
                contentStart = index;
            }
            index = contentStart;
            boolean endedWithNewline = false;
            while (index < text.length()) {
                char ch = text.charAt(index);
                if (ch == '\n') {
                    index++;
                    endedWithNewline = true;
                    break;
                }
                if (ch == '\r') {
                    index++;
                    if (index < text.length() && text.charAt(index) == '\n') {
                        index++;
                    }
                    endedWithNewline = true;
                    break;
                }
                buffer.append(ch);
                index++;
            }
            // Content line ended without a newline: done (no trailing space).
            // Content line ended with a newline: continue so a trailing-only
            // newline collapses to a single space (dart-sass _writeWithIndent).
            if (!endedWithNewline) {
                return;
            }
        }
    }

    /// Trims trailing ASCII whitespace while keeping a trailing backslash escape.
    private static String trimAsciiRightPreserveEscaped(String value) {
        int end = value.length();
        while (end > 0) {
            char ch = value.charAt(end - 1);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f') {
                // Keep a space that is escaped by a preceding odd number of backslashes.
                if (ch == ' ' || ch == '\t') {
                    int backslashes = 0;
                    for (var i = end - 2; i >= 0 && value.charAt(i) == '\\'; i--) {
                        backslashes++;
                    }
                    if ((backslashes & 1) == 1) {
                        break;
                    }
                }
                end--;
                continue;
            }
            break;
        }
        return value.substring(0, end);
    }

    /// Returns whether a character is ASCII whitespace.
    private static boolean isAsciiWhitespace(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f';
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
