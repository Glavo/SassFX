// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.bss;

import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.ast.selector.Combinator;
import org.glavo.sassfx.internal.ast.selector.ComplexSelector;
import org.glavo.sassfx.internal.ast.selector.ComplexSelectorComponent;
import org.glavo.sassfx.internal.ast.selector.SelectorList;
import org.glavo.sassfx.internal.css.CssComment;
import org.glavo.sassfx.internal.css.CssDeclaration;
import org.glavo.sassfx.internal.css.CssFontFace;
import org.glavo.sassfx.internal.css.CssImport;
import org.glavo.sassfx.internal.css.CssMediaRule;
import org.glavo.sassfx.internal.css.CssNode;
import org.glavo.sassfx.internal.css.CssSupportsRule;
import org.glavo.sassfx.internal.css.CssStyleRule;
import org.glavo.sassfx.internal.css.CssStylesheet;
import org.glavo.sassfx.internal.css.CssUnknownAtRule;
import org.glavo.sassfx.internal.css.JavaFXCssImport;
import org.glavo.sassfx.internal.css.JavaFXCssLexer;
import org.glavo.sassfx.internal.css.JavaFXLegacyGradient;
import org.glavo.sassfx.internal.css.JavaFXValueFunction;
import org.glavo.sassfx.internal.css.JavaFXMediaQuery;
import org.glavo.sassfx.internal.css.JavaFXMediaQueryValidator;
import org.glavo.sassfx.internal.css.JavaFXSimpleSelector;
import org.glavo.sassfx.internal.value.ListSeparator;
import org.glavo.sassfx.internal.value.SassBoolean;
import org.glavo.sassfx.internal.value.SassColor;
import org.glavo.sassfx.internal.value.SassList;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassString;
import org.glavo.sassfx.internal.value.SassValue;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.glavo.sassfx.JavaFXFeature.MULTIPLE_RULES_PER_MEDIA_QUERY;

/// Converts the supported CSS IR subset into JavaFX binary stylesheet bytes.
///
/// The serializer implements the BSS v5 through v9 wire framing directly and does
/// not load or link JavaFX classes. Unsupported selectors and declaration
/// values fail with a source-associated [BssSerializeException] instead of
/// producing a binary stylesheet with different semantics.
@ApiStatus.Internal
@NotNullByDefault
public final class BssSerializer {
    /// Contains the JavaFX 8 binary stylesheet version.
    private static final int VERSION_5 = 5;

    /// Contains the first BSS version with per-rule media framing.
    private static final int VERSION_7 = 7;

    /// The BSS version introduced by JavaFX 27.
    private static final int VERSION_9 = 9;

    /// Contains the first BSS version with stylesheet-import framing.
    /// Contains the maximum count and string-table index representable by BSS.
    private static final int MAXIMUM_SHORT_VALUE = Short.MAX_VALUE;

    /// Contains the BSS selector tag for a simple selector.
    private static final byte SIMPLE_SELECTOR = 1;

    /// Contains the BSS selector tag for a compound selector.
    private static final byte COMPOUND_SELECTOR = 2;

    /// Contains the BSS parsed-value tag for a nested parsed value.
    private static final byte NESTED_VALUE = 1;

    /// Contains the BSS parsed-value tag for an array of parsed values.
    private static final byte VALUE_ARRAY = 2;

    /// Contains the BSS parsed-value tag for an array of parsed-value arrays.
    private static final byte ARRAY_OF_VALUE_ARRAY = 3;

    /// Contains the BSS parsed-value tag for a string.
    private static final byte STRING_VALUE = 4;

    /// Contains the BSS parsed-value tag for an RGBA color.
    private static final byte COLOR_VALUE = 5;

    /// Contains the BSS parsed-value tag for a raw boolean.
    private static final byte BOOLEAN_VALUE = 7;

    /// Contains the BSS parsed-value tag for a size with a unit.
    private static final byte SIZE_VALUE = 9;

    /// Contains the BSS ordinal for a child combinator.
    private static final byte CHILD_COMBINATOR = 0;

    /// Contains the BSS ordinal for a descendant combinator.
    private static final byte DESCENDANT_COMBINATOR = 1;

    /// Contains JavaFX's author stylesheet origin spelling.
    private static final String AUTHOR_ORIGIN = "AUTHOR";

    /// Contains the JavaFX converter name for ordinary numeric CSS values.
    private static final String SIZE_CONVERTER = "javafx.css.converter.SizeConverter";

    /// Contains the JavaFX converter name for font shorthand values.
    private static final String FONT_CONVERTER = "javafx.css.converter.FontConverter";

    /// Contains the JavaFX converter name for font-size values.
    private static final String FONT_SIZE_CONVERTER =
            "javafx.css.converter.FontConverter$FontSizeConverter";

    /// Contains the JavaFX converter name for font style values.
    private static final String FONT_STYLE_CONVERTER =
            "javafx.css.converter.FontConverter$FontStyleConverter";

    /// Contains the JavaFX converter name for font weight values.
    private static final String FONT_WEIGHT_CONVERTER =
            "javafx.css.converter.FontConverter$FontWeightConverter";

    /// Contains the JavaFX converter name for scalar enum values.
    private static final String ENUM_CONVERTER = "javafx.css.converter.EnumConverter";

    /// Contains the JavaFX converter name for sequences of sizes.
    private static final String SIZE_SEQUENCE_CONVERTER =
            "javafx.css.converter.SizeConverter$SequenceConverter";

    /// Contains the JavaFX converter name for duration values.
    private static final String DURATION_CONVERTER =
            "javafx.css.converter.DurationConverter";

    /// Contains the JavaFX converter name for font-family values.
    private static final String STRING_CONVERTER = "javafx.css.converter.StringConverter";

    /// Contains the JavaFX converter name for one URL source.
    private static final String URL_CONVERTER = "javafx.css.converter.URLConverter";

    /// Contains the JavaFX converter name for a sequence of URL sources.
    private static final String URL_SEQUENCE_CONVERTER =
            "javafx.css.converter.URLConverter$SequenceConverter";

    /// Contains the JavaFX converter name for layered background positions.
    private static final String LAYERED_BACKGROUND_POSITION_CONVERTER =
            "com.sun.javafx.scene.layout.region.LayeredBackgroundPositionConverter";

    /// Contains the JavaFX converter name for one background position.
    private static final String BACKGROUND_POSITION_CONVERTER =
            "com.sun.javafx.scene.layout.region.BackgroundPositionConverter";

    /// Contains the JavaFX converter name for layered background repeat modes.
    private static final String REPEAT_STRUCT_CONVERTER =
            "com.sun.javafx.scene.layout.region.RepeatStructConverter";

    /// Contains the JavaFX enum class for one background-repeat axis.
    private static final String BACKGROUND_REPEAT_ENUM_CLASS =
            "javafx.scene.layout.BackgroundRepeat";

    /// Contains the JavaFX converter name for layered background sizes.
    private static final String LAYERED_BACKGROUND_SIZE_CONVERTER =
            "com.sun.javafx.scene.layout.region.LayeredBackgroundSizeConverter";

    /// Contains the JavaFX converter name for one background size.
    private static final String BACKGROUND_SIZE_CONVERTER =
            "com.sun.javafx.scene.layout.region.BackgroundSizeConverter";

    /// Contains JavaFX's zero-percent position offset.
    private static final SassNumber ZERO_PERCENT = SassNumber.of(0.0, "%");

    /// Contains JavaFX's centered fifty-percent position offset.
    private static final SassNumber FIFTY_PERCENT = SassNumber.of(50.0, "%");

    /// Contains JavaFX's far-edge hundred-percent position offset.
    private static final SassNumber ONE_HUNDRED_PERCENT = SassNumber.of(100.0, "%");

    /// Contains the BSS parsed-value tag for a null value.
    private static final byte NULL_VALUE = 0;

    /// Contains the JavaFX converter name for boolean values.
    private static final String BOOLEAN_CONVERTER = "javafx.css.converter.BooleanConverter";

    /// Contains the JavaFX converter name for four-sided inset values.
    private static final String INSETS_CONVERTER = "javafx.css.converter.InsetsConverter";

    /// Contains the JavaFX converter name for a sequence of paint values.
    private static final String PAINT_SEQUENCE_CONVERTER =
            "javafx.css.converter.PaintConverter$SequenceConverter";

    /// Contains the JavaFX converter name for one linear gradient paint.
    private static final String LINEAR_GRADIENT_CONVERTER =
            "javafx.css.converter.PaintConverter$LinearGradientConverter";

    /// Contains the JavaFX converter name for one radial gradient paint.
    private static final String RADIAL_GRADIENT_CONVERTER =
            "javafx.css.converter.PaintConverter$RadialGradientConverter";

    /// Contains the JavaFX converter name for one image-pattern paint.
    private static final String IMAGE_PATTERN_CONVERTER =
            "javafx.css.converter.PaintConverter$ImagePatternConverter";

    /// Contains the JavaFX converter name for one repeating-image-pattern paint.
    private static final String REPEATING_IMAGE_PATTERN_CONVERTER =
            "javafx.css.converter.PaintConverter$RepeatingImagePatternConverter";

    /// Contains the JavaFX converter name for one gradient color stop.
    private static final String STOP_CONVERTER = "javafx.css.converter.StopConverter";

    /// Contains the JavaFX converter name for `derive(...)` colors.
    private static final String DERIVE_COLOR_CONVERTER =
            "javafx.css.converter.DeriveColorConverter";

    /// Contains the JavaFX converter name for `ladder(...)` colors.
    private static final String LADDER_CONVERTER = "javafx.css.converter.LadderConverter";

    /// Contains the JavaFX enum class used for gradient cycle methods.
    private static final String CYCLE_METHOD_ENUM_CLASS = "javafx.scene.paint.CycleMethod";

    /// Contains the JavaFX enum class used for shadow blur algorithms.
    private static final String BLUR_TYPE_ENUM_CLASS = "javafx.scene.effect.BlurType";

    /// Contains the JavaFX converter name for a sequence of inset values.
    private static final String INSETS_SEQUENCE_CONVERTER =
            "javafx.css.converter.InsetsConverter$SequenceConverter";


    /// Contains the JavaFX converter name for a sequence of border-image slices.
    private static final String SLICE_SEQUENCE_CONVERTER =
            "com.sun.javafx.scene.layout.region.SliceSequenceConverter";

    /// Contains the JavaFX converter name for one border-image slice layer.
    private static final String BORDER_IMAGE_SLICE_CONVERTER =
            "com.sun.javafx.scene.layout.region.BorderImageSliceConverter";

    /// Contains the JavaFX converter name for a sequence of border-image widths.
    private static final String BORDER_IMAGE_WIDTHS_SEQUENCE_CONVERTER =
            "com.sun.javafx.scene.layout.region.BorderImageWidthsSequenceConverter";

    /// Contains the JavaFX converter name for one border-image width layer.
    private static final String BORDER_IMAGE_WIDTH_CONVERTER =
            "com.sun.javafx.scene.layout.region.BorderImageWidthConverter";
    /// Contains the JavaFX converter name for layered corner-radius values.
    private static final String CORNER_RADII_CONVERTER =
            "com.sun.javafx.scene.layout.region.CornerRadiiConverter";

    /// Contains the JavaFX converter name for layered four-sided border paints.
    private static final String LAYERED_BORDER_PAINT_CONVERTER =
            "com.sun.javafx.scene.layout.region.LayeredBorderPaintConverter";

    /// Contains the JavaFX converter name for one four-sided border paint layer.
    private static final String STROKE_BORDER_PAINT_CONVERTER =
            "com.sun.javafx.scene.layout.region.StrokeBorderPaintConverter";

    /// Contains the JavaFX converter name for one four-sided border-width layer.
    private static final String MARGINS_CONVERTER =
            "com.sun.javafx.scene.layout.region.Margins$Converter";

    /// Contains the JavaFX converter name for layered border-style values.
    private static final String LAYERED_BORDER_STYLE_CONVERTER =
            "com.sun.javafx.scene.layout.region.LayeredBorderStyleConverter";

    /// Contains the JavaFX converter name for one four-sided border-style sequence.
    private static final String BORDER_STROKE_STYLE_SEQUENCE_CONVERTER =
            "com.sun.javafx.scene.layout.region.BorderStrokeStyleSequenceConverter";

    /// Contains the JavaFX converter name for one border stroke style.
    private static final String BORDER_STYLE_CONVERTER =
            "com.sun.javafx.scene.layout.region.BorderStyleConverter";

    /// Contains the JavaFX enum class for border stroke types.
    private static final String STROKE_TYPE_ENUM_CLASS = "javafx.scene.shape.StrokeType";

    /// Contains the JavaFX enum class for border stroke line joins.
    private static final String STROKE_LINE_JOIN_ENUM_CLASS = "javafx.scene.shape.StrokeLineJoin";

    /// Contains the JavaFX enum class for border stroke line caps.
    private static final String STROKE_LINE_CAP_ENUM_CLASS = "javafx.scene.shape.StrokeLineCap";

    /// Contains the JavaFX converter name for a sequence of border-width layers.
    private static final String MARGINS_SEQUENCE_CONVERTER =
            "com.sun.javafx.scene.layout.region.Margins$SequenceConverter";

    /// Prevents instantiation.
    private BssSerializer() {
    }

    /// Serializes a stylesheet for the supplied BSS target.
    ///
    /// The returned buffer is read-only, has position zero, and contains one
    /// complete BSS document with no trailing capacity.
    ///
    /// @param stylesheet the evaluated CSS IR root
    /// @param target     the binary target version
    /// @param importResolver the compilation-scoped retained-import resolver
    /// @return a read-only buffer containing one BSS document
    /// @throws BssSerializeException if CSS IR cannot be represented by the
    ///                               supported JavaFX BSS subset
    public static @Unmodifiable ByteBuffer serialize(
            CssStylesheet stylesheet,
            BssTarget target,
            BssImportResolver importResolver
    ) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(importResolver, "importResolver");

        var activeUrls = new LinkedHashSet<URI>();
        if (stylesheet.span().url() != null) {
            activeUrls.add(stylesheet.span().url());
        }
        var content = collectStylesheet(
                stylesheet,
                target.javaFXTarget(),
                importResolver,
                activeUrls,
                true
        );
        try {
            var strings = new StringStore(target.bssVersion(), stylesheet.span());
            byte[] body = writeBody(
                    target.bssVersion(),
                    content,
                    strings,
                    stylesheet.span()
            );
            var document = new ByteArrayOutputStream(body.length + 128);
            try (var output = new DataOutputStream(document)) {
                output.writeShort(target.bssVersion());
                strings.writeBinary(output);
                output.write(body);
                output.flush();
            }
            return ByteBuffer.wrap(document.toByteArray()).asReadOnlyBuffer();
        } catch (IOException failure) {
            throw new BssSerializeException(
                    "Unable to encode JavaFX BSS output.",
                    stylesheet.span(),
                    failure
            );
        }
    }

    /// Collects top-level style rules and font faces for BSS encoding.
    ///
    /// JavaFX stores font faces separately from style rules, and its CSS parser
    /// accepts them only before ordinary rules. BSS preserves that same safe
    /// source-order boundary.
    ///
    /// @param stylesheet    the evaluated CSS root
    /// @param compatibility the selected JavaFX release
    /// @param importResolver the compilation-scoped import resolver
    /// @param activeUrls     canonical URLs active in the current import chain
    /// @param preserveImports whether direct imports are retained as BSS v9 entries
    /// @return binary-ready stylesheet content
    private static BssStylesheet collectStylesheet(
            CssStylesheet stylesheet,
            JavaFXTarget compatibility,
            BssImportResolver importResolver,
            LinkedHashSet<URI> activeUrls,
            boolean preserveImports
    ) {
        var imports = new ArrayList<BssImport>();
        var rules = new ArrayList<BssRule>();
        var fontFaces = new ArrayList<BssFontFace>();
        var properties = new JavaFXPropertyRegistry();
        var sawStyleRule = false;
        for (var child : stylesheet.children()) {
            if (child instanceof CssComment || child.isInvisible()) {
                continue;
            }
            if (child instanceof CssImport cssImport) {
                var parsed = JavaFXCssImport.parse(cssImport, compatibility);
                BssImportResolver.ResolvedImport resolved;
                try {
                    resolved = importResolver.resolve(
                            parsed.resource(),
                            cssImport.span().url(),
                            cssImport.span()
                    );
                } catch (IOException failure) {
                    throw new BssSerializeException(
                            "Unable to load JavaFX CSS import \"" + parsed.resource() + "\".",
                            cssImport.span(),
                            failure
                    );
                }
                if (!activeUrls.add(resolved.canonicalUrl())) {
                    throw new BssSerializeException(
                            "Recursive JavaFX CSS import of \""
                                    + resolved.canonicalUrl() + "\".",
                            cssImport.span(),
                            null
                    );
                }
                BssStylesheet imported;
                try {
                    imported = collectStylesheet(
                            resolved.stylesheet(),
                            compatibility,
                            importResolver,
                            activeUrls,
                            false
                    );
                } finally {
                    activeUrls.remove(resolved.canonicalUrl());
                }
                if (preserveImports && compatibility.bssVersion() >= VERSION_9) {
                    imports.add(new BssImport(parsed.conditions(), imported, cssImport.span()));
                } else {
                    for (var importedRule : imported.rules()) {
                        rules.add(withImportConditions(importedRule, parsed.conditions()));
                    }
                }
            } else if (child instanceof CssStyleRule rule) {
                sawStyleRule = true;
                @Nullable BssRule converted = collectRule(
                        rule,
                        null,
                        properties
                );
                if (converted != null) {
                    rules.add(converted);
                }
            } else if (child instanceof CssFontFace fontFace) {
                if (sawStyleRule) {
                    throw new BssSerializeException(
                            "BSS @font-face rules must precede style rules.",
                            fontFace.span(),
                            null
                    );
                }
                fontFaces.add(collectFontFace(fontFace));
            } else if (child instanceof CssMediaRule mediaRule) {
                sawStyleRule = true;
                collectMediaRule(
                        mediaRule,
                        null,
                        compatibility,
                        rules,
                        properties
                );
            } else if (child instanceof CssSupportsRule supportsRule) {
                throw new BssSerializeException(
                        "BSS output doesn't support @supports rules.",
                        supportsRule.span(),
                        null
                );
            } else if (child instanceof CssUnknownAtRule unknownAtRule) {
                throw unsupportedAtRule(unknownAtRule);
            } else {
                throw unsupported(child, "top-level CSS node");
            }
        }
        return new BssStylesheet(imports, rules, fontFaces);
    }

    /// Adds a flattened import condition as the outer parent of a rule's media chain.
    ///
    /// @param rule       the imported style rule
    /// @param conditions the import condition list
    /// @return the rule with import conditions attached, or the original rule
    private static BssRule withImportConditions(
            BssRule rule,
            JavaFXMediaQuery conditions
    ) {
        if (conditions.alternatives().isEmpty()) {
            return rule;
        }
        return new BssRule(
                rule.selectors(),
                rule.declarations(),
                appendMediaParent(
                        rule.mediaRule(),
                        new BssMediaRule(conditions, null)
                ),
                rule.span()
        );
    }

    /// Appends one outer media rule to an existing immutable parent chain.
    ///
    /// @param current the current chain, or {@code null}
    /// @param outer   the outer rule to append
    /// @return a chain ending in the outer rule
    private static BssMediaRule appendMediaParent(
            @Nullable BssMediaRule current,
            BssMediaRule outer
    ) {
        if (current == null) {
            return outer;
        }
        return new BssMediaRule(
                current.query(),
                appendMediaParent(current.parent(), outer)
        );
    }

    /// Collects rules nested under one JavaFX media rule.
    ///
    /// @param mediaRule     the CSS media rule
    /// @param parent        the enclosing binary media rule, or `null`
    /// @param compatibility the selected JavaFX release
    /// @param rules         the destination rule list
    /// @param properties    the declaration names seen in this source stylesheet
    private static void collectMediaRule(
            CssMediaRule mediaRule,
            @Nullable BssMediaRule parent,
            JavaFXTarget compatibility,
            List<BssRule> rules,
            JavaFXPropertyRegistry properties
    ) {
        if (!compatibility.supports(MULTIPLE_RULES_PER_MEDIA_QUERY)
                && mediaRule.children().stream()
                .filter(child -> !child.isInvisible())
                .filter(CssStyleRule.class::isInstance)
                .skip(1)
                .findAny()
                .isPresent()) {
            throw new BssSerializeException(
                    "JavaFX " + compatibility.version()
                            + " CSS cannot apply multiple style rules"
                            + " within one @media rule.",
                    mediaRule.span(),
                    null
            );
        }
        var queryText = new StringBuilder();
        for (var index = 0; index < mediaRule.queries().size(); index++) {
            if (index != 0) {
                queryText.append(", ");
            }
            queryText.append(mediaRule.queries().get(index).toCssString());
        }
        var binaryMediaRule = new BssMediaRule(
                JavaFXMediaQueryValidator.parse(
                        queryText.toString(),
                        mediaRule.span(),
                        compatibility
                ),
                parent
        );

        for (var child : mediaRule.children()) {
            if (child instanceof CssComment || child.isInvisible()) {
                continue;
            }
            if (child instanceof CssStyleRule rule) {
                @Nullable BssRule converted = collectRule(
                        rule,
                        binaryMediaRule,
                        properties
                );
                if (converted != null) {
                    rules.add(converted);
                }
            } else if (child instanceof CssMediaRule nested) {
                collectMediaRule(
                        nested,
                        binaryMediaRule,
                        compatibility,
                        rules,
                        properties
                );
            } else if (child instanceof CssSupportsRule supportsRule) {
                throw new BssSerializeException(
                        "BSS output doesn't support @supports rules.",
                        supportsRule.span(),
                        null
                );
            } else if (child instanceof CssUnknownAtRule unknownAtRule) {
                throw unsupportedAtRule(unknownAtRule);
            } else {
                throw unsupported(child, "media-rule CSS node");
            }
        }
    }

    /// Collects JavaFX font-face descriptors and sources from one CSS rule.
    ///
    /// @param fontFace the evaluated font-face rule
    /// @return a BSS-ready font-face snapshot
    private static BssFontFace collectFontFace(CssFontFace fontFace) {
        var descriptors = new HashMap<String, String>();
        var sources = new ArrayList<JavaFXFontFaceParser.Source>();
        for (var child : fontFace.children()) {
            if (child instanceof CssComment || child.isInvisible()) {
                continue;
            }
            if (!(child instanceof CssDeclaration declaration)) {
                throw unsupported(child, "font-face CSS node");
            }
            requireDeclarationName(declaration);
            if (!declaration.parsedAsSassScript()) {
                throw new BssSerializeException(
                        "BSS @font-face doesn't support raw CSS descriptor values.",
                        declaration.value().span(),
                        null
                );
            }
            var name = declaration.name().value();
            var value = fontFaceValue(declaration);
            requireTokenizableValue(value, declaration.value().span());
            if (name.equalsIgnoreCase("src")) {
                sources.addAll(JavaFXFontFaceParser.parseSources(
                        value,
                        declaration.value().span()
                ));
            } else {
                descriptors.put(name, value);
            }
        }
        return new BssFontFace(descriptors, sources, fontFace.span());
    }

    /// Requires a declaration name accepted by JavaFX's ASCII identifier lexer.
    ///
    /// @param declaration the declaration whose name is validated
    /// @throws BssSerializeException if JavaFX CSS cannot tokenize the name
    private static void requireDeclarationName(CssDeclaration declaration) {
        if (!JavaFXCssLexer.isIdentifier(declaration.name().value())) {
            throw new BssSerializeException(
                    "JavaFX CSS does not support this declaration name.",
                    declaration.name().span(),
                    null
            );
        }
    }

    /// Returns one JavaFX font-face descriptor value without priority syntax.
    ///
    /// @param declaration the descriptor declaration
    /// @return the canonical CSS descriptor text
    /// @throws BssSerializeException if the value cannot be emitted safely
    private static String fontFaceValue(CssDeclaration declaration) {
        var split = splitImportant(declaration.value().value());
        if (split.important()) {
            throw new BssSerializeException(
                    "BSS @font-face declarations don't support !important.",
                    declaration.value().span(),
                    null
            );
        }
        try {
            return split.value().toCssString();
        } catch (SassValueException failure) {
            throw new BssSerializeException(
                    Objects.requireNonNull(failure.getMessage(), "font-face value failure message"),
                    declaration.value().span(),
                    failure
            );
        }
    }

    /// Collects declarations from one style rule.
    ///
    /// Nested style rules, conditionals, and opaque at-rules are rejected. BSS
    /// encodes only a flat list of selectors and declarations.
    ///
    /// @param rule the CSS style rule
    /// @param mediaRule the enclosing media rule, or `null`
    /// @param properties the declaration names seen in this source stylesheet
    /// @return a binary-ready rule, or {@code null} when comments are its only content
    private static @Nullable BssRule collectRule(
            CssStyleRule rule,
            @Nullable BssMediaRule mediaRule,
            JavaFXPropertyRegistry properties
    ) {
        var declarations = new ArrayList<BssDeclaration>();
        for (var child : rule.children()) {
            if (child instanceof CssComment || child.isInvisible()) {
                continue;
            }
            if (child instanceof CssImport) {
                throw new BssSerializeException(
                        "BSS output doesn't support @import rules.",
                        child.span(),
                        null
                );
            } else if (child instanceof CssDeclaration declaration) {
                declarations.add(properties.register(declaration));
            } else if (child instanceof CssStyleRule nested) {
                throw new BssSerializeException(
                        "BSS output doesn't support nested style rules.",
                        nested.span(),
                        null
                );
            } else if (child instanceof CssMediaRule nestedMediaRule) {
                throw new BssSerializeException(
                        "BSS output doesn't support @media rules.",
                        nestedMediaRule.span(),
                        null
                );
            } else if (child instanceof CssSupportsRule supportsRule) {
                throw new BssSerializeException(
                        "BSS output doesn't support @supports rules.",
                        supportsRule.span(),
                        null
                );
            } else if (child instanceof CssUnknownAtRule unknownAtRule) {
                throw unsupportedAtRule(unknownAtRule);
            } else {
                throw unsupported(child, "nested CSS node");
            }
        }
        if (declarations.isEmpty()) {
            return null;
        }
        return new BssRule(
                rule.selector().value(),
                declarations,
                mediaRule,
                rule.span()
        );
    }

    /// Writes the BSS stylesheet body after all output strings have been collected.
    ///
    /// @param version    the selected BSS version
    /// @param stylesheet the top-level stylesheet content
    /// @param strings    the shared string table
    /// @param span       the root source range
    /// @return the stylesheet body bytes
    /// @throws IOException if an in-memory output stream rejects a write
    private static byte[] writeBody(
            int version,
            BssStylesheet stylesheet,
            StringStore strings,
            SourceSpan span
    ) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            switch (version) {
                case VERSION_9 -> {
                    output.writeInt(stylesheet.imports().size());
                    for (var imported : stylesheet.imports()) {
                        writeMediaQuery(output, imported.conditions(), strings);
                        var child = writeBody(
                                version,
                                imported.stylesheet(),
                                strings,
                                imported.span()
                        );
                        output.writeInt(child.length);
                        output.write(child);
                    }
                }
                case VERSION_5, 6, VERSION_7, 8 -> {
                    if (!stylesheet.imports().isEmpty()) {
                        throw new BssSerializeException(
                                "BSS version " + version + " cannot encode stylesheet imports.",
                                span,
                                null
                        );
                    }
                }
                default -> throw new BssSerializeException(
                        "Unsupported JavaFX BSS version " + version + ".",
                        span,
                        null
                );
            }

            output.writeShort(strings.add(AUTHOR_ORIGIN));
            writeShortCount(output, stylesheet.rules().size(), span, "style rules");
            for (var rule : stylesheet.rules()) {
                writeRule(output, version, rule, strings);
            }
            writeShortCount(output, stylesheet.fontFaces().size(), span, "font faces");
            for (var fontFace : stylesheet.fontFaces()) {
                writeFontFace(output, fontFace, strings);
            }
            output.flush();
        }
        return bytes.toByteArray();
    }

    /// Writes one JavaFX BSS font-face payload.
    ///
    /// @param output   the stylesheet output stream
    /// @param fontFace the BSS-ready font-face snapshot
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeFontFace(
            DataOutputStream output,
            BssFontFace fontFace,
            StringStore strings
    ) throws IOException {
        writeShortCount(
                output,
                fontFace.descriptors().size(),
                fontFace.span(),
                "font-face descriptors"
        );
        for (var descriptor : fontFace.descriptors().entrySet()) {
            output.writeInt(strings.add(descriptor.getKey()));
            output.writeInt(strings.add(descriptor.getValue()));
        }
        writeShortCount(
                output,
                fontFace.sources().size(),
                fontFace.span(),
                "font-face sources"
        );
        for (var source : fontFace.sources()) {
            output.writeInt(strings.add(source.type().name()));
            output.writeInt(strings.add(source.source()));
            output.writeInt(strings.add(source.format()));
        }
    }

    /// Writes one BSS style rule.
    ///
    /// @param output  the stylesheet output stream
    /// @param version the selected BSS version
    /// @param rule    the rule to write
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeRule(
            DataOutputStream output,
            int version,
            BssRule rule,
            StringStore strings
    ) throws IOException {
        if (version >= VERSION_7) {
            output.writeBoolean(rule.mediaRule() != null);
            if (rule.mediaRule() != null) {
                writeMediaRule(output, rule.mediaRule(), strings);
            }
        }

        var selectors = rule.selectors().components();
        writeShortCount(output, selectors.size(), rule.span(), "selectors");
        for (var selector : selectors) {
            writeComplexSelector(output, selector, strings);
        }

        var declarationBytes = new ByteArrayOutputStream();
        try (var declarations = new DataOutputStream(declarationBytes)) {
            writeShortCount(
                    declarations,
                    rule.declarations().size(),
                    rule.span(),
                    "declarations"
            );
            for (var declaration : rule.declarations()) {
                writeDeclaration(declarations, declaration, strings);
            }
            declarations.flush();
        }
        output.writeInt(declarationBytes.size());
        declarationBytes.writeTo(output);
    }

    /// Writes one media rule and its optional parent chain.
    ///
    /// @param output    the stylesheet output stream
    /// @param mediaRule the media rule to encode
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeMediaRule(
            DataOutputStream output,
            BssMediaRule mediaRule,
            StringStore strings
    ) throws IOException {
        var alternatives = mediaRule.query().alternatives();
        output.writeInt(alternatives.size());
        for (var expression : alternatives) {
            writeMediaExpression(output, expression, strings);
        }
        output.writeBoolean(mediaRule.parent() != null);
        if (mediaRule.parent() != null) {
            writeMediaRule(output, mediaRule.parent(), strings);
        }
    }

    /// Writes an import media-query list without a parent-rule marker.
    ///
    /// @param output  the stylesheet output stream
    /// @param query   the import condition list
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeMediaQuery(
            DataOutputStream output,
            JavaFXMediaQuery query,
            StringStore strings
    ) throws IOException {
        output.writeInt(query.alternatives().size());
        for (var expression : query.alternatives()) {
            writeMediaExpression(output, expression, strings);
        }
    }

    /// Writes one JavaFX media-query expression.
    ///
    /// @param output     the stylesheet output stream
    /// @param expression the expression to encode
    /// @param strings    the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeMediaExpression(
            DataOutputStream output,
            JavaFXMediaQuery.Expression expression,
            StringStore strings
    ) throws IOException {
        if (expression instanceof JavaFXMediaQuery.Feature feature) {
            output.writeByte(2);
            output.writeInt(strings.add(feature.name()));
            output.writeInt(
                    feature.value() == null ? -1 : strings.add(feature.value())
            );
        } else if (expression instanceof JavaFXMediaQuery.Conjunction conjunction) {
            output.writeByte(3);
            writeMediaExpression(output, conjunction.left(), strings);
            writeMediaExpression(output, conjunction.right(), strings);
        } else if (expression instanceof JavaFXMediaQuery.Disjunction disjunction) {
            output.writeByte(4);
            writeMediaExpression(output, disjunction.left(), strings);
            writeMediaExpression(output, disjunction.right(), strings);
        } else if (expression instanceof JavaFXMediaQuery.Negation negation) {
            output.writeByte(5);
            writeMediaExpression(output, negation.expression(), strings);
        } else if (expression instanceof JavaFXMediaQuery.Range range) {
            output.writeByte(range.comparison().binaryTag());
            output.writeInt(strings.add(range.name()));
            output.writeDouble(range.value());
            output.writeByte(
                    range.unit() == null ? -1 : range.unit().binaryOrdinal()
            );
        } else {
            throw new AssertionError("Unknown JavaFX media expression");
        }
    }

    /// Writes a JavaFX-compatible complex selector.
    ///
    /// @param output   the selector output stream
    /// @param selector the selector to write
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeComplexSelector(
            DataOutputStream output,
            ComplexSelector selector,
            StringStore strings
    ) throws IOException {
        if (!selector.leadingCombinators().isEmpty()) {
            throw new BssSerializeException(
                    "BSS output doesn't support leading selector combinators.",
                    selector.span(),
                    null
            );
        }
        var components = selector.components();
        if (components.size() == 1) {
            if (!components.get(0).combinators().isEmpty()) {
                throw new BssSerializeException(
                        "BSS output doesn't support trailing selector combinators.",
                        selector.span(),
                        null
                );
            }
            writeSimpleSelector(output, components.get(0), strings);
            return;
        }

        output.writeByte(COMPOUND_SELECTOR);
        writeShortCount(output, components.size(), selector.span(), "compound selector parts");
        for (var component : components) {
            writeSimpleSelector(output, component, strings);
        }
        writeShortCount(
                output,
                components.size() - 1,
                selector.span(),
                "compound selector combinators"
        );
        for (var index = 0; index < components.size() - 1; index++) {
            output.writeByte(combinatorByte(components.get(index), selector.span()));
        }
        if (!components.get(components.size() - 1).combinators().isEmpty()) {
            throw new BssSerializeException(
                    "BSS output doesn't support trailing selector combinators.",
                    selector.span(),
                    null
            );
        }
    }

    /// Converts the combinators after one compound to one JavaFX BSS relation.
    ///
    /// @param component the compound and its trailing combinators
    /// @param span      the containing selector span
    /// @return the JavaFX BSS relationship ordinal
    private static byte combinatorByte(ComplexSelectorComponent component, SourceSpan span) {
        var combinators = component.combinators();
        if (combinators.isEmpty()) {
            return DESCENDANT_COMBINATOR;
        }
        if (combinators.size() == 1 && combinators.get(0) == Combinator.CHILD) {
            return CHILD_COMBINATOR;
        }
        throw new BssSerializeException(
                "BSS output supports only descendant and child selector combinators.",
                span,
                null
        );
    }

    /// Writes one JavaFX-compatible simple selector.
    ///
    /// @param output    the selector output stream
    /// @param component the source selector component
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeSimpleSelector(
            DataOutputStream output,
            ComplexSelectorComponent component,
            StringStore strings
    ) throws IOException {
        var selector = JavaFXSimpleSelector.from(component);
        if (selector == null) {
            throw new BssSerializeException(
                    "BSS output doesn't support selector "
                            + component.selector().toCssString()
                            + ".",
                    component.span(),
                    null
            );
        }

        output.writeByte(SIMPLE_SELECTOR);
        output.writeShort(strings.add(selector.typeName()));
        writeShortCount(
                output,
                selector.styleClasses().size(),
                component.span(),
                "style classes"
        );
        for (var styleClass : selector.styleClasses()) {
            output.writeShort(strings.add(styleClass));
        }
        output.writeShort(strings.add(selector.id()));
        writeShortCount(
                output,
                selector.pseudoClasses().size(),
                component.span(),
                "pseudo classes"
        );
        for (var pseudoClass : selector.pseudoClasses()) {
            output.writeShort(strings.add(pseudoClass));
        }
    }
    /// Writes one BSS declaration.
    ///
    /// @param output      the declaration output stream
    /// @param declaration the normalized declaration and lookup context
    /// @param strings     the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeDeclaration(
            DataOutputStream output,
            BssDeclaration declaration,
            StringStore strings
    ) throws IOException {
        var source = declaration.source();
        if (!source.parsedAsSassScript()) {
            throw new BssSerializeException(
                    "BSS output doesn't support raw CSS declaration values.",
                    source.value().span(),
                    null
            );
        }

        strings.useLookupProperties(declaration.lookupProperties());
        var property = declaration.property();
        output.writeShort(strings.add(property));
        var value = splitImportant(source.value().value());
        requireTokenizableValue(value.value(), source.value().span());
        var normalizedValue = normalizeLayeredPaintColors(
                property,
                value.value(),
                source.value().span(),
                strings
        );
        writeDeclarationValue(
                output,
                property,
                normalizedValue,
                source.value().span(),
                strings
        );
        output.writeBoolean(value.important());
    }

    /// Converts named colors in JavaFX's layered paint properties.
    ///
    /// Plain CSS evaluation retains named colors as strings, while SCSS
    /// evaluation generally produces [SassColor] values. OpenJFX resolves a
    /// previously registered declaration name as a property lookup before
    /// interpreting the same token as a color.
    ///
    /// @param property the normalized declaration name
    /// @param value    the evaluated declaration value
    /// @param span     the source range associated with the declaration
    /// @param strings  the current property-lookup registry
    /// @return the value with applicable named colors normalized
    private static SassValue normalizeLayeredPaintColors(
            String property,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) {
        if (!isBackgroundColorProperty(property)
                && !isBorderColorProperty(property)) {
            return value;
        }
        return normalizeLayeredPaintColor(value, span, strings);
    }

    /// Recursively normalizes one layered paint value.
    ///
    /// @param value   the atomic paint or nested list
    /// @param span    the declaration source range
    /// @param strings the current property-lookup registry
    /// @return the normalized paint value
    private static SassValue normalizeLayeredPaintColor(
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) {
        if (value instanceof SassString string
                && (string.hasQuotes()
                || !strings.isRegisteredLookupKey(string.text()))) {
            @Nullable var color = JavaFXPaintParser.tryParseSolidColor(
                    string.text(),
                    span
            );
            if (color != null) {
                return color.color();
            }
            return value;
        }
        if (!(value instanceof SassList list)) {
            return value;
        }
        var normalized = new ArrayList<SassValue>(list.contents().size());
        for (var item : list.contents()) {
            normalized.add(normalizeLayeredPaintColor(item, span, strings));
        }
        return new SassList(normalized, list.separator(), list.hasBrackets());
    }

    /// Separates a trailing Sass representation of {@code !important}.
    ///
    /// @param value the evaluated declaration value
    /// @return the value without priority text and its priority flag
    private static DeclarationValue splitImportant(SassValue value) {
        if (!(value instanceof SassList list)
                || list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().size() < 2) {
            return new DeclarationValue(value, false);
        }
        var last = list.contents().get(list.contents().size() - 1);
        if (!(last instanceof SassString string)
                || string.hasQuotes()
                || !string.text().equals("!important")) {
            return new DeclarationValue(value, false);
        }
        var contents = list.contents().subList(0, list.contents().size() - 1);
        SassValue trimmed = contents.size() == 1
                ? contents.get(0)
                : new SassList(contents, ListSeparator.SPACE, false);
        return new DeclarationValue(trimmed, true);
    }

    /// Requires an evaluated value to use token forms accepted by JavaFX CSS.
    ///
    /// @param value the evaluated declaration value
    /// @param span  the source range associated with the value
    /// @throws BssSerializeException if JavaFX's legacy lexer cannot tokenize
    /// the serialized value
    private static void requireTokenizableValue(
            SassValue value,
            SourceSpan span
    ) {
        final String text;
        try {
            text = value.toCssString();
        } catch (SassValueException failure) {
            throw new BssSerializeException(
                    Objects.requireNonNull(
                            failure.getMessage(),
                            "declaration value failure message"
                    ),
                    span,
                    failure
            );
        }
        requireTokenizableValue(text, span);
    }

    /// Requires emitted value text to use token forms accepted by JavaFX CSS.
    ///
    /// @param text the emitted declaration value
    /// @param span the source range associated with the value
    /// @throws BssSerializeException if JavaFX's legacy lexer cannot tokenize
    /// the value
    private static void requireTokenizableValue(String text, SourceSpan span) {
        if (!JavaFXCssLexer.isTokenizableValue(text)) {
            throw new BssSerializeException(
                    "JavaFX CSS cannot tokenize this declaration value.",
                    span,
                    null
            );
        }
    }

    /// Writes one supported declaration value.
    ///
    /// @param output   the declaration output stream
    /// @param property the CSS property name
    /// @param value    the evaluated Sass value
    /// @param span     the source value span
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeDeclarationValue(
            DataOutputStream output,
            String property,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (isTransitionProperty(property)) {
            throw new BssSerializeException(
                    "JavaFX 23 through 27 cannot load transition declarations from BSS.",
                    span,
                    null
            );
        }
        if (value instanceof SassString string && isGlobalKeyword(string)) {
            writeStringValue(output, property, string, strings);
            return;
        }
        if (isFontSizeProperty(property)) {
            writeFontSizeValue(output, value, span, strings);
            return;
        }
        if (property.equals("-fx-blend-mode")
                && value instanceof SassColor color
                && isExtendedBlendModeColor(color)) {
            writePlainStringValue(output, color.toString().toLowerCase(Locale.ROOT), strings);
            return;
        }
        if (isFontProperty(property)) {
            writeFontValue(output, value, span, strings);
            return;
        }
        if (isFontStyleProperty(property) || isFontWeightProperty(property)) {
            writeFontKeywordValue(output, property, value, span, strings);
            return;
        }
        if (isFontSmoothingProperty(property)) {
            writeFontSmoothingValue(output, value, span, strings);
            return;
        }
        @Nullable String enumClass = strokeEnumClass(property);
        if (enumClass != null) {
            writeEnumValue(output, value, enumClass, span, strings);
            return;
        }
        if (isStrokeDashArrayProperty(property)) {
            writeStrokeDashArray(output, value, span, strings);
            return;
        }
        if (isBackgroundColorProperty(property)) {
            writeBackgroundPaintLayers(output, value, span, strings);
            return;
        }
        if (isBorderColorProperty(property)) {
            writeBorderPaintLayers(output, value, span, strings);
            return;
        }
        if (isBorderWidthProperty(property)) {
            writeBorderWidthLayers(output, value, span, strings);
            return;
        }
        if (isBorderStyleProperty(property)) {
            writeBorderStyleLayers(output, value, span, strings);
            return;
        }
        if (isBorderImageSliceProperty(property)) {
            writeBorderImageSliceLayers(output, value, span, strings);
            return;
        }
        if (isBorderImageWidthProperty(property)) {
            writeBorderImageWidthLayers(output, value, span, strings);
            return;
        }
        if (isUrlLayersProperty(property)) {
            writeUrlLayers(output, value, span, strings);
            return;
        }
        if (isBackgroundPositionProperty(property)) {
            writeBackgroundPositionLayers(output, value, span, strings);
            return;
        }
        if (isBackgroundRepeatProperty(property) || isBorderImageRepeatProperty(property)) {
            writeBackgroundRepeatLayers(output, value, span, strings);
            return;
        }
        if (isBackgroundSizeProperty(property)) {
            writeBackgroundSizeLayers(output, value, span, strings);
            return;
        }
        if (isBorderImageInsetsProperty(property)) {
            writeBorderImageInsetLayers(output, value, span, strings);
            return;
        }
        if (isLayeredInsetsProperty(property)) {
            writeLayeredInsetsValue(output, value, span, strings);
            return;
        }
        if (isCornerRadiiProperty(property)) {
            writeCornerRadiiValue(output, value, span, strings);
            return;
        }
        if (isEffectProperty(property)) {
            if (JavaFXEffectParser.isEffectFunction(value)) {
                writeEffectValue(
                        output,
                        JavaFXEffectParser.parse(
                                value,
                                span,
                                strings::isRegisteredLookupKey
                        ),
                        span,
                        strings
                );
            } else if (value instanceof SassString string
                    && !string.hasQuotes()
                    && JavaFXPaintParser.isLookupIdentifier(string.text())) {
                writeStringValue(output, property, string, strings);
            } else {
                JavaFXEffectParser.parse(
                        value,
                        span,
                        strings::isRegisteredLookupKey
                );
                throw new AssertionError("invalid JavaFX effect parsing returned normally");
            }
            return;
        }
        if (JavaFXEffectParser.isEffectFunction(value)) {
            writeEffectValue(
                    output,
                    JavaFXEffectParser.parse(
                            value,
                            span,
                            strings::isRegisteredLookupKey
                    ),
                    span,
                    strings
            );
            return;
        }
        if (JavaFXPaintParser.isPaintFunction(value)) {
            writePaintValue(
                    output,
                    JavaFXPaintParser.parse(
                            value,
                            span,
                            strings::isRegisteredLookupKey
                    ),
                    span,
                    strings
            );
            return;
        }
        if (isScalarUrlValue(value)) {
            var string = (SassString) value;
            var resource = urlResource(string.text(), span);
            @Nullable String stylesheetUrl = stylesheetUrl(span);
            if (property.equals("-fx-fill")) {
                writeFillUrlValue(
                        output,
                        resource,
                        strings
                );
            } else {
                writeUrlValue(
                        output,
                        resource,
                        stylesheetUrl,
                        strings
                );
            }
            return;
        }
        if (isInsetsProperty(property)) {
            writeInsetsValue(output, value, span, strings);
        } else if (value instanceof SassNumber number) {
            writeNumberValue(output, property, number, span, strings);
        } else if (value instanceof SassColor color) {
            @Nullable String lookup =
                    JavaFXPaintParser.registeredSourceColorLookup(
                            color,
                            strings::isRegisteredLookupKey
                    );
            if (lookup == null) {
                writeColorValue(output, color, span, strings);
            } else {
                writeLookupValue(output, lookup, strings);
            }
        } else if (value instanceof SassString string) {
            if (usesGenericStringGrammar(property)) {
                writeGenericStringValue(output, property, string, span, strings);
            } else {
                writeStringValue(output, property, string, strings);
            }
        } else if (value instanceof SassBoolean bool) {
            writeBooleanValue(output, bool, strings);
        } else if (value instanceof SassList list) {
            writeGenericSizeSequence(output, list, span, strings);
        } else {
            throw new BssSerializeException(
                    "BSS output doesn't support this declaration value.",
                    span,
                    null
            );
        }
    }

    /// Writes JavaFX's generic space-separated size sequence.
    ///
    /// @param output  the declaration output stream
    /// @param list    the evaluated Sass list
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeGenericSizeSequence(
            DataOutputStream output,
            SassList list,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().size() < 2) {
            throw invalidGenericSizeSequence(span);
        }
        var sizes = new ArrayList<SassNumber>(list.contents().size());
        for (var item : list.contents()) {
            if (!(item instanceof SassNumber number) || isTimeNumber(number)) {
                throw invalidGenericSizeSequence(span);
            }
            sizes.add(number);
        }

        writeParsedHeader(output, false, SIZE_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, sizes.size());
        for (var size : sizes) {
            output.writeByte(NESTED_VALUE);
            writeSizeValue(output, size, span, strings);
        }
    }

    /// Creates the standard failure for an invalid generic size sequence.
    ///
    /// @param span the source value span
    /// @return the source-associated failure
    private static BssSerializeException invalidGenericSizeSequence(SourceSpan span) {
        return new BssSerializeException(
                "BSS generic size sequences require two or more unbracketed"
                        + " space-separated non-time sizes.",
                span,
                null
        );
    }

    /// Returns whether a property delegates scalar strings to JavaFX's generic parser.
    ///
    /// @param property the CSS property name
    /// @return whether generic keyword and color recognition applies
    private static boolean usesGenericStringGrammar(String property) {
        return !property.endsWith("font-family")
                && !property.endsWith("font-size")
                && !property.equals("-fx-blend-mode");
    }

    /// Returns whether a string is one of JavaFX's global declaration keywords.
    ///
    /// @param string the evaluated Sass string
    /// @return whether the string must bypass a property-specific converter
    private static boolean isGlobalKeyword(SassString string) {
        if (string.hasQuotes()) {
            return false;
        }
        return switch (string.text().toLowerCase(Locale.ROOT)) {
            case "inherit", "none", "null" -> true;
            default -> false;
        };
    }

    /// Returns whether a declaration belongs to JavaFX's transition property family.
    ///
    /// @param property the CSS property name
    /// @return whether the property uses a transition-specific converter
    private static boolean isTransitionProperty(String property) {
        return switch (property) {
            case "transition",
                 "transition-delay",
                 "transition-duration",
                 "transition-property",
                 "transition-timing-function" -> true;
            default -> false;
        };
    }

    /// Returns whether a property consumes a font-style scalar.
    ///
    /// @param property the CSS property name
    /// @return whether the property uses the font-style converter
    private static boolean isFontStyleProperty(String property) {
        return property.endsWith("font-style");
    }

    /// Returns whether a property consumes a font-weight scalar.
    ///
    /// @param property the CSS property name
    /// @return whether the property uses the font-weight converter
    private static boolean isFontWeightProperty(String property) {
        return property.endsWith("font-weight");
    }

    /// Returns whether a property consumes a font-size scalar.
    ///
    /// @param property the CSS property name
    /// @return whether the property uses the font-size converter
    private static boolean isFontSizeProperty(String property) {
        return property.endsWith("font-size");
    }

    /// Returns whether a property consumes the JavaFX font shorthand grammar.
    ///
    /// @param property the CSS property name
    /// @return whether the property uses the composite font converter
    private static boolean isFontProperty(String property) {
        return property.endsWith("font");
    }

    /// Returns whether a property stores JavaFX font-smoothing text.
    ///
    /// @param property the CSS property name
    /// @return whether the property is the JavaFX font-smoothing property
    private static boolean isFontSmoothingProperty(String property) {
        return property.equals("-fx-font-smoothing-type");
    }

    /// Returns whether a property consumes a JavaFX effect or effect lookup.
    ///
    /// @param property the CSS property name
    /// @return whether the property is JavaFX's standard effect property
    private static boolean isEffectProperty(String property) {
        return property.equals("-fx-effect");
    }

    /// Returns the JavaFX enum class serialized for a supported stroke property.
    ///
    /// @param property the CSS property name
    /// @return the enum class name, or {@code null} when the property is not supported here
    private static @Nullable String strokeEnumClass(String property) {
        return switch (property) {
            case "-fx-stroke-line-cap" -> "javafx.scene.shape.StrokeLineCap";
            case "-fx-stroke-line-join" -> "javafx.scene.shape.StrokeLineJoin";
            case "-fx-stroke-type" -> "javafx.scene.shape.StrokeType";
            default -> null;
        };
    }

    /// Returns whether a property consumes a sequence of size values.
    ///
    /// @param property the CSS property name
    /// @return whether the property uses the stroke-dash-array converter
    private static boolean isStrokeDashArrayProperty(String property) {
        return property.equals("-fx-stroke-dash-array");
    }

    /// Returns whether a property uses JavaFX's four-sided insets payload.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes an insets value
    private static boolean isInsetsProperty(String property) {
        return property.equals("-fx-padding")
                || property.equals("-fx-label-padding")
                || property.equals("-fx-opaque-insets");
    }

    /// Returns whether a property accepts a comma-separated sequence of solid background paints.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes background paint layers
    private static boolean isBackgroundColorProperty(String property) {
        return property.equals("-fx-background-color");
    }

    /// Returns whether a property accepts comma-separated four-sided border paint layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes layered border paints
    private static boolean isBorderColorProperty(String property) {
        return property.equals("-fx-border-color");
    }

    /// Returns whether a property accepts comma-separated four-sided border-width layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes layered border widths
    private static boolean isBorderWidthProperty(String property) {
        return property.equals("-fx-border-width");
    }

    /// Returns whether a property accepts comma-separated four-sided border-style layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes layered border styles
    private static boolean isBorderStyleProperty(String property) {
        return property.equals("-fx-border-style");
    }

    /// Returns whether a property accepts comma-separated border-image inset layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes layered border-image insets
    private static boolean isBorderImageInsetsProperty(String property) {
        return property.equals("-fx-border-image-insets");
    }

    /// Returns whether a property accepts comma-separated border-image repeat layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes border-image repeat layers
    private static boolean isBorderImageRepeatProperty(String property) {
        return property.equals("-fx-border-image-repeat");
    }

    /// Returns whether a property accepts comma-separated border-image slice layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes border-image slice layers
    private static boolean isBorderImageSliceProperty(String property) {
        return property.equals("-fx-border-image-slice");
    }

    /// Returns whether a property accepts comma-separated border-image width layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes border-image width layers
    private static boolean isBorderImageWidthProperty(String property) {
        return property.equals("-fx-border-image-width");
    }

    /// Returns whether a property accepts a comma-separated sequence of URL sources.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes URL layers
    private static boolean isUrlLayersProperty(String property) {
        return property.equals("-fx-background-image")
                || property.equals("-fx-border-image-source");
    }

    /// Returns whether a value is one scalar JavaFX `url(...)` expression.
    ///
    /// @param value the evaluated Sass value
    /// @return whether the value begins with an unquoted URL function
    private static boolean isScalarUrlValue(SassValue value) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            return false;
        }
        var text = string.text().stripLeading();
        return text.startsWith("url(");
    }

    /// Returns whether a Sass color token is a JavaFX 18 blend-mode identifier.
    ///
    /// Sass evaluates the conflicting `red`, `green`, and `blue` identifiers as
    /// colors before BSS serialization. JavaFX 18 and later instead retain them
    /// as strings for `-fx-blend-mode`.
    ///
    /// @param color the evaluated Sass color
    /// @return whether the color's preferred representation is a blend mode
    private static boolean isExtendedBlendModeColor(SassColor color) {
        return switch (color.toString().toLowerCase(Locale.ROOT)) {
            case "red", "green", "blue" -> true;
            default -> false;
        };
    }

    /// Returns whether a property accepts comma-separated background-position layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes background-position layers
    private static boolean isBackgroundPositionProperty(String property) {
        return property.equals("-fx-background-position");
    }

    /// Returns whether a property accepts comma-separated background-repeat layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes background-repeat layers
    private static boolean isBackgroundRepeatProperty(String property) {
        return property.equals("-fx-background-repeat");
    }

    /// Returns whether a property accepts comma-separated background-size layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes background-size layers
    private static boolean isBackgroundSizeProperty(String property) {
        return property.equals("-fx-background-size");
    }

    /// Returns whether a property accepts a comma-separated sequence of inset layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes layered insets
    private static boolean isLayeredInsetsProperty(String property) {
        return property.equals("-fx-background-insets") || property.equals("-fx-border-insets");
    }

    /// Returns whether a property accepts a comma-separated sequence of corner-radius layers.
    ///
    /// @param property the CSS property name
    /// @return whether the property consumes layered corner radii
    private static boolean isCornerRadiiProperty(String property) {
        return property.equals("-fx-background-radius") || property.equals("-fx-border-radius");
    }

    /// Writes an ordinary size, duration, or font-size numeric value.
    ///
    /// @param output   the declaration output stream
    /// @param property the CSS property name
    /// @param number   the evaluated Sass number
    /// @param span     the source value span
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNumberValue(
            DataOutputStream output,
            String property,
            SassNumber number,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (number.slashNumerator() != null) {
            throw invalidGenericSizeSequence(span);
        }
        var converter = property.endsWith("font-size")
                ? FONT_SIZE_CONVERTER
                : isTimeNumber(number) ? DURATION_CONVERTER : SIZE_CONVERTER;
        writeParsedHeader(output, false, converter, strings);
        output.writeByte(NESTED_VALUE);
        writeSizeValue(output, number, span, strings);
    }

    /// Writes a JavaFX font size after expanding its CSS keyword, if any.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated numeric size or font-size keyword
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeFontSizeValue(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        @Nullable var size = normalizedFontSize(value);
        if (size == null) {
            throw new BssSerializeException(
                    "BSS font sizes require a JavaFX size or font-size keyword.",
                    span,
                    null
            );
        }
        writeNumberValue(output, "-fx-font-size", size, span, strings);
    }

    /// Returns the OpenJFX numeric representation of a font-size value.
    ///
    /// @param value the evaluated size or keyword
    /// @return the accepted number, or {@code null} when the value is invalid
    private static @Nullable SassNumber normalizedFontSize(SassValue value) {
        if (value instanceof SassNumber number) {
            return isFontSize(number) ? number : null;
        }
        if (!(value instanceof SassString keyword) || keyword.hasQuotes()) {
            return null;
        }
        var percentage = switch (keyword.text().toLowerCase(Locale.ROOT)) {
            case "xx-small" -> 60.0;
            case "x-small" -> 75.0;
            case "small", "smaller" -> 80.0;
            case "inherit", "medium" -> 100.0;
            case "large", "larger" -> 120.0;
            case "x-large" -> 150.0;
            case "xx-large" -> 200.0;
            default -> Double.NaN;
        };
        return Double.isNaN(percentage)
                ? null
                : SassNumber.of(percentage, "%");
    }

    /// Returns whether a Sass number has one JavaFX time unit.
    ///
    /// @param number the evaluated number
    /// @return whether its sole numerator unit is `s` or `ms`
    private static boolean isTimeNumber(SassNumber number) {
        if (!number.denominatorUnits().isEmpty()
                || number.numeratorUnits().size() != 1) {
            return false;
        }
        var unit = number.numeratorUnits().get(0);
        return unit.equalsIgnoreCase("s") || unit.equalsIgnoreCase("ms");
    }

    /// Writes a JavaFX color parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param color   the evaluated Sass color
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeColorValue(
            DataOutputStream output,
            SassColor color,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, null, strings);
        output.writeByte(COLOR_VALUE);
        output.writeLong(Double.doubleToLongBits(normalizedColorChannel(color.red(), span)));
        output.writeLong(Double.doubleToLongBits(normalizedColorChannel(color.green(), span)));
        output.writeLong(Double.doubleToLongBits(normalizedColorChannel(color.blue(), span)));
        output.writeLong(Double.doubleToLongBits(normalizedColorAlpha(color.alpha())));
    }

    /// Validates and normalizes one Sass RGB channel for JavaFX Color storage.
    ///
    /// @param channel the Sass channel in the zero-to-255 range
    /// @param span    the source value span
    /// @return the normalized JavaFX channel
    private static double normalizedColorChannel(double channel, SourceSpan span) {
        if (!Double.isFinite(channel) || channel < 0.0 || channel > 255.0) {
            throw new BssSerializeException(
                    "BSS output supports only in-gamut RGB colors.",
                    span,
                    null
            );
        }
        return (double) (float) (channel / 255.0);
    }

    /// Normalizes alpha to JavaFX's single-precision color representation.
    ///
    /// @param alpha the Sass alpha channel in the zero-to-one range
    /// @return the JavaFX-compatible alpha component
    private static double normalizedColorAlpha(double alpha) {
        return (double) (float) alpha;
    }

    /// Writes one solid, lookup, gradient, or image-pattern JavaFX paint parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param paint   the normalized paint
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writePaintValue(
            DataOutputStream output,
            JavaFXPaintParser.Paint paint,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (paint instanceof JavaFXPaintParser.ColorPaint color) {
            writeColorPaintValue(output, color, span, strings);
            return;
        }
        if (paint instanceof JavaFXPaintParser.LinearGradientPaint gradient) {
            writeLinearGradientValue(output, gradient, span, strings);
            return;
        }
        if (paint instanceof JavaFXPaintParser.RadialGradientPaint gradient) {
            writeRadialGradientValue(output, gradient, span, strings);
            return;
        }
        if (paint instanceof JavaFXPaintParser.ImagePatternPaint pattern) {
            writeImagePatternValue(output, pattern, span, strings);
            return;
        }
        if (paint instanceof JavaFXPaintParser.RepeatingImagePatternPaint pattern) {
            writeRepeatingImagePatternValue(output, pattern, strings);
            return;
        }
        if (paint instanceof JavaFXPaintParser.RegionReferencePaint reference) {
            writeRegionReferenceValue(output, reference, strings);
            return;
        }
        throw new AssertionError("unsupported JavaFX paint type");
    }

    /// Writes one JavaFX `region("selector")` reference.
    ///
    /// @param output    the declaration output stream
    /// @param reference the normalized region reference
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeRegionReferenceValue(
            DataOutputStream output,
            JavaFXPaintParser.RegionReferencePaint reference,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, STRING_CONVERTER, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(reference.value()));
    }

    /// Writes one JavaFX drop-shadow or inner-shadow effect.
    ///
    /// @param output  the declaration output stream
    /// @param effect  the normalized shadow effect
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeEffectValue(
            DataOutputStream output,
            JavaFXEffectParser.ShadowEffect effect,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, effect.kind().converterClass(), strings);
        writeParsedValueArrayPrefix(output, 6);

        output.writeByte(NESTED_VALUE);
        writeEnumConstant(output, BLUR_TYPE_ENUM_CLASS, effect.blurType(), strings);

        output.writeByte(NESTED_VALUE);
        writeColorPaintValue(output, effect.color(), span, strings);

        writeEffectSize(output, effect.radius(), span, strings);
        writeEffectSize(output, effect.spreadOrChoke(), span, strings);
        writeEffectSize(output, effect.offsetX(), span, strings);
        writeEffectSize(output, effect.offsetY(), span, strings);
    }

    /// Writes one nested raw or property-lookup-backed effect size.
    ///
    /// @param output  the declaration output stream
    /// @param size    the normalized effect size
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeEffectSize(
            DataOutputStream output,
            JavaFXEffectParser.EffectSize size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        output.writeByte(NESTED_VALUE);
        if (size instanceof JavaFXEffectParser.RawEffectSize raw) {
            writeSizeValue(output, raw.value(), span, strings);
        } else if (size instanceof JavaFXEffectParser.LookupEffectSize lookup) {
            writeLookupValue(output, lookup.key(), strings);
        } else {
            throw new AssertionError("unsupported JavaFX effect size type");
        }
    }

    /// Writes one solid, lookup, derived, or ladder JavaFX color.
    ///
    /// @param output  the declaration output stream
    /// @param color   the normalized color
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeColorPaintValue(
            DataOutputStream output,
            JavaFXPaintParser.ColorPaint color,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (color instanceof JavaFXPaintParser.SolidPaint solid) {
            writeColorValue(output, solid.color(), span, strings);
            return;
        }
        if (color instanceof JavaFXPaintParser.LookupPaint lookup) {
            writeLookupValue(
                    output,
                    strings.normalizeLookupKey(lookup.key()),
                    strings
            );
            return;
        }
        if (color instanceof JavaFXPaintParser.DerivedPaint derived) {
            writeParsedHeader(output, false, DERIVE_COLOR_CONVERTER, strings);
            writeParsedValueArrayPrefix(output, 2);
            output.writeByte(NESTED_VALUE);
            writeColorPaintValue(output, derived.base(), span, strings);
            writeNestedGradientSize(output, derived.brightness(), span, strings);
            return;
        }
        if (color instanceof JavaFXPaintParser.LadderPaint ladder) {
            writeParsedHeader(output, false, LADDER_CONVERTER, strings);
            writeParsedValueArrayPrefix(output, 1 + ladder.stops().size());
            output.writeByte(NESTED_VALUE);
            writeColorPaintValue(output, ladder.base(), span, strings);
            for (var stop : ladder.stops()) {
                output.writeByte(NESTED_VALUE);
                writeGradientStopValue(output, stop, span, strings);
            }
            return;
        }
        throw new AssertionError("unsupported JavaFX color type");
    }

    /// Writes one JavaFX lookup parsed value with no converter.
    ///
    /// @param output  the declaration output stream
    /// @param key     the property key to resolve at CSS application time
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeLookupValue(
            DataOutputStream output,
            String key,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, true, null, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(key));
    }

    /// Writes one JavaFX {@code image-pattern(...)} parsed value.
    ///
    /// JavaFX stores image-pattern URI values with a null stylesheet base even
    /// when the declaration source has a canonical URL.
    ///
    /// @param output  the declaration output stream
    /// @param pattern the normalized image-pattern paint
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeImagePatternValue(
            DataOutputStream output,
            JavaFXPaintParser.ImagePatternPaint pattern,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        @Nullable Boolean proportional = pattern.proportional();
        var valueCount = pattern.hasGeometry() ? proportional == null ? 5 : 6 : 1;
        writeParsedHeader(output, false, IMAGE_PATTERN_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, valueCount);
        output.writeByte(NESTED_VALUE);
        writeUrlValue(output, pattern.resource(), null, strings);
        if (!pattern.hasGeometry()) {
            return;
        }

        var x = Objects.requireNonNull(pattern.x(), "image pattern x");
        var y = Objects.requireNonNull(pattern.y(), "image pattern y");
        var width = Objects.requireNonNull(pattern.width(), "image pattern width");
        var height = Objects.requireNonNull(pattern.height(), "image pattern height");
        output.writeByte(NESTED_VALUE);
        writeImagePatternSizeValue(output, x, span, strings);
        output.writeByte(NESTED_VALUE);
        writeImagePatternSizeValue(output, y, span, strings);
        output.writeByte(NESTED_VALUE);
        writeImagePatternSizeValue(output, width, span, strings);
        output.writeByte(NESTED_VALUE);
        writeImagePatternSizeValue(output, height, span, strings);
        if (proportional != null) {
            output.writeByte(NESTED_VALUE);
            writeParsedHeader(output, false, null, strings);
            output.writeByte(BOOLEAN_VALUE);
            output.writeBoolean(proportional);
        }
    }

    /// Writes one JavaFX {@code repeating-image-pattern(...)} parsed value.
    ///
    /// JavaFX stores repeating image-pattern URI values with a null stylesheet
    /// base even when the declaration source has a canonical URL.
    ///
    /// @param output  the declaration output stream
    /// @param pattern the normalized repeating image-pattern paint
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeRepeatingImagePatternValue(
            DataOutputStream output,
            JavaFXPaintParser.RepeatingImagePatternPaint pattern,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, REPEATING_IMAGE_PATTERN_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 1);
        output.writeByte(NESTED_VALUE);
        writeUrlValue(output, pattern.resource(), null, strings);
    }

    /// Writes one raw or lookup JavaFX image-pattern size value.
    ///
    /// @param output  the declaration output stream
    /// @param size    the normalized image-pattern size
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeImagePatternSizeValue(
            DataOutputStream output,
            JavaFXPaintParser.ImagePatternSize size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (size instanceof JavaFXPaintParser.RawImagePatternSize raw) {
            writeSizeValue(output, raw.size(), span, strings);
            return;
        }
        if (size instanceof JavaFXPaintParser.LookupImagePatternSize lookup) {
            writeLookupValue(output, lookup.key(), strings);
            return;
        }
        throw new AssertionError("unsupported JavaFX image-pattern size type");
    }

    /// Writes one JavaFX linear-gradient parsed value.
    ///
    /// @param output   the declaration output stream
    /// @param gradient the normalized gradient
    /// @param span     the source value span
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeLinearGradientValue(
            DataOutputStream output,
            JavaFXPaintParser.LinearGradientPaint gradient,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, LINEAR_GRADIENT_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 5 + gradient.stops().size());
        writeNestedGradientSize(output, gradient.startX(), span, strings);
        writeNestedGradientSize(output, gradient.startY(), span, strings);
        writeNestedGradientSize(output, gradient.endX(), span, strings);
        writeNestedGradientSize(output, gradient.endY(), span, strings);
        var cycleMethod = strings.version() == VERSION_5
                && !gradient.legacySyntax()
                && gradient.cycleMethod().equals("REPEAT")
                ? "REFLECT"
                : gradient.cycleMethod();
        writeNestedGradientCycleMethod(output, cycleMethod, strings);
        for (var stop : gradient.stops()) {
            output.writeByte(NESTED_VALUE);
            writeGradientStopValue(output, stop, span, strings);
        }
    }

    /// Writes one JavaFX radial-gradient parsed value.
    ///
    /// @param output   the declaration output stream
    /// @param gradient the normalized gradient
    /// @param span     the source value span
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeRadialGradientValue(
            DataOutputStream output,
            JavaFXPaintParser.RadialGradientPaint gradient,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, RADIAL_GRADIENT_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 6 + gradient.stops().size());
        writeNullableGradientSize(output, gradient.focusAngle(), span, strings);
        writeNullableGradientSize(output, gradient.focusDistance(), span, strings);
        writeNullableGradientSize(output, gradient.centerX(), span, strings);
        writeNullableGradientSize(output, gradient.centerY(), span, strings);
        writeNestedGradientSize(output, gradient.radius(), span, strings);
        var cycleMethod = strings.version() == VERSION_5
                && !gradient.legacySyntax()
                && gradient.cycleMethod().equals("REPEAT")
                ? "REFLECT"
                : gradient.cycleMethod();
        writeNestedGradientCycleMethod(output, cycleMethod, strings);
        for (var stop : gradient.stops()) {
            output.writeByte(NESTED_VALUE);
            writeGradientStopValue(output, stop, span, strings);
        }
    }

    /// Writes one nested gradient size or a JavaFX parsed-value null marker.
    ///
    /// @param output  the declaration output stream
    /// @param size    the optional size
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNullableGradientSize(
            DataOutputStream output,
            @Nullable JavaFXPaintParser.GradientSize size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (size == null) {
            output.writeByte(NULL_VALUE);
            return;
        }
        writeNestedGradientSize(output, size, span, strings);
    }

    /// Writes one nested raw or property-lookup JavaFX gradient size.
    ///
    /// @param output  the declaration output stream
    /// @param size    the gradient size to serialize
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNestedGradientSize(
            DataOutputStream output,
            JavaFXPaintParser.GradientSize size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (size instanceof JavaFXPaintParser.RawGradientSize raw) {
            writeNestedGradientSize(output, raw.size(), span, strings);
            return;
        }
        if (size instanceof JavaFXPaintParser.LookupGradientSize lookup) {
            output.writeByte(NESTED_VALUE);
            writeLookupValue(output, lookup.key(), strings);
            return;
        }
        throw new AssertionError("unsupported JavaFX gradient size type");
    }

    /// Writes one nested raw JavaFX Size parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param size    the size to serialize
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNestedGradientSize(
            DataOutputStream output,
            SassNumber size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        output.writeByte(NESTED_VALUE);
        writeSizeValue(output, size, span, strings);
    }

    /// Writes one nested gradient cycle-method enum value.
    ///
    /// @param output      the declaration output stream
    /// @param cycleMethod the JavaFX {@code CycleMethod} enum spelling
    /// @param strings     the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNestedGradientCycleMethod(
            DataOutputStream output,
            String cycleMethod,
            StringStore strings
    ) throws IOException {
        output.writeByte(NESTED_VALUE);
        writeEnumConstant(output, CYCLE_METHOD_ENUM_CLASS, cycleMethod, strings);
    }

    /// Writes one JavaFX gradient color-stop parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param stop    the normalized stop
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeGradientStopValue(
            DataOutputStream output,
            JavaFXPaintParser.GradientStop stop,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, STOP_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 2);
        writeNestedGradientSize(output, stop.offset(), span, strings);
        output.writeByte(NESTED_VALUE);
        writeGradientColorValue(output, stop.color(), span, strings);
    }

    /// Writes one concrete or lookup JavaFX gradient stop color.
    ///
    /// @param output  the declaration output stream
    /// @param color   the normalized gradient stop color
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeGradientColorValue(
            DataOutputStream output,
            JavaFXPaintParser.ColorPaint color,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeColorPaintValue(output, color, span, strings);
    }

    /// Writes one JavaFX enum converter value with a constant spelling.
    ///
    /// @param output    the declaration output stream
    /// @param enumClass the JavaFX enum class name
    /// @param constant  the enum constant name
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeEnumConstant(
            DataOutputStream output,
            String enumClass,
            String constant,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, ENUM_CONVERTER, strings);
        output.writeShort(strings.add(enumClass));
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(constant));
    }

    /// Writes JavaFX's sequence of background paint values.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBackgroundPaintLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var paints = backgroundPaintValues(value, span, strings);
        writeParsedHeader(output, false, PAINT_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, paints.size());
        for (var paint : paints) {
            output.writeByte(NESTED_VALUE);
            writePaintValue(output, paint, span, strings);
        }
    }

    /// Returns one or more supported paints from a background declaration.
    ///
    /// Solid colors, property lookups, JavaFX linear or radial gradients, and
    /// image-pattern paint forms share JavaFX's paint sequence converter.
    ///
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the property-lookup registry for this declaration
    /// @return the paints in layer order
    /// @throws BssSerializeException if a layer is not a supported paint
    private static @Unmodifiable List<JavaFXPaintParser.Paint> backgroundPaintValues(
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) {
        if (!(value instanceof SassList list)) {
            return List.of(JavaFXPaintParser.parse(
                    value,
                    span,
                    strings::isRegisteredLookupKey
            ));
        }
        if (list.separator() == ListSeparator.SPACE
                && JavaFXLegacyGradient.serialize(value) != null) {
            return List.of(JavaFXPaintParser.parse(
                    value,
                    span,
                    strings::isRegisteredLookupKey
            ));
        }
        if (list.hasBrackets()
                || list.separator() != ListSeparator.COMMA
                || list.contents().isEmpty()) {
            throw new BssSerializeException(
                    "BSS background paints require one or more comma-separated paint layers.",
                    span,
                    null
            );
        }
        var paints = new ArrayList<JavaFXPaintParser.Paint>(list.contents().size());
        for (var item : list.contents()) {
            paints.add(JavaFXPaintParser.parse(
                    item,
                    span,
                    strings::isRegisteredLookupKey
            ));
        }
        return List.copyOf(paints);
    }

    /// Writes JavaFX's sequence of URL image-source values.
    ///
    /// The nested representation matches JavaFX's `parseURILayers`: each URL
    /// contains the resource text and the declaration source URL used as its
    /// resolution base. A source without a canonical URL preserves JavaFX's
    /// null-base behavior.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeUrlLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var resources = urlResources(value, span);
        @Nullable String stylesheetUrl = stylesheetUrl(span);
        writeParsedHeader(output, false, URL_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, resources.size());
        for (var resource : resources) {
            output.writeByte(NESTED_VALUE);
            writeUrlValue(output, resource, stylesheetUrl, strings);
        }
    }

    /// Writes one JavaFX `URLConverter` parsed value.
    ///
    /// @param output        the declaration output stream
    /// @param resource      the decoded URL resource text
    /// @param strings       the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeUrlValue(
            DataOutputStream output,
            String resource,
            @Nullable String stylesheetUrl,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, URL_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 2);

        output.writeByte(NESTED_VALUE);
        writeParsedHeader(output, false, STRING_CONVERTER, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(resource));

        if (stylesheetUrl == null) {
            output.writeByte(0);
            return;
        }
        output.writeByte(NESTED_VALUE);
        writeParsedHeader(output, false, null, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(stylesheetUrl));
    }

    /// Wraps a scalar URL in JavaFX's special `-fx-fill` image-pattern value.
    ///
    /// @param output        the declaration output stream
    /// @param resource      the decoded URL resource text
    /// @param strings       the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeFillUrlValue(
            DataOutputStream output,
            String resource,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, IMAGE_PATTERN_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 1);
        output.writeByte(NESTED_VALUE);
        writeUrlValue(output, resource, null, strings);
    }

    /// Writes JavaFX's generic `indefinite` duration representation.
    ///
    /// Generic parsing stores positive infinity with pixel units before
    /// applying `DurationConverter`; transition-specific parsing is handled
    /// separately and is not emitted to BSS.
    ///
    /// @param output  the declaration output stream
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeIndefiniteDurationValue(
            DataOutputStream output,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, DURATION_CONVERTER, strings);
        output.writeByte(NESTED_VALUE);
        writeRawSizeValue(
                output,
                Double.POSITIVE_INFINITY,
                strings
        );
    }

    /// Returns the URL resources encoded by one JavaFX image-source declaration.
    ///
    /// @param value the evaluated Sass value
    /// @param span  the source value span
    /// @return URL resource texts in layer order
    /// @throws BssSerializeException if a layer is not one `url(...)` expression
    private static @Unmodifiable List<String> urlResources(SassValue value, SourceSpan span) {
        var layers = layeredValues(
                value,
                span,
                "BSS image sources require one or more comma-separated url(...) layers."
        );
        var resources = new ArrayList<String>(layers.size());
        for (var layer : layers) {
            if (!(layer instanceof SassString string) || string.hasQuotes()) {
                throw invalidUrlSource(span);
            }
            resources.add(urlResource(string.text(), span));
        }
        return List.copyOf(resources);
    }

    /// Extracts JavaFX's decoded URL token text from one serialized Sass string.
    ///
    /// JavaFX's lexer removes one escape marker without interpreting CSS
    /// hexadecimal escapes, so the BSS writer intentionally follows that
    /// behavior rather than using a general-purpose URI decoder.
    ///
    /// @param text the serialized unquoted Sass string
    /// @param span the source value span
    /// @return the JavaFX URL token text
    /// @throws BssSerializeException if the string is not a complete non-empty URL token
    static String urlResource(String text, SourceSpan span) {
        var length = text.length();
        var index = 0;
        while (index < length && isCssWhitespace(text.charAt(index))) {
            index++;
        }
        if (!text.startsWith("url(", index)) {
            throw invalidUrlSource(span);
        }
        index += 4;
        while (index < length && isCssWhitespace(text.charAt(index))) {
            index++;
        }
        if (index >= length) {
            throw invalidUrlSource(span);
        }

        var resource = new StringBuilder();
        var first = text.charAt(index);
        if (first == '\'' || first == '"') {
            index = appendQuotedUrlResource(text, index, resource, span);
        } else {
            index = appendUnquotedUrlResource(text, index, resource, span);
        }

        while (index < length && isCssWhitespace(text.charAt(index))) {
            index++;
        }
        if (index != length || resource.isEmpty()) {
            throw invalidUrlSource(span);
        }
        return resource.toString();
    }

    /// Appends a quoted JavaFX URL token and returns the index after its closing parenthesis.
    ///
    /// @param text     the serialized Sass string
    /// @param index    the index of the opening quote
    /// @param resource the destination resource text
    /// @param span     the source value span
    /// @return the index after the closing parenthesis
    /// @throws BssSerializeException if the URL token is malformed
    private static int appendQuotedUrlResource(
            String text,
            int index,
            StringBuilder resource,
            SourceSpan span
    ) {
        var quote = text.charAt(index++);
        var length = text.length();
        var closedQuote = false;
        while (index < length) {
            var character = text.charAt(index++);
            if (character == quote) {
                closedQuote = true;
                break;
            }
            if (isCssNewline(character)) {
                throw invalidUrlSource(span);
            }
            if (character == '\\') {
                index = appendEscapedUrlCharacter(text, index, resource, span);
            } else {
                resource.append(character);
            }
        }
        if (!closedQuote) {
            throw invalidUrlSource(span);
        }
        while (index < length && isCssWhitespace(text.charAt(index))) {
            index++;
        }
        if (index >= length || text.charAt(index) != ')') {
            throw invalidUrlSource(span);
        }
        return index + 1;
    }

    /// Appends an unquoted JavaFX URL token and returns the index after its closing parenthesis.
    ///
    /// @param text     the serialized Sass string
    /// @param index    the index of the first resource character
    /// @param resource the destination resource text
    /// @param span     the source value span
    /// @return the index after the closing parenthesis
    /// @throws BssSerializeException if the URL token is malformed
    private static int appendUnquotedUrlResource(
            String text,
            int index,
            StringBuilder resource,
            SourceSpan span
    ) {
        var length = text.length();
        while (index < length) {
            var character = text.charAt(index++);
            if (isCssWhitespace(character)) {
                continue;
            }
            if (character == ')') {
                return index;
            }
            if (character == '\\') {
                index = appendEscapedUrlCharacter(text, index, resource, span);
                continue;
            }
            if (character == '\'' || character == '"' || character == '(') {
                throw invalidUrlSource(span);
            }
            resource.append(character);
        }
        throw invalidUrlSource(span);
    }

    /// Appends one escaped JavaFX URL character and returns the following index.
    ///
    /// @param text     the serialized Sass string
    /// @param index    the index after the escape marker
    /// @param resource the destination resource text
    /// @param span     the source value span
    /// @return the next unread index
    /// @throws BssSerializeException if the escape is incomplete
    private static int appendEscapedUrlCharacter(
            String text,
            int index,
            StringBuilder resource,
            SourceSpan span
    ) {
        if (index >= text.length()) {
            throw invalidUrlSource(span);
        }
        var escaped = text.charAt(index++);
        if (isCssNewline(escaped)) {
            if (escaped == '\r' && index < text.length() && text.charAt(index) == '\n') {
                return index + 1;
            }
            return index;
        }
        resource.append(escaped);
        return index;
    }

    /// Returns whether a character is CSS whitespace.
    ///
    /// @param character the character to inspect
    /// @return whether JavaFX's CSS lexer treats the character as whitespace
    private static boolean isCssWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\f'
                || isCssNewline(character);
    }

    /// Returns whether a character is a CSS newline.
    ///
    /// @param character the character to inspect
    /// @return whether JavaFX's CSS lexer treats the character as a newline
    private static boolean isCssNewline(char character) {
        return character == '\n' || character == '\r';
    }

    /// Returns the source URL spelling JavaFX stores for URL resolution.
    ///
    /// @param span the declaration value span
    /// @return the JavaFX stylesheet URL, or {@code null} when the source has none
    private static @Nullable String stylesheetUrl(SourceSpan span) {
        @Nullable URI sourceUrl = span.url();
        if (sourceUrl == null) {
            return null;
        }
        try {
            return sourceUrl.toURL().toExternalForm();
        } catch (MalformedURLException ignored) {
            return sourceUrl.toString();
        }
    }

    /// Creates the standard BSS failure for one unsupported URL source declaration.
    ///
    /// @param span the source value span
    /// @return the source-associated serialization failure
    private static BssSerializeException invalidUrlSource(SourceSpan span) {
        return new BssSerializeException(
                "BSS image sources require one or more comma-separated url(...) layers.",
                span,
                null
        );
    }
    /// Writes JavaFX's layered background-position representation.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBackgroundPositionLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = backgroundPositionLayers(value, span);
        writeParsedHeader(output, false, LAYERED_BACKGROUND_POSITION_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeBackgroundPositionLayer(output, layer, span, strings);
        }
    }

    /// Writes one JavaFX background-position parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param layer   the normalized position layer
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBackgroundPositionLayer(
            DataOutputStream output,
            BackgroundPositionLayer layer,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, BACKGROUND_POSITION_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 4);
        for (var offset : layer.offsets()) {
            output.writeByte(NESTED_VALUE);
            writeSizeValue(output, offset, span, strings);
        }
    }

    /// Returns normalized JavaFX background-position layers.
    ///
    /// @param value the evaluated Sass value
    /// @param span  the source value span
    /// @return top, right, bottom, left offsets in source layer order
    /// @throws BssSerializeException if a layer does not match JavaFX's position grammar
    private static @Unmodifiable List<BackgroundPositionLayer> backgroundPositionLayers(
            SassValue value,
            SourceSpan span
    ) {
        var values = layeredValues(
                value,
                span,
                "BSS background positions require one or more comma-separated position layers."
        );
        var layers = new ArrayList<BackgroundPositionLayer>(values.size());
        for (var layer : values) {
            layers.add(backgroundPositionLayer(layer, span));
        }
        return List.copyOf(layers);
    }

    /// Normalizes one JavaFX background-position layer to four stored offsets.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return the normalized layer
    /// @throws BssSerializeException if the layer cannot be represented
    private static BackgroundPositionLayer backgroundPositionLayer(SassValue value, SourceSpan span) {
        var components = backgroundPositionComponents(value, span);
        reorderBackgroundPositionComponents(components);
        return switch (components.size()) {
            case 1 -> singleBackgroundPosition(components.get(0), span);
            case 2 -> twoComponentBackgroundPosition(components.get(0), components.get(1), span);
            case 3 -> threeComponentBackgroundPosition(
                    components.get(0), components.get(1), components.get(2), span
            );
            case 4 -> fourComponentBackgroundPosition(
                    components.get(0), components.get(1), components.get(2), components.get(3), span
            );
            default -> throw new AssertionError("validated background-position count is invalid");
        };
    }

    /// Returns mutable space-separated components for one background-position layer.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return one to four position components
    /// @throws BssSerializeException if the layer shape is unsupported
    private static ArrayList<SassValue> backgroundPositionComponents(SassValue value, SourceSpan span) {
        if (!(value instanceof SassList list)) {
            return new ArrayList<>(List.of(value));
        }
        if (list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()
                || list.contents().size() > 4) {
            throw invalidBackgroundPosition(span);
        }
        return new ArrayList<>(list.contents());
    }

    /// Reorders JavaFX vertical-leading position forms into horizontal-first order.
    ///
    /// @param components the mutable source-order components
    private static void reorderBackgroundPositionComponents(ArrayList<SassValue> components) {
        @Nullable String firstKeyword = positionKeyword(components.get(0));
        if (components.size() == 2) {
            @Nullable String secondKeyword = positionKeyword(components.get(1));
            if (isVerticalPositionKeyword(firstKeyword)
                    && isHorizontalOrCenterPositionKeyword(secondKeyword)) {
                var first = components.get(0);
                components.set(0, components.get(1));
                components.set(1, first);
            }
            return;
        }
        if (components.size() == 3 && isVerticalPositionKeyword(firstKeyword)) {
            var first = components.get(0);
            var second = components.get(1);
            var third = components.get(2);
            if (isHorizontalPositionKeyword(positionKeyword(second))) {
                components.set(0, second);
                components.set(1, third);
                components.set(2, first);
            } else {
                components.set(0, third);
                components.set(1, first);
                components.set(2, second);
            }
            return;
        }
        if (components.size() == 4 && isVerticalPositionKeyword(firstKeyword)
                && isHorizontalPositionKeyword(positionKeyword(components.get(2)))) {
            var first = components.get(0);
            var second = components.get(1);
            var third = components.get(2);
            var fourth = components.get(3);
            components.set(0, third);
            components.set(1, fourth);
            components.set(2, first);
            components.set(3, second);
        }
    }

    /// Normalizes one position component.
    ///
    /// @param component the source component
    /// @param span      the source value span
    /// @return the normalized layer
    /// @throws BssSerializeException if the component is unsupported
    private static BackgroundPositionLayer singleBackgroundPosition(SassValue component, SourceSpan span) {
        @Nullable String keyword = positionKeyword(component);
        if (keyword == null) {
            return new BackgroundPositionLayer(
                    FIFTY_PERCENT, ZERO_PERCENT, ZERO_PERCENT, positionSize(component, span)
            );
        }
        return switch (keyword) {
            case "center" -> new BackgroundPositionLayer(
                    FIFTY_PERCENT, ZERO_PERCENT, ZERO_PERCENT, FIFTY_PERCENT
            );
            case "left" -> new BackgroundPositionLayer(
                    FIFTY_PERCENT, ZERO_PERCENT, ZERO_PERCENT, ZERO_PERCENT
            );
            case "right" -> new BackgroundPositionLayer(
                    FIFTY_PERCENT, ZERO_PERCENT, ZERO_PERCENT, ONE_HUNDRED_PERCENT
            );
            case "top" -> new BackgroundPositionLayer(
                    ZERO_PERCENT, ZERO_PERCENT, ZERO_PERCENT, FIFTY_PERCENT
            );
            case "bottom" -> new BackgroundPositionLayer(
                    ONE_HUNDRED_PERCENT, ZERO_PERCENT, ZERO_PERCENT, FIFTY_PERCENT
            );
            default -> throw invalidBackgroundPosition(span);
        };
    }

    /// Normalizes a horizontal-first two-component position layer.
    ///
    /// @param first  the horizontal component
    /// @param second the vertical component
    /// @param span   the source value span
    /// @return the normalized layer
    /// @throws BssSerializeException if the components are unsupported
    private static BackgroundPositionLayer twoComponentBackgroundPosition(
            SassValue first,
            SassValue second,
            SourceSpan span
    ) {
        @Nullable String firstKeyword = positionKeyword(first);
        @Nullable String secondKeyword = positionKeyword(second);
        if (!isPositionKeyword(firstKeyword)) {
            return new BackgroundPositionLayer(
                    verticalPosition(secondKeyword, second, span),
                    ZERO_PERCENT,
                    ZERO_PERCENT,
                    positionSize(first, span)
            );
        }
        return switch (firstKeyword) {
            case "left" -> new BackgroundPositionLayer(
                    verticalPosition(secondKeyword, second, span),
                    ZERO_PERCENT,
                    ZERO_PERCENT,
                    ZERO_PERCENT
            );
            case "right" -> new BackgroundPositionLayer(
                    verticalPosition(secondKeyword, second, span),
                    ZERO_PERCENT,
                    ZERO_PERCENT,
                    ONE_HUNDRED_PERCENT
            );
            case "center" -> new BackgroundPositionLayer(
                    verticalPosition(secondKeyword, second, span),
                    ZERO_PERCENT,
                    ZERO_PERCENT,
                    FIFTY_PERCENT
            );
            default -> throw invalidBackgroundPosition(span);
        };
    }
    /// Normalizes a horizontal-first three-component position layer.
    ///
    /// @param first  the first component
    /// @param second the middle component
    /// @param third  the final component
    /// @param span   the source value span
    /// @return the normalized layer
    /// @throws BssSerializeException if the components are unsupported
    private static BackgroundPositionLayer threeComponentBackgroundPosition(
            SassValue first,
            SassValue second,
            SassValue third,
            SourceSpan span
    ) {
        @Nullable String firstKeyword = positionKeyword(first);
        @Nullable String secondKeyword = positionKeyword(second);
        @Nullable String thirdKeyword = positionKeyword(third);
        if (!isPositionKeyword(firstKeyword) || "center".equals(firstKeyword)) {
            var left = "center".equals(firstKeyword) ? FIFTY_PERCENT : positionSize(first, span);
            var offset = positionSize(third, span);
            if ("top".equals(secondKeyword)) {
                return new BackgroundPositionLayer(offset, ZERO_PERCENT, ZERO_PERCENT, left);
            }
            if ("bottom".equals(secondKeyword)) {
                return new BackgroundPositionLayer(ZERO_PERCENT, ZERO_PERCENT, offset, left);
            }
            throw invalidBackgroundPosition(span);
        }
        if (!"left".equals(firstKeyword) && !"right".equals(firstKeyword)) {
            throw invalidBackgroundPosition(span);
        }
        if (!isPositionKeyword(secondKeyword)) {
            var horizontalOffset = positionSize(second, span);
            if (thirdKeyword == null) {
                throw invalidBackgroundPosition(span);
            }
            return switch (thirdKeyword) {
                case "top" -> new BackgroundPositionLayer(
                        ZERO_PERCENT,
                        "right".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT,
                        ZERO_PERCENT,
                        "left".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT
                );
                case "bottom" -> new BackgroundPositionLayer(
                        ONE_HUNDRED_PERCENT,
                        "right".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT,
                        ZERO_PERCENT,
                        "left".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT
                );
                case "center" -> new BackgroundPositionLayer(
                        FIFTY_PERCENT,
                        "right".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT,
                        ZERO_PERCENT,
                        "left".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT
                );
                default -> throw invalidBackgroundPosition(span);
            };
        }

        var verticalOffset = positionSize(third, span);
        if ("top".equals(secondKeyword)) {
            return new BackgroundPositionLayer(
                    verticalOffset,
                    ZERO_PERCENT,
                    ZERO_PERCENT,
                    "left".equals(firstKeyword) ? ZERO_PERCENT : ONE_HUNDRED_PERCENT
            );
        }
        if ("bottom".equals(secondKeyword)) {
            return new BackgroundPositionLayer(
                    ZERO_PERCENT,
                    ZERO_PERCENT,
                    verticalOffset,
                    "left".equals(firstKeyword) ? ZERO_PERCENT : ONE_HUNDRED_PERCENT
            );
        }
        throw invalidBackgroundPosition(span);
    }

    /// Normalizes a horizontal-edge four-component position layer.
    ///
    /// @param first  the horizontal edge
    /// @param second the horizontal offset
    /// @param third  the vertical edge
    /// @param fourth the vertical offset
    /// @param span   the source value span
    /// @return the normalized layer
    /// @throws BssSerializeException if the components are unsupported
    private static BackgroundPositionLayer fourComponentBackgroundPosition(
            SassValue first,
            SassValue second,
            SassValue third,
            SassValue fourth,
            SourceSpan span
    ) {
        @Nullable String firstKeyword = positionKeyword(first);
        @Nullable String thirdKeyword = positionKeyword(third);
        if ((!"left".equals(firstKeyword) && !"right".equals(firstKeyword))
                || (!"top".equals(thirdKeyword) && !"bottom".equals(thirdKeyword))) {
            throw invalidBackgroundPosition(span);
        }
        var horizontalOffset = positionSize(second, span);
        var verticalOffset = positionSize(fourth, span);
        return new BackgroundPositionLayer(
                "top".equals(thirdKeyword) ? verticalOffset : ZERO_PERCENT,
                "right".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT,
                "bottom".equals(thirdKeyword) ? verticalOffset : ZERO_PERCENT,
                "left".equals(firstKeyword) ? horizontalOffset : ZERO_PERCENT
        );
    }

    /// Returns JavaFX's top-axis offset for one vertical keyword or numeric position.
    ///
    /// @param keyword   the normalized identifier, or {@code null}
    /// @param component the source component
    /// @param span      the source value span
    /// @return the top-axis offset
    /// @throws BssSerializeException if the component cannot name a vertical position
    private static SassNumber verticalPosition(
            @Nullable String keyword,
            SassValue component,
            SourceSpan span
    ) {
        if (!isPositionKeyword(keyword)) {
            return positionSize(component, span);
        }
        return switch (keyword) {
            case "top" -> ZERO_PERCENT;
            case "bottom" -> ONE_HUNDRED_PERCENT;
            case "center" -> FIFTY_PERCENT;
            default -> throw invalidBackgroundPosition(span);
        };
    }

    /// Returns the lower-case unquoted CSS identifier represented by one position component.
    ///
    /// @param value the source component
    /// @return the normalized identifier, or {@code null} when the component is not an unquoted string
    private static @Nullable String positionKeyword(SassValue value) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            return null;
        }
        return string.text().toLowerCase(Locale.ROOT);
    }

    /// Returns whether a keyword names a JavaFX background-position direction.
    ///
    /// @param keyword the normalized identifier, or {@code null}
    /// @return whether the identifier is left, right, top, bottom, or center
    private static boolean isPositionKeyword(@Nullable String keyword) {
        return keyword != null && switch (keyword) {
            case "left", "right", "top", "bottom", "center" -> true;
            default -> false;
        };
    }

    /// Returns whether a keyword names a vertical background-position edge.
    ///
    /// @param keyword the normalized identifier, or {@code null}
    /// @return whether the identifier is top or bottom
    private static boolean isVerticalPositionKeyword(@Nullable String keyword) {
        return "top".equals(keyword) || "bottom".equals(keyword);
    }

    /// Returns whether a keyword names a horizontal background-position edge.
    ///
    /// @param keyword the normalized identifier, or {@code null}
    /// @return whether the identifier is left or right
    private static boolean isHorizontalPositionKeyword(@Nullable String keyword) {
        return "left".equals(keyword) || "right".equals(keyword);
    }

    /// Returns whether a keyword names a horizontal edge or the center.
    ///
    /// @param keyword the normalized identifier, or {@code null}
    /// @return whether the identifier is left, right, or center
    private static boolean isHorizontalOrCenterPositionKeyword(@Nullable String keyword) {
        return isHorizontalPositionKeyword(keyword) || "center".equals(keyword);
    }

    /// Returns one numeric background-position component.
    ///
    /// @param value the source component
    /// @param span  the source value span
    /// @return the supplied Sass number
    /// @throws BssSerializeException if the component is not a number
    private static SassNumber positionSize(SassValue value, SourceSpan span) {
        if (value instanceof SassNumber number) {
            return number;
        }
        throw invalidBackgroundPosition(span);
    }
    /// Writes JavaFX's layered repeat-structure representation.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBackgroundRepeatLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = backgroundRepeatLayers(value, span);
        writeParsedHeader(output, false, REPEAT_STRUCT_CONVERTER, strings);
        output.writeByte(ARRAY_OF_VALUE_ARRAY);
        output.writeByte(NESTED_VALUE);
        output.writeInt(layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            output.writeInt(2);
            output.writeByte(NESTED_VALUE);
            writeBackgroundRepeatEnum(output, layer.horizontal(), strings);
            output.writeByte(NESTED_VALUE);
            writeBackgroundRepeatEnum(output, layer.vertical(), strings);
        }
    }

    /// Writes one JavaFX `BackgroundRepeat` enum value.
    ///
    /// @param output  the declaration output stream
    /// @param value   the enum constant spelling
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBackgroundRepeatEnum(
            DataOutputStream output,
            String value,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, ENUM_CONVERTER, strings);
        output.writeShort(strings.add(BACKGROUND_REPEAT_ENUM_CLASS));
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(value));
    }

    /// Returns normalized JavaFX repeat-structure layers.
    ///
    /// @param value the evaluated Sass value
    /// @param span  the source value span
    /// @return horizontal and vertical repeat modes in source layer order
    /// @throws BssSerializeException if a layer is not one or two repeat identifiers
    private static @Unmodifiable List<BackgroundRepeatLayer> backgroundRepeatLayers(
            SassValue value,
            SourceSpan span
    ) {
        var values = layeredValues(
                value,
                span,
                "BSS repeat structures require one or more comma-separated repeat layers."
        );
        var layers = new ArrayList<BackgroundRepeatLayer>(values.size());
        for (var layer : values) {
            layers.add(backgroundRepeatLayer(layer, span));
        }
        return List.copyOf(layers);
    }

    /// Normalizes one JavaFX repeat-structure layer.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return the normalized repeat layer
    /// @throws BssSerializeException if the layer cannot be represented
    private static BackgroundRepeatLayer backgroundRepeatLayer(SassValue value, SourceSpan span) {
        var components = backgroundRepeatComponents(value, span);
        var firstKeyword = repeatKeyword(components.get(0), span);
        var first = switch (firstKeyword) {
            case "repeat-x" -> new BackgroundRepeatLayer("REPEAT", "NO_REPEAT");
            case "repeat-y" -> new BackgroundRepeatLayer("NO_REPEAT", "REPEAT");
            case "repeat" -> new BackgroundRepeatLayer("REPEAT", "REPEAT");
            case "space" -> new BackgroundRepeatLayer("SPACE", "SPACE");
            case "round" -> new BackgroundRepeatLayer("ROUND", "ROUND");
            case "no-repeat", "stretch" -> new BackgroundRepeatLayer("NO_REPEAT", "NO_REPEAT");
            default -> throw invalidBackgroundRepeat(span);
        };
        if (components.size() == 1) {
            return first;
        }
        if (firstKeyword.equals("repeat-x") || firstKeyword.equals("repeat-y")) {
            throw invalidBackgroundRepeat(span);
        }
        return new BackgroundRepeatLayer(
                first.horizontal(),
                repeatAxis(repeatKeyword(components.get(1), span), span)
        );
    }

    /// Returns mutable one-or-two identifier components for one repeat-structure layer.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return the source components
    /// @throws BssSerializeException if the layer shape is unsupported
    private static ArrayList<SassValue> backgroundRepeatComponents(SassValue value, SourceSpan span) {
        if (!(value instanceof SassList list)) {
            return new ArrayList<>(List.of(value));
        }
        if (list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()
                || list.contents().size() > 2) {
            throw invalidBackgroundRepeat(span);
        }
        return new ArrayList<>(list.contents());
    }

    /// Returns the JavaFX vertical repeat enum spelling selected by a second repeat identifier.
    ///
    /// @param keyword the normalized repeat identifier
    /// @param span    the source value span
    /// @return the enum constant spelling
    /// @throws BssSerializeException if the identifier is unsupported in second position
    private static String repeatAxis(String keyword, SourceSpan span) {
        return switch (keyword) {
            case "repeat" -> "REPEAT";
            case "space" -> "SPACE";
            case "round" -> "ROUND";
            case "no-repeat", "stretch" -> "NO_REPEAT";
            default -> throw invalidBackgroundRepeat(span);
        };
    }

    /// Returns one lower-case unquoted repeat identifier.
    ///
    /// @param value the source component
    /// @param span  the source value span
    /// @return the normalized identifier
    /// @throws BssSerializeException if the component is not an unquoted identifier
    private static String repeatKeyword(SassValue value, SourceSpan span) {
        if (value instanceof SassString string && !string.hasQuotes()) {
            return string.text().toLowerCase(Locale.ROOT);
        }
        throw invalidBackgroundRepeat(span);
    }
    /// Writes JavaFX's layered background-size representation.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBackgroundSizeLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = backgroundSizeLayers(value, span);
        writeParsedHeader(output, false, LAYERED_BACKGROUND_SIZE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeBackgroundSizeLayer(output, layer, span, strings);
        }
    }

    /// Writes one JavaFX background-size parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param layer   the normalized background-size layer
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBackgroundSizeLayer(
            DataOutputStream output,
            BackgroundSizeLayer layer,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, BACKGROUND_SIZE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 4);
        writeNullableBackgroundSize(output, layer.width(), span, strings);
        writeNullableBackgroundSize(output, layer.height(), span, strings);
        output.writeByte(NESTED_VALUE);
        writeBooleanValue(output, SassBoolean.of(layer.cover()), strings);
        output.writeByte(NESTED_VALUE);
        writeBooleanValue(output, SassBoolean.of(layer.contain()), strings);
    }

    /// Writes one nullable raw JavaFX Size parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param size    the size to serialize, or {@code null} for JavaFX's auto marker
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNullableBackgroundSize(
            DataOutputStream output,
            @Nullable SassNumber size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (size == null) {
            output.writeByte(NULL_VALUE);
            return;
        }
        output.writeByte(NESTED_VALUE);
        writeSizeValue(output, size, span, strings);
    }

    /// Returns normalized JavaFX background-size layers.
    ///
    /// @param value the evaluated Sass value
    /// @param span  the source value span
    /// @return width, height, cover, and contain values in source layer order
    /// @throws BssSerializeException if a layer does not match JavaFX's size grammar
    private static @Unmodifiable List<BackgroundSizeLayer> backgroundSizeLayers(
            SassValue value,
            SourceSpan span
    ) {
        var values = layeredValues(
                value,
                span,
                "BSS background sizes require one or more comma-separated size layers."
        );
        var layers = new ArrayList<BackgroundSizeLayer>(values.size());
        for (var layer : values) {
            layers.add(backgroundSizeLayer(layer, span));
        }
        return List.copyOf(layers);
    }

    /// Normalizes one JavaFX background-size layer.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return the normalized size layer
    /// @throws BssSerializeException if the layer cannot be represented
    private static BackgroundSizeLayer backgroundSizeLayer(SassValue value, SourceSpan span) {
        var components = backgroundSizeComponents(value, span);
        var first = components.get(0);
        @Nullable String firstKeyword = backgroundSizeKeyword(first);
        if ("cover".equals(firstKeyword)) {
            if (components.size() != 1) {
                throw invalidBackgroundSize(span);
            }
            return new BackgroundSizeLayer(null, null, true, false);
        }
        if ("contain".equals(firstKeyword)) {
            if (components.size() != 1) {
                throw invalidBackgroundSize(span);
            }
            return new BackgroundSizeLayer(null, null, false, true);
        }

        @Nullable SassNumber width;
        if ("auto".equals(firstKeyword)) {
            width = null;
        } else if ("stretch".equals(firstKeyword)) {
            width = ONE_HUNDRED_PERCENT;
        } else if (firstKeyword != null) {
            throw invalidBackgroundSize(span);
        } else {
            width = backgroundSizeNumber(first, span);
        }

        @Nullable SassNumber height = "stretch".equals(firstKeyword) ? ONE_HUNDRED_PERCENT : null;
        if (components.size() == 2) {
            var second = components.get(1);
            @Nullable String secondKeyword = backgroundSizeKeyword(second);
            if ("auto".equals(secondKeyword)) {
                height = null;
            } else if ("stretch".equals(secondKeyword)) {
                height = ONE_HUNDRED_PERCENT;
            } else if (secondKeyword != null) {
                throw invalidBackgroundSize(span);
            } else {
                height = backgroundSizeNumber(second, span);
            }
        }
        return new BackgroundSizeLayer(width, height, false, false);
    }

    /// Returns mutable one-or-two components for one background-size layer.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return the source components
    /// @throws BssSerializeException if the layer shape is unsupported
    private static ArrayList<SassValue> backgroundSizeComponents(SassValue value, SourceSpan span) {
        if (!(value instanceof SassList list)) {
            return new ArrayList<>(List.of(value));
        }
        if (list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()
                || list.contents().size() > 2) {
            throw invalidBackgroundSize(span);
        }
        return new ArrayList<>(list.contents());
    }

    /// Returns the lower-case unquoted identifier represented by one size component.
    ///
    /// @param value the source component
    /// @return the normalized identifier, or {@code null} when the component is not an unquoted string
    private static @Nullable String backgroundSizeKeyword(SassValue value) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            return null;
        }
        return string.text().toLowerCase(Locale.ROOT);
    }

    /// Returns one numeric background-size component.
    ///
    /// @param value the source component
    /// @param span  the source value span
    /// @return the supplied Sass number
    /// @throws BssSerializeException if the component is not a number
    private static SassNumber backgroundSizeNumber(SassValue value, SourceSpan span) {
        if (value instanceof SassNumber number) {
            return number;
        }
        throw invalidBackgroundSize(span);
    }

    /// Creates the standard BSS failure for an unsupported background-position layer.
    ///
    /// @param span the source value span
    /// @return the source-associated serialization failure
    private static BssSerializeException invalidBackgroundPosition(SourceSpan span) {
        return new BssSerializeException(
                "BSS background positions require JavaFX-compatible one-to-four-component layers.",
                span,
                null
        );
    }

    /// Creates the standard BSS failure for an unsupported repeat-structure layer.
    ///
    /// @param span the source value span
    /// @return the source-associated serialization failure
    private static BssSerializeException invalidBackgroundRepeat(SourceSpan span) {
        return new BssSerializeException(
                "BSS repeat structures require one or two supported repeat identifiers per layer.",
                span,
                null
        );
    }

    /// Creates the standard BSS failure for an unsupported background-size layer.
    ///
    /// @param span the source value span
    /// @return the source-associated serialization failure
    private static BssSerializeException invalidBackgroundSize(SourceSpan span) {
        return new BssSerializeException(
                "BSS background sizes require JavaFX-compatible one-or-two-component layers.",
                span,
                null
        );
    }

    /// Writes JavaFX's sequence of four-sided border paint layers.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderPaintLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = borderPaintLayers(value, span, strings);
        writeParsedHeader(output, false, LAYERED_BORDER_PAINT_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeParsedHeader(output, false, STROKE_BORDER_PAINT_CONVERTER, strings);
            writeParsedValueArrayPrefix(output, layer.paints().size());
            for (var paint : layer.paints()) {
                output.writeByte(NESTED_VALUE);
                writePaintValue(output, paint, span, strings);
            }
        }
    }

    /// Returns normalized four-sided border paint layers.
    ///
    /// Solid colors, property lookups, JavaFX linear or radial gradients, and
    /// image-pattern paint forms are supported.
    ///
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the property-lookup registry for this declaration
    /// @return the normalized paint layers in source order
    /// @throws BssSerializeException if a layer is not one to four supported paints
    private static @Unmodifiable List<BorderPaintLayer> borderPaintLayers(
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) {
        var values = layeredValues(
                value,
                span,
                "BSS border paints require one or more comma-separated paint layers."
        );
        var layers = new ArrayList<BorderPaintLayer>(values.size());
        for (var layer : values) {
            layers.add(new BorderPaintLayer(expandBorderPaints(
                    borderPaintValues(layer, span, strings)
            )));
        }
        return List.copyOf(layers);
    }

    /// Returns the one-to-four paints supplied for one border paint layer.
    ///
    /// @param value   the evaluated Sass value for one layer
    /// @param span    the source value span
    /// @param strings the property-lookup registry for this declaration
    /// @return the source-order paints
    /// @throws BssSerializeException if the layer is not a supported paint sequence
    private static @Unmodifiable List<JavaFXPaintParser.Paint> borderPaintValues(
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) {
        if (!(value instanceof SassList list)) {
            return List.of(JavaFXPaintParser.parse(
                    value,
                    span,
                    strings::isRegisteredLookupKey
            ));
        }
        if (list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()) {
            throw invalidBorderPaints(span);
        }
        var paints = new ArrayList<JavaFXPaintParser.Paint>(4);
        for (var index = 0; index < list.contents().size();) {
            @Nullable var legacyGradient = JavaFXLegacyGradient.consume(
                    list.contents(),
                    index
            );
            if (legacyGradient != null) {
                paints.add(JavaFXPaintParser.parse(
                        new SassString(legacyGradient.css(), false),
                        span,
                        strings::isRegisteredLookupKey
                ));
                index = legacyGradient.nextIndex();
            } else {
                paints.add(JavaFXPaintParser.parse(
                        list.contents().get(index),
                        span,
                        strings::isRegisteredLookupKey
                ));
                index++;
            }
            if (paints.size() > 4) {
                throw invalidBorderPaints(span);
            }
        }
        return List.copyOf(paints);
    }

    /// Expands CSS one-to-four-value border paint shorthand to four sides.
    ///
    /// @param supplied the one to four supplied paints
    /// @return top, right, bottom, and left paints
    private static @Unmodifiable List<JavaFXPaintParser.Paint> expandBorderPaints(
            List<JavaFXPaintParser.Paint> supplied
    ) {
        return switch (supplied.size()) {
            case 1 -> List.of(supplied.get(0), supplied.get(0), supplied.get(0), supplied.get(0));
            case 2 -> List.of(supplied.get(0), supplied.get(1), supplied.get(0), supplied.get(1));
            case 3 -> List.of(supplied.get(0), supplied.get(1), supplied.get(2), supplied.get(1));
            case 4 -> List.copyOf(supplied);
            default -> throw new AssertionError("validated border paint count is invalid");
        };
    }

    /// Creates the standard BSS failure for an unsupported border paint layer.
    ///
    /// @param span the source value span
    /// @return the source-associated serialization failure
    private static BssSerializeException invalidBorderPaints(SourceSpan span) {
        return new BssSerializeException(
                "BSS border paints require one to four space-separated paint values per layer.",
                span,
                null
        );
    }

    /// Writes JavaFX's layered four-sided border-style values.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderStyleLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = JavaFXBorderStyleParser.parseLayers(value, span);
        writeParsedHeader(output, false, LAYERED_BORDER_STYLE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeParsedHeader(output, false, BORDER_STROKE_STYLE_SEQUENCE_CONVERTER, strings);
            writeParsedValueArrayPrefix(output, layer.styles().size());
            for (var style : layer.styles()) {
                output.writeByte(NESTED_VALUE);
                writeBorderStyleValue(output, style, span, strings);
            }
        }
    }

    /// Writes one JavaFX border stroke style parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param style   the normalized border style
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderStyleValue(
            DataOutputStream output,
            JavaFXBorderStyleParser.BorderStyle style,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, BORDER_STYLE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 6);
        writeBorderStyleDashValue(output, style.dashStyle(), span, strings);
        writeNullableBorderStyleNumericValue(output, style.phase(), span, strings);
        writeNullableBorderStyleEnumValue(output, style.strokeType(), STROKE_TYPE_ENUM_CLASS, strings);
        writeNullableBorderStyleEnumValue(output, style.lineJoin(), STROKE_LINE_JOIN_ENUM_CLASS, strings);
        writeNullableBorderStyleNumericValue(output, style.miterLimit(), span, strings);
        writeNullableBorderStyleEnumValue(output, style.lineCap(), STROKE_LINE_CAP_ENUM_CLASS, strings);
    }

    /// Writes one JavaFX border dash-style parsed value.
    ///
    /// Keyword dash styles are encoded as JavaFX's untyped null parsed values;
    /// JavaFX's BSS reader intentionally uses that same representation for its
    /// predefined dash-style constants.
    ///
    /// @param output    the declaration output stream
    /// @param dashStyle the normalized dash style
    /// @param span      the source value span
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderStyleDashValue(
            DataOutputStream output,
            JavaFXBorderStyleParser.DashStyle dashStyle,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        output.writeByte(NESTED_VALUE);
        if (dashStyle instanceof JavaFXBorderStyleParser.KeywordDashStyle) {
            writeParsedHeader(output, false, null, strings);
            output.writeByte(NULL_VALUE);
            return;
        }
        if (dashStyle instanceof JavaFXBorderStyleParser.SegmentsDashStyle segments) {
            writeParsedHeader(output, false, SIZE_SEQUENCE_CONVERTER, strings);
            writeParsedValueArrayPrefix(output, segments.segments().size());
            for (var size : segments.segments()) {
                output.writeByte(NESTED_VALUE);
                writeBorderStyleSizeValue(output, size, span, strings);
            }
            return;
        }
        throw new AssertionError("unsupported JavaFX border dash style type");
    }

    /// Writes one optional JavaFX size-converted border-style number.
    ///
    /// @param output  the declaration output stream
    /// @param size    the optional raw or lookup size
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNullableBorderStyleNumericValue(
            DataOutputStream output,
            @Nullable JavaFXBorderStyleParser.BorderStyleSize size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (size == null) {
            output.writeByte(NULL_VALUE);
            return;
        }
        output.writeByte(NESTED_VALUE);
        writeParsedHeader(output, false, SIZE_CONVERTER, strings);
        output.writeByte(NESTED_VALUE);
        writeBorderStyleSizeValue(output, size, span, strings);
    }

    /// Writes one optional JavaFX enum-converted border-style value.
    ///
    /// @param output    the declaration output stream
    /// @param constant  the optional lower-case enum spelling
    /// @param enumClass the JavaFX enum class name
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeNullableBorderStyleEnumValue(
            DataOutputStream output,
            @Nullable String constant,
            String enumClass,
            StringStore strings
    ) throws IOException {
        if (constant == null) {
            output.writeByte(NULL_VALUE);
            return;
        }
        output.writeByte(NESTED_VALUE);
        writeEnumConstant(output, enumClass, constant, strings);
    }

    /// Writes one raw or lookup JavaFX border-style size parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param size    the normalized size
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderStyleSizeValue(
            DataOutputStream output,
            JavaFXBorderStyleParser.BorderStyleSize size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (size instanceof JavaFXBorderStyleParser.RawBorderStyleSize raw) {
            writeSizeValue(output, raw.value(), span, strings);
            return;
        }
        if (size instanceof JavaFXBorderStyleParser.LookupBorderStyleSize lookup) {
            writeLookupValue(output, lookup.key(), strings);
            return;
        }
        throw new AssertionError("unsupported JavaFX border-style size type");
    }

    /// Writes JavaFX's layered border-image inset values.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageInsetLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = JavaFXBorderImageParser.parseInsetLayers(value, span);
        writeParsedHeader(output, false, INSETS_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeBorderImageInsetsValue(output, layer, span, strings);
        }
    }

    /// Writes JavaFX's layered border-image slice values.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageSliceLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = JavaFXBorderImageParser.parseSliceLayers(value, span);
        writeParsedHeader(output, false, SLICE_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeBorderImageSliceValue(output, layer, span, strings);
        }
    }

    /// Writes one JavaFX border-image slice layer.
    ///
    /// @param output  the declaration output stream
    /// @param slice   the normalized slice layer
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageSliceValue(
            DataOutputStream output,
            JavaFXBorderImageParser.BorderImageSlice slice,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, BORDER_IMAGE_SLICE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 2);
        output.writeByte(NESTED_VALUE);
        writeBorderImageInsetsValue(output, slice.sizes(), span, strings);
        output.writeByte(NESTED_VALUE);
        writeBorderImageRawBooleanValue(output, slice.fill(), strings);
    }

    /// Writes JavaFX's layered border-image width values.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageWidthLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = JavaFXBorderImageParser.parseWidthLayers(value, span);
        writeParsedHeader(output, false, BORDER_IMAGE_WIDTHS_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeBorderImageWidthValue(output, layer, span, strings);
        }
    }

    /// Writes one JavaFX border-image width layer.
    ///
    /// @param output  the declaration output stream
    /// @param widths  the normalized four-sided width layer
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageWidthValue(
            DataOutputStream output,
            JavaFXBorderImageParser.FourSidedSizes widths,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, BORDER_IMAGE_WIDTH_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, widths.values().size());
        for (var width : widths.values()) {
            output.writeByte(NESTED_VALUE);
            writeBorderImageSizeValue(output, width, span, strings);
        }
    }

    /// Writes one JavaFX four-sided inset parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param insets  the normalized four-sided size layer
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageInsetsValue(
            DataOutputStream output,
            JavaFXBorderImageParser.FourSidedSizes insets,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, INSETS_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, insets.values().size());
        for (var inset : insets.values()) {
            output.writeByte(NESTED_VALUE);
            writeBorderImageSizeValue(output, inset, span, strings);
        }
    }

    /// Writes one raw or lookup JavaFX border-image size parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param size    the normalized size
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageSizeValue(
            DataOutputStream output,
            JavaFXBorderImageParser.SizeValue size,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (size instanceof JavaFXBorderImageParser.RawSizeValue raw) {
            writeSizeValue(output, raw.value(), span, strings);
            return;
        }
        if (size instanceof JavaFXBorderImageParser.LookupSizeValue lookup) {
            writeLookupValue(output, lookup.key(), strings);
            return;
        }
        throw new AssertionError("unsupported JavaFX border-image size type");
    }

    /// Writes one raw JavaFX boolean parsed value without a boolean converter.
    ///
    /// @param output  the declaration output stream
    /// @param value   the boolean value
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderImageRawBooleanValue(
            DataOutputStream output,
            boolean value,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, null, strings);
        output.writeByte(BOOLEAN_VALUE);
        output.writeBoolean(value);
    }

    /// Writes JavaFX's sequence of four-sided border-width layers.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBorderWidthLayers(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = layeredValues(
                value,
                span,
                "BSS border widths require one or more comma-separated size layers."
        );
        writeParsedHeader(output, false, MARGINS_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeMarginsValue(output, layer, span, strings);
        }
    }

    /// Writes JavaFX's four-sided border-width representation.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeMarginsValue(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var values = fourSidedSizeValues(
                value,
                span,
                "BSS border widths require one to four space-separated sizes."
        );
        writeParsedHeader(output, false, MARGINS_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, values.size());
        for (var number : values) {
            output.writeByte(NESTED_VALUE);
            writeSizeValue(output, number, span, strings);
        }
    }

    /// Writes JavaFX's sequence of four-sided inset values.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeLayeredInsetsValue(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = layeredValues(
                value,
                span,
                "BSS layered insets require one or more comma-separated inset values."
        );
        writeParsedHeader(output, false, INSETS_SEQUENCE_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeInsetsValue(output, layer, span, strings);
        }
    }

    /// Writes JavaFX's sequence of two-axis corner-radius values.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeCornerRadiiValue(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var layers = cornerRadiiLayers(value, span);
        writeParsedHeader(output, false, CORNER_RADII_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, layers.size());
        for (var layer : layers) {
            output.writeByte(NESTED_VALUE);
            writeParsedHeader(output, false, null, strings);
            output.writeByte(ARRAY_OF_VALUE_ARRAY);
            output.writeByte(NESTED_VALUE);
            output.writeInt(2);
            writeSizeRow(output, layer.horizontal(), span, strings);
            writeSizeRow(output, layer.vertical(), span, strings);
        }
    }

    /// Returns source-order values for a direct value or an unbracketed comma list.
    ///
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param message the error text for an invalid outer list
    /// @return one or more layer values
    /// @throws BssSerializeException if the outer list is not representable
    private static @Unmodifiable List<SassValue> layeredValues(
            SassValue value,
            SourceSpan span,
            String message
    ) {
        if (!(value instanceof SassList list) || list.separator() != ListSeparator.COMMA) {
            return List.of(value);
        }
        if (list.hasBrackets() || list.contents().isEmpty()) {
            throw new BssSerializeException(message, span, null);
        }
        return List.copyOf(list.contents());
    }

    /// Returns the normalized corner-radius layers represented by one declaration.
    ///
    /// @param value the evaluated Sass value
    /// @param span  the source value span
    /// @return the normalized layers in source order
    /// @throws BssSerializeException if a layer cannot be represented
    private static @Unmodifiable List<CornerRadiiLayer> cornerRadiiLayers(
            SassValue value,
            SourceSpan span
    ) {
        var values = layeredValues(
                value,
                span,
                "BSS corner radii require one or more comma-separated radius layers."
        );
        var layers = new ArrayList<CornerRadiiLayer>(values.size());
        for (var layer : values) {
            layers.add(cornerRadiiLayer(layer, span));
        }
        return List.copyOf(layers);
    }

    /// Parses and normalizes one horizontal-and-vertical corner-radius layer.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return one normalized radius layer
    /// @throws BssSerializeException if the layer has an unsupported shape
    private static CornerRadiiLayer cornerRadiiLayer(SassValue value, SourceSpan span) {
        var values = cornerRadiiValues(value, span);
        var horizontal = new ArrayList<SassNumber>(4);
        var vertical = new ArrayList<SassNumber>(4);
        var sawSlash = false;
        for (var number : values) {
            @Nullable SassNumber numerator = number.slashNumerator();
            @Nullable SassNumber denominator = number.slashDenominator();
            if (numerator == null) {
                if (sawSlash) {
                    vertical.add(number);
                } else {
                    horizontal.add(number);
                }
                continue;
            }
            if (sawSlash) {
                throw invalidCornerRadii(span);
            }
            horizontal.add(numerator);
            vertical.add(Objects.requireNonNull(denominator, "slash denominator"));
            sawSlash = true;
        }
        if (horizontal.isEmpty() || horizontal.size() > 4 || vertical.size() > 4) {
            throw invalidCornerRadii(span);
        }

        var normalizedHorizontal = new ArrayList<>(expandCornerRadii(horizontal));
        var normalizedVertical = sawSlash
                ? new ArrayList<>(expandCornerRadii(vertical))
                : new ArrayList<>(normalizedHorizontal);
        for (var index = 0; index < normalizedHorizontal.size(); index++) {
            if (isJavaFXPixelZero(normalizedHorizontal.get(index))
                    || isJavaFXPixelZero(normalizedVertical.get(index))) {
                var zero = SassNumber.of(0.0, "px");
                normalizedHorizontal.set(index, zero);
                normalizedVertical.set(index, zero);
            }
        }
        return new CornerRadiiLayer(normalizedHorizontal, normalizedVertical);
    }

    /// Returns the one-to-four horizontal or vertical size values for one radius side.
    ///
    /// @param value the evaluated Sass value for one layer
    /// @param span  the source value span
    /// @return the supplied source-order size values
    /// @throws BssSerializeException if the value is not a size sequence
    private static @Unmodifiable List<SassNumber> cornerRadiiValues(
            SassValue value,
            SourceSpan span
    ) {
        if (value instanceof SassNumber number) {
            return List.of(number);
        }
        if (!(value instanceof SassList list)
                || list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()) {
            throw invalidCornerRadii(span);
        }
        var values = new ArrayList<SassNumber>(list.contents().size());
        for (var item : list.contents()) {
            if (!(item instanceof SassNumber number)) {
                throw invalidCornerRadii(span);
            }
            values.add(number);
        }
        return List.copyOf(values);
    }

    /// Expands CSS one-to-four-value corner-radius shorthand to four corners.
    ///
    /// @param supplied the one to four supplied sizes
    /// @return top-left, top-right, bottom-right, and bottom-left sizes
    private static @Unmodifiable List<SassNumber> expandCornerRadii(List<SassNumber> supplied) {
        return switch (supplied.size()) {
            case 1 -> List.of(supplied.get(0), supplied.get(0), supplied.get(0), supplied.get(0));
            case 2 -> List.of(supplied.get(0), supplied.get(1), supplied.get(0), supplied.get(1));
            case 3 -> List.of(supplied.get(0), supplied.get(1), supplied.get(2), supplied.get(1));
            case 4 -> List.copyOf(supplied);
            default -> throw new AssertionError("validated corner radius count is invalid");
        };
    }

    /// Returns whether JavaFX parses the size as its canonical zero-pixel value.
    ///
    /// @param number the Sass size
    /// @return whether the parsed JavaFX size equals zero pixels
    private static boolean isJavaFXPixelZero(SassNumber number) {
        return number.value() == 0.0
                && number.denominatorUnits().isEmpty()
                && (number.isUnitless()
                || number.numeratorUnits().size() == 1
                && number.numeratorUnits().get(0).equalsIgnoreCase("px"));
    }

    /// Creates the standard BSS failure for an unsupported corner-radius layer.
    ///
    /// @param span the source value span
    /// @return the source-associated serialization failure
    private static BssSerializeException invalidCornerRadii(SourceSpan span) {
        return new BssSerializeException(
                "BSS corner radii require one to four space-separated sizes on each side of '/'.",
                span,
                null
        );
    }

    /// Writes an array header for non-null nested parsed values.
    ///
    /// @param output the declaration output stream
    /// @param count  the number of nested values
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeParsedValueArrayPrefix(DataOutputStream output, int count) throws IOException {
        output.writeByte(VALUE_ARRAY);
        output.writeByte(NESTED_VALUE);
        output.writeInt(count);
    }

    /// Writes one non-null row of nested raw size values.
    ///
    /// @param output  the declaration output stream
    /// @param values  the source-order size values
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeSizeRow(
            DataOutputStream output,
            List<SassNumber> values,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        output.writeByte(NESTED_VALUE);
        output.writeInt(values.size());
        for (var value : values) {
            output.writeByte(NESTED_VALUE);
            writeSizeValue(output, value, span, strings);
        }
    }

    /// Writes one string parsed value with JavaFX lookup semantics.
    ///
    /// @param output   the declaration output stream
    /// @param property the CSS property name
    /// @param string   the evaluated Sass string
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeStringValue(
            DataOutputStream output,
            String property,
            SassString string,
            StringStore strings
    ) throws IOException {
        var text = string.text();
        var specialKeyword = isGlobalKeyword(string);
        var fontFamily = property.endsWith("font-family");
        var serializedText = text.equalsIgnoreCase("none") && !string.hasQuotes()
                ? "null"
                : string.hasQuotes() && !fontFamily ? text : string.toCssString();
        @Nullable String converter = !specialKeyword && fontFamily ? STRING_CONVERTER : null;
        var lookup = !fontFamily && !string.hasQuotes() && !specialKeyword;
        if (lookup) {
            serializedText = strings.normalizeLookupKey(serializedText);
        }
        writeParsedHeader(output, lookup, converter, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(serializedText));
    }

    /// Writes one string through JavaFX's generic scalar-token recognition.
    ///
    /// @param output   the declaration output stream
    /// @param property the CSS property name
    /// @param string   the evaluated Sass string
    /// @param span     the source value span
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeGenericStringValue(
            DataOutputStream output,
            String property,
            SassString string,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var lower = string.text().toLowerCase(Locale.ROOT);
        switch (lower) {
            case "indefinite" -> writeIndefiniteDurationValue(output, strings);
            case "infinity" -> {
                writeParsedHeader(output, false, SIZE_CONVERTER, strings);
                output.writeByte(NESTED_VALUE);
                writeRawSizeValue(output, Double.MAX_VALUE, strings);
            }
            case "true", "false" -> {
                writeParsedHeader(output, false, BOOLEAN_CONVERTER, strings);
                output.writeByte(STRING_VALUE);
                output.writeShort(strings.add(lower));
            }
            default -> {
                if (string.hasQuotes()
                        || !strings.isRegisteredLookupKey(string.text())) {
                    @Nullable JavaFXPaintParser.SolidPaint color =
                            JavaFXPaintParser.tryParseSolidColor(string.text(), span);
                    if (color != null) {
                        writeColorValue(output, color.color(), span, strings);
                        return;
                    }
                }
                if (!string.hasQuotes()
                        && JavaFXValueFunction.invocationName(string.text()) != null) {
                    throw new BssSerializeException(
                            "BSS output doesn't support this JavaFX value function.",
                            span,
                            null
                    );
                }
                writeStringValue(output, property, string, strings);
            }
        }
    }

    /// Writes JavaFX's font-smoothing payload without treating the {@code gray}
    /// keyword as a Sass color.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated declaration value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeFontSmoothingValue(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (value instanceof SassString string && !string.hasQuotes()) {
            writePlainStringValue(output, string.text(), strings);
            return;
        }
        if (value instanceof SassColor color && color.toString().equalsIgnoreCase("gray")) {
            writePlainStringValue(output, "gray", strings);
            return;
        }
        throw new BssSerializeException(
                "BSS font smoothing requires an unquoted identifier.",
                span,
                null
        );
    }

    /// Writes one JavaFX string value without a converter or property lookup.
    ///
    /// @param output  the declaration output stream
    /// @param value   the JavaFX font-smoothing identifier
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writePlainStringValue(
            DataOutputStream output,
            String value,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, null, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(value));
    }

    /// Writes JavaFX's four-slot font shorthand representation.
    ///
    /// The slots contain family, size, weight, and posture. JavaFX validates
    /// but does not retain the optional line-height or `small-caps` variant.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated font shorthand
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeFontValue(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var font = parseFontShorthand(value, span);
        writeParsedHeader(output, false, FONT_CONVERTER, strings);
        writeParsedValueArrayPrefix(output, 4);

        output.writeByte(NESTED_VALUE);
        writeParsedHeader(output, false, STRING_CONVERTER, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(fontFamilyText(font.family())));

        output.writeByte(NESTED_VALUE);
        writeNumberValue(output, "-fx-font-size", font.size(), span, strings);

        if (font.weight() == null) {
            output.writeByte(NULL_VALUE);
        } else {
            output.writeByte(NESTED_VALUE);
            writeFontKeywordValue(
                    output,
                    "-fx-font-weight",
                    font.weight(),
                    span,
                    strings
            );
        }

        if (font.style() == null) {
            output.writeByte(NULL_VALUE);
        } else {
            output.writeByte(NESTED_VALUE);
            writeFontKeywordValue(
                    output,
                    "-fx-font-style",
                    font.style(),
                    span,
                    strings
            );
        }
    }

    /// Parses the JavaFX font shorthand from its evaluated Sass list.
    ///
    /// @param value the evaluated declaration value
    /// @param span  the source value span
    /// @return the family, size, optional weight, and optional style
    /// @throws BssSerializeException if the value does not follow JavaFX's grammar
    private static FontShorthand parseFontShorthand(
            SassValue value,
            SourceSpan span
    ) {
        if (!(value instanceof SassList list)
                || list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().size() < 2) {
            throw invalidFontShorthand(span);
        }
        var terms = list.contents();
        var familyValue = terms.get(terms.size() - 1);
        if (!(familyValue instanceof SassString family)) {
            throw invalidFontShorthand(span);
        }

        var sizeIndex = terms.size() - 2;
        var size = fontShorthandSize(terms.get(sizeIndex), span);
        @Nullable SassValue weight = null;
        @Nullable SassValue style = null;
        var sawVariant = false;

        // OpenJFX parses the optional prefix backwards after locating the
        // mandatory size and family terms.
        for (var index = sizeIndex - 1; index >= 0; index--) {
            var term = terms.get(index);
            if (!(term instanceof SassString keyword) || keyword.hasQuotes()) {
                throw invalidFontShorthand(span);
            }
            var normalized = keyword.text().toLowerCase(Locale.ROOT);
            if (style == null && switch (normalized) {
                case "normal", "italic", "oblique" -> true;
                default -> false;
            }) {
                style = keyword;
            } else if (!sawVariant && normalized.equals("small-caps")) {
                sawVariant = true;
            } else if (weight == null && switch (normalized) {
                case "normal", "bold", "bolder", "lighter" -> true;
                default -> false;
            }) {
                weight = keyword;
            } else {
                throw invalidFontShorthand(span);
            }
        }
        return new FontShorthand(family, size, weight, style);
    }

    /// Returns the size retained by a font shorthand.
    ///
    /// @param value the size term or size/line-height slash list
    /// @param span  the source value span
    /// @return the font size before an optional line height
    /// @throws BssSerializeException if either size is malformed
    private static SassNumber fontShorthandSize(
            SassValue value,
            SourceSpan span
    ) {
        if (value instanceof SassNumber number) {
            if (number.slashNumerator() != null
                    && number.slashDenominator() != null) {
                var numerator = number.slashNumerator();
                var denominator = number.slashDenominator();
                if (!isFontSize(numerator) || !isFontSize(denominator)) {
                    throw invalidFontShorthand(span);
                }
                return numerator;
            }
            @Nullable var size = normalizedFontSize(number);
            if (size != null) {
                return size;
            }
        }
        if (value instanceof SassList slash
                && !slash.hasBrackets()
                && slash.separator() == ListSeparator.SLASH
                && slash.contents().size() == 2) {
            @Nullable var size = normalizedFontSize(slash.contents().get(0));
            @Nullable var lineHeight = normalizedFontSize(
                    slash.contents().get(1)
            );
            if (size != null && lineHeight != null) {
                return size;
            }
        }
        if (value instanceof SassString slashText && !slashText.hasQuotes()) {
            var separator = slashText.text().indexOf('/');
            if (separator > 0
                    && separator == slashText.text().lastIndexOf('/')
                    && separator < slashText.text().length() - 1) {
                @Nullable var size = normalizedFontSize(new SassString(
                        slashText.text().substring(0, separator).trim(),
                        false
                ));
                @Nullable var lineHeight = normalizedFontSize(new SassString(
                        slashText.text().substring(separator + 1).trim(),
                        false
                ));
                if (size != null && lineHeight != null) {
                    return size;
                }
            }
        }
        @Nullable var keywordSize = normalizedFontSize(value);
        if (keywordSize != null) {
            return keywordSize;
        }
        throw invalidFontShorthand(span);
    }

    /// Returns whether a number is accepted by OpenJFX's font-size grammar.
    ///
    /// @param number the candidate size
    /// @return whether the number has at most one supported font-size unit
    private static boolean isFontSize(SassNumber number) {
        if (!Double.isFinite(number.value())
                || !number.denominatorUnits().isEmpty()
                || number.numeratorUnits().size() > 1) {
            return false;
        }
        if (number.numeratorUnits().isEmpty()) {
            return true;
        }
        return switch (number.numeratorUnits().get(0).toLowerCase(Locale.ROOT)) {
            case "%", "em", "ex", "px", "cm", "mm", "in", "pt", "pc" -> true;
            default -> false;
        };
    }

    /// Returns the string stored by JavaFX's font-family parser.
    ///
    /// @param family the parsed family token
    /// @return the original CSS token, except for normalized generic families
    private static String fontFamilyText(SassString family) {
        if (family.hasQuotes()) {
            return family.toCssString();
        }
        var lower = family.text().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "inherit", "serif", "sans-serif", "cursive", "fantasy", "monospace" -> lower;
            default -> family.text();
        };
    }

    /// Creates the standard failure for an invalid JavaFX font shorthand.
    ///
    /// @param span the source value span
    /// @return the source-associated failure
    private static BssSerializeException invalidFontShorthand(SourceSpan span) {
        return new BssSerializeException(
                "BSS font shorthand requires optional style, small-caps, and"
                        + " weight identifiers followed by a size, optional"
                        + " line height, and one font family.",
                span,
                null
        );
    }

    /// Writes one font style or font weight value.
    ///
    /// @param output   the declaration output stream
    /// @param property the CSS property name
    /// @param value    the evaluated declaration value
    /// @param span     the source value span
    /// @param strings  the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeFontKeywordValue(
            DataOutputStream output,
            String property,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var style = isFontStyleProperty(property);
        var converter = style ? FONT_STYLE_CONVERTER : FONT_WEIGHT_CONVERTER;
        var serialized = style
                ? canonicalFontStyle(value, span)
                : canonicalFontWeight(value, span);
        writeParsedHeader(output, false, converter, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(serialized));
    }

    /// Canonicalizes a JavaFX font-style CSS value for BSS storage.
    ///
    /// @param value the evaluated declaration value
    /// @param span  the source value span
    /// @return the JavaFX font posture spelling
    /// @throws BssSerializeException if the value is not a supported font-style identifier
    private static String canonicalFontStyle(SassValue value, SourceSpan span) {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            throw new BssSerializeException(
                    "BSS font style requires an unquoted identifier.",
                    span,
                    null
            );
        }
        return switch (string.text().toLowerCase(Locale.ROOT)) {
            case "normal" -> "REGULAR";
            case "italic", "oblique" -> "ITALIC";
            default -> throw new BssSerializeException(
                    "BSS output doesn't support this JavaFX font style.",
                    span,
                    null
            );
        };
    }

    /// Canonicalizes a JavaFX font-weight CSS value for BSS storage.
    ///
    /// @param value the evaluated declaration value
    /// @param span  the source value span
    /// @return the JavaFX font weight spelling
    /// @throws BssSerializeException if the value is not a supported font-weight identifier or number
    private static String canonicalFontWeight(SassValue value, SourceSpan span) {
        if (value instanceof SassString string && !string.hasQuotes()) {
            return switch (string.text().toLowerCase(Locale.ROOT)) {
                case "normal" -> "NORMAL";
                case "bold", "bolder" -> "BOLD";
                case "lighter" -> "LIGHT";
                default -> throw new BssSerializeException(
                        "BSS output doesn't support this JavaFX font weight.",
                        span,
                        null
                );
            };
        }
        if (value instanceof SassNumber number) {
            return canonicalNumericFontWeight(number, span);
        }
        throw new BssSerializeException(
                "BSS font weight requires an unquoted identifier or a unitless 100-to-900 value.",
                span,
                null
        );
    }

    /// Canonicalizes one numeric JavaFX font weight for BSS storage.
    ///
    /// @param number the evaluated Sass number
    /// @param span   the source value span
    /// @return the JavaFX font weight spelling
    /// @throws BssSerializeException if the number is not one of JavaFX's unitless CSS weights
    private static String canonicalNumericFontWeight(SassNumber number, SourceSpan span) {
        var numericValue = number.value();
        if (!number.isUnitless()
                || !Double.isFinite(numericValue)
                || numericValue < 100.0
                || numericValue > 900.0
                || numericValue % 100.0 != 0.0) {
            throw new BssSerializeException(
                    "BSS font weight requires a unitless value from 100 to 900 in steps of 100.",
                    span,
                    null
            );
        }
        return switch ((int) numericValue) {
            case 100 -> "THIN";
            case 200 -> "EXTRA_LIGHT";
            case 300 -> "LIGHT";
            case 400 -> "NORMAL";
            case 500 -> "MEDIUM";
            case 600 -> "SEMI_BOLD";
            case 700 -> "BOLD";
            case 800 -> "EXTRA_BOLD";
            case 900 -> "BLACK";
            default -> throw new AssertionError("validated font weight is unsupported");
        };
    }

    /// Writes one JavaFX enum parsed value.
    ///
    /// @param output    the declaration output stream
    /// @param value     the evaluated declaration value
    /// @param enumClass the JavaFX enum class name
    /// @param span      the source value span
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeEnumValue(
            DataOutputStream output,
            SassValue value,
            String enumClass,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (!(value instanceof SassString string) || string.hasQuotes()) {
            throw new BssSerializeException(
                    "BSS enum values require an unquoted identifier.",
                    span,
                    null
            );
        }
        writeParsedHeader(output, false, ENUM_CONVERTER, strings);
        output.writeShort(strings.add(enumClass));
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(string.text()));
    }

    /// Writes JavaFX's stroke-dash-array size sequence representation.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated declaration value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeStrokeDashArray(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var values = dashArrayValues(value, span);
        writeParsedHeader(output, false, SIZE_SEQUENCE_CONVERTER, strings);
        output.writeByte(VALUE_ARRAY);
        output.writeByte(NESTED_VALUE);
        output.writeInt(values.size());
        for (var number : values) {
            output.writeByte(NESTED_VALUE);
            writeSizeValue(output, number, span, strings);
        }
    }

    /// Returns a non-empty sequence of sizes for a stroke dash array.
    ///
    /// @param value the evaluated declaration value
    /// @param span  the source value span
    /// @return the source-order sequence of size values
    private static List<SassNumber> dashArrayValues(SassValue value, SourceSpan span) {
        if (value instanceof SassNumber number) {
            return List.of(number);
        }
        if (!(value instanceof SassList list)
                || list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()) {
            throw new BssSerializeException(
                    "BSS stroke dash arrays require one or more space-separated sizes.",
                    span,
                    null
            );
        }
        var values = new ArrayList<SassNumber>(list.contents().size());
        for (var item : list.contents()) {
            if (!(item instanceof SassNumber number)) {
                throw new BssSerializeException(
                        "BSS stroke dash arrays require one or more space-separated sizes.",
                        span,
                        null
                );
            }
            values.add(number);
        }
        return List.copyOf(values);
    }

    /// Writes JavaFX's boolean converter representation.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass boolean
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeBooleanValue(
            DataOutputStream output,
            SassBoolean value,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, BOOLEAN_CONVERTER, strings);
        output.writeByte(STRING_VALUE);
        output.writeShort(strings.add(Boolean.toString(value.value())));
    }
    /// Writes JavaFX's four-sided insets representation.
    ///
    /// @param output  the declaration output stream
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeInsetsValue(
            DataOutputStream output,
            SassValue value,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        var values = insetsValues(value, span);
        writeParsedHeader(output, false, INSETS_CONVERTER, strings);
        output.writeByte(VALUE_ARRAY);
        output.writeByte(NESTED_VALUE);
        output.writeInt(values.size());
        for (var number : values) {
            output.writeByte(NESTED_VALUE);
            writeSizeValue(output, number, span, strings);
        }
    }

    /// Returns four size values using JavaFX's CSS shorthand expansion rules.
    ///
    /// @param value the evaluated Sass value
    /// @param span  the source value span
    /// @return the expanded top, right, bottom, and left values
    private static @Unmodifiable List<SassNumber> insetsValues(SassValue value, SourceSpan span) {
        return fourSidedSizeValues(
                value,
                span,
                "BSS insets require one to four space-separated sizes."
        );
    }

    /// Expands one to four unbracketed Sass sizes using CSS four-sided shorthand rules.
    ///
    /// @param value   the evaluated Sass value
    /// @param span    the source value span
    /// @param message the error text for an unsupported value shape
    /// @return the expanded top, right, bottom, and left values
    /// @throws BssSerializeException if the value is not one to four Sass sizes
    private static @Unmodifiable List<SassNumber> fourSidedSizeValues(
            SassValue value,
            SourceSpan span,
            String message
    ) {
        if (value instanceof SassNumber number) {
            return List.of(number, number, number, number);
        }
        if (!(value instanceof SassList list)
                || list.hasBrackets()
                || list.separator() != ListSeparator.SPACE
                || list.contents().isEmpty()
                || list.contents().size() > 4) {
            throw new BssSerializeException(message, span, null);
        }
        var supplied = new ArrayList<SassNumber>(list.contents().size());
        for (var item : list.contents()) {
            if (!(item instanceof SassNumber number)) {
                throw new BssSerializeException(message, span, null);
            }
            supplied.add(number);
        }
        return switch (supplied.size()) {
            case 1 -> List.of(supplied.get(0), supplied.get(0), supplied.get(0), supplied.get(0));
            case 2 -> List.of(supplied.get(0), supplied.get(1), supplied.get(0), supplied.get(1));
            case 3 -> List.of(supplied.get(0), supplied.get(1), supplied.get(2), supplied.get(1));
            case 4 -> List.copyOf(supplied);
            default -> throw new AssertionError("validated four-sided size count is invalid");
        };
    }

    /// Writes a raw JavaFX Size parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param number  the evaluated Sass number
    /// @param span    the source value span
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeSizeValue(
            DataOutputStream output,
            SassNumber number,
            SourceSpan span,
            StringStore strings
    ) throws IOException {
        if (!Double.isFinite(number.value())) {
            throw new BssSerializeException(
                    "BSS output doesn't support non-finite sizes.",
                    span,
                    null
            );
        }
        writeParsedHeader(output, false, null, strings);
        output.writeByte(SIZE_VALUE);
        output.writeLong(Double.doubleToLongBits(number.value()));
        output.writeShort(strings.add(sizeUnit(number, span)));
    }

    /// Writes one raw JavaFX Size parsed value.
    ///
    /// @param output  the declaration output stream
    /// @param value   the numeric size value, including positive infinity
    /// @param strings the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeRawSizeValue(
            DataOutputStream output,
            double value,
            StringStore strings
    ) throws IOException {
        writeParsedHeader(output, false, null, strings);
        output.writeByte(SIZE_VALUE);
        output.writeLong(Double.doubleToLongBits(value));
        output.writeShort(strings.add("PX"));
    }

    /// Returns the JavaFX SizeUnits enum name for one Sass number.
    ///
    /// @param number the evaluated Sass number
    /// @param span   the source value span
    /// @return the JavaFX SizeUnits enum spelling
    private static String sizeUnit(SassNumber number, SourceSpan span) {
        if (!number.denominatorUnits().isEmpty() || number.numeratorUnits().size() > 1) {
            throw new BssSerializeException(
                    "BSS output supports only a single size unit.",
                    span,
                    null
            );
        }
        if (number.numeratorUnits().isEmpty()) {
            return "PX";
        }
        return switch (number.numeratorUnits().get(0).toLowerCase(Locale.ROOT)) {
            case "%" -> "PERCENT";
            case "em" -> "EM";
            case "ex" -> "EX";
            case "px" -> "PX";
            case "cm" -> "CM";
            case "mm" -> "MM";
            case "in" -> "IN";
            case "pt" -> "PT";
            case "pc" -> "PC";
            case "deg" -> "DEG";
            case "grad" -> "GRAD";
            case "rad" -> "RAD";
            case "turn" -> "TURN";
            case "s" -> "S";
            case "ms" -> "MS";
            default -> throw new BssSerializeException(
                    "BSS output doesn't support this size unit.",
                    span,
                    null
            );
        };
    }

    /// Writes the common BSS parsed-value header.
    ///
    /// @param output    the declaration output stream
    /// @param lookup    whether JavaFX resolves the value as a property lookup
    /// @param converter the JavaFX converter class name, or {@code null}
    /// @param strings   the shared string table
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeParsedHeader(
            DataOutputStream output,
            boolean lookup,
            @Nullable String converter,
            StringStore strings
    ) throws IOException {
        output.writeBoolean(lookup);
        output.writeBoolean(converter != null);
        if (converter != null) {
            output.writeShort(strings.add(converter));
        }
    }

    /// Writes a short-backed BSS count after checking its format limit.
    ///
    /// @param output the output stream
    /// @param count  the non-negative item count
    /// @param span   the source range associated with the count
    /// @param label  the count's human-readable subject
    /// @throws IOException if an in-memory output stream rejects a write
    private static void writeShortCount(
            DataOutputStream output,
            int count,
            SourceSpan span,
            String label
    ) throws IOException {
        if (count < 0 || count > MAXIMUM_SHORT_VALUE) {
            throw new BssSerializeException(
                    "BSS output supports at most " + MAXIMUM_SHORT_VALUE + " " + label + ".",
                    span,
                    null
            );
        }
        output.writeShort(count);
    }

    /// Creates a serialization failure for an unsupported CSS node.
    ///
    /// @param node     the unsupported node
    /// @param position the node's structural position
    /// @return the serialization failure
    private static BssSerializeException unsupported(CssNode node, String position) {
        return new BssSerializeException(
                "BSS output doesn't support this " + position + ".",
                node.span(),
                null
        );
    }

    /// Creates a serialization failure for an opaque at-rule.
    ///
    /// @param rule the unsupported at-rule
    /// @return the serialization failure
    private static BssSerializeException unsupportedAtRule(CssUnknownAtRule rule) {
        return new BssSerializeException(
                "BSS output doesn't support @" + rule.name() + " rules.",
                rule.span(),
                null
        );
    }

    /// Stores one normalized JavaFX background-position layer.
    ///
    /// @param top    the top offset
    /// @param right  the right offset
    /// @param bottom the bottom offset
    /// @param left   the left offset
    @NotNullByDefault
    private record BackgroundPositionLayer(
            SassNumber top,
            SassNumber right,
            SassNumber bottom,
            SassNumber left
    ) {
        /// Creates an immutable background-position layer.
        private BackgroundPositionLayer {
            Objects.requireNonNull(top, "top");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(bottom, "bottom");
            Objects.requireNonNull(left, "left");
        }

        /// Returns the offsets in JavaFX top, right, bottom, left order.
        ///
        /// @return an immutable offset list
        private @Unmodifiable List<SassNumber> offsets() {
            return List.of(top, right, bottom, left);
        }
    }

    /// Stores one normalized JavaFX repeat-structure layer.
    ///
    /// @param horizontal the horizontal `BackgroundRepeat` enum constant
    /// @param vertical   the vertical `BackgroundRepeat` enum constant
    @NotNullByDefault
    private record BackgroundRepeatLayer(String horizontal, String vertical) {
        /// Creates an immutable repeat-structure layer.
        private BackgroundRepeatLayer {
            Objects.requireNonNull(horizontal, "horizontal");
            Objects.requireNonNull(vertical, "vertical");
        }
    }

    /// Stores one normalized JavaFX background-size layer.
    ///
    /// @param width   the width size, or {@code null} for auto
    /// @param height  the height size, or {@code null} for auto
    /// @param cover   whether JavaFX must preserve aspect ratio while covering the region
    /// @param contain whether JavaFX must preserve aspect ratio while containing the image
    @NotNullByDefault
    private record BackgroundSizeLayer(
            @Nullable SassNumber width,
            @Nullable SassNumber height,
            boolean cover,
            boolean contain
    ) {
        /// Creates an immutable background-size layer.
        ///
        /// @throws IllegalArgumentException if cover and contain are both enabled
        private BackgroundSizeLayer {
            if (cover && contain) {
                throw new IllegalArgumentException("background size cannot be both cover and contain");
            }
        }
    }

    /// Stores one normalized four-sided border paint layer.
    ///
    /// @param paints the top, right, bottom, and left paints
    @NotNullByDefault
    private record BorderPaintLayer(@Unmodifiable List<JavaFXPaintParser.Paint> paints) {
        /// Creates an immutable normalized border paint layer.
        ///
        /// @throws IllegalArgumentException if the layer does not contain exactly four paints
        private BorderPaintLayer {
            paints = List.copyOf(paints);
            if (paints.size() != 4) {
                throw new IllegalArgumentException("border paints require four paints");
            }
        }
    }

    /// Stores one normalized layer of horizontal and vertical corner radii.
    ///
    /// @param horizontal the four top-left, top-right, bottom-right, and bottom-left horizontal sizes
    /// @param vertical   the four top-left, top-right, bottom-right, and bottom-left vertical sizes
    @NotNullByDefault
    private record CornerRadiiLayer(
            @Unmodifiable List<SassNumber> horizontal,
            @Unmodifiable List<SassNumber> vertical
    ) {
        /// Creates an immutable normalized corner-radius layer.
        ///
        /// @throws IllegalArgumentException if either axis does not contain exactly four values
        private CornerRadiiLayer {
            horizontal = List.copyOf(horizontal);
            vertical = List.copyOf(vertical);
            if (horizontal.size() != 4 || vertical.size() != 4) {
                throw new IllegalArgumentException("corner radii require four values per axis");
            }
        }
    }

    /// Holds all BSS-ready top-level stylesheet content.
    ///
    /// @param imports   the resolved JavaFX 27 stylesheet imports
    /// @param rules     the ordinary style rules
    /// @param fontFaces the JavaFX font faces
    @NotNullByDefault
    private record BssStylesheet(
            @Unmodifiable List<BssImport> imports,
            @Unmodifiable List<BssRule> rules,
            @Unmodifiable List<BssFontFace> fontFaces
    ) {
        /// Creates one immutable stylesheet content snapshot.
        private BssStylesheet {
            imports = List.copyOf(imports);
            rules = List.copyOf(rules);
            fontFaces = List.copyOf(fontFaces);
        }
    }

    /// Holds one resolved stylesheet import ready for BSS v9 encoding.
    ///
    /// @param conditions the import media-query list
    /// @param stylesheet the resolved imported stylesheet
    /// @param span       the source range of the import rule
    @NotNullByDefault
    private record BssImport(
            JavaFXMediaQuery conditions,
            BssStylesheet stylesheet,
            SourceSpan span
    ) {
        /// Validates import components.
        private BssImport {
            Objects.requireNonNull(conditions, "conditions");
            Objects.requireNonNull(stylesheet, "stylesheet");
            Objects.requireNonNull(span, "span");
        }
    }

    /// Holds one JavaFX font-face payload ready for BSS encoding.
    ///
    /// @param descriptors the JavaFX descriptor map
    /// @param sources     the JavaFX source list
    /// @param span        the source range of the font-face rule
    @NotNullByDefault
    private record BssFontFace(
            @Unmodifiable Map<String, String> descriptors,
            @Unmodifiable List<JavaFXFontFaceParser.Source> sources,
            SourceSpan span
    ) {
        /// Creates one immutable font-face snapshot.
        private BssFontFace {
            descriptors = Collections.unmodifiableMap(new HashMap<>(descriptors));
            sources = List.copyOf(sources);
            Objects.requireNonNull(span, "span");
        }
    }

    /// Tracks declaration names encountered while collecting one source stylesheet.
    ///
    /// Imported stylesheets use independent registries because OpenJFX parses
    /// each imported document with a new CSS parser.
    @NotNullByDefault
    private static final class JavaFXPropertyRegistry {
        /// Contains lower-case declaration names seen in source order.
        private final Set<String> properties = new HashSet<>();

        /// Registers one declaration before its value is interpreted.
        ///
        /// @param declaration the source declaration
        /// @return the declaration with its canonical name and visible lookup names
        private BssDeclaration register(CssDeclaration declaration) {
            Objects.requireNonNull(declaration, "declaration");
            requireDeclarationName(declaration);
            var property = declaration.name().value().toLowerCase(Locale.ROOT);
            properties.add(property);
            return new BssDeclaration(declaration, property, properties);
        }
    }

    /// Holds one declaration and the property names visible while parsing it.
    ///
    /// @param source           the evaluated source declaration
    /// @param property         the lower-case JavaFX property name
    /// @param lookupProperties the lower-case property names registered through
    ///                         this declaration
    @NotNullByDefault
    private record BssDeclaration(
            CssDeclaration source,
            String property,
            @Unmodifiable Set<String> lookupProperties
    ) {
        /// Creates an immutable declaration snapshot.
        private BssDeclaration {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(property, "property");
            lookupProperties = Set.copyOf(lookupProperties);
        }
    }

    /// Holds one style rule ready for BSS encoding.
    ///
    /// @param selectors    the resolved selector list
    /// @param declarations the source-order declarations
    /// @param span         the rule source range
    @NotNullByDefault
    private record BssRule(
            SelectorList selectors,
            @Unmodifiable List<BssDeclaration> declarations,
            @Nullable BssMediaRule mediaRule,
            SourceSpan span
    ) {
        /// Creates an immutable BSS rule snapshot.
        private BssRule {
            Objects.requireNonNull(selectors, "selectors");
            declarations = List.copyOf(declarations);
            Objects.requireNonNull(span, "span");
        }
    }

    /// Holds one media rule attached to a binary style rule.
    ///
    /// @param query  the media-query list
    /// @param parent the enclosing media rule, or `null`
    @NotNullByDefault
    private record BssMediaRule(
            JavaFXMediaQuery query,
            @Nullable BssMediaRule parent
    ) {
        /// Validates the media-query list.
        private BssMediaRule {
            Objects.requireNonNull(query, "query");
        }
    }

    /// Holds one declaration value and its CSS importance flag.
    ///
    /// @param value     the value excluding a trailing priority token
    /// @param important whether JavaFX must treat the declaration as important
    @NotNullByDefault
    private record DeclarationValue(SassValue value, boolean important) {
        /// Creates one declaration value snapshot.
        private DeclarationValue {
            Objects.requireNonNull(value, "value");
        }
    }

    /// Stores the retained components of one JavaFX font shorthand.
    ///
    /// @param family the single font family token
    /// @param size   the font size
    /// @param weight the optional font-weight token, or {@code null}
    /// @param style  the optional font-style token, or {@code null}
    @NotNullByDefault
    private record FontShorthand(
            SassString family,
            SassNumber size,
            @Nullable SassValue weight,
            @Nullable SassValue style
    ) {
        /// Validates retained font components.
        private FontShorthand {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(size, "size");
        }
    }

    /// Interns BSS strings in their encounter order.
    @NotNullByDefault
    private static final class StringStore {
        /// Contains the BSS version whose class names are encoded.
        private final int version;

        /// Contains declaration names visible while encoding the current value.
        private @Unmodifiable Set<String> lookupProperties = Set.of();

        /// Maps non-null strings to their BSS indices.
        private final Map<String, Integer> indices = new HashMap<>();

        /// Contains interned strings, including an optional null entry.
        private final ArrayList<@Nullable String> values = new ArrayList<>();

        /// Contains the index reserved for {@code null}, or {@code null} when absent.
        private @Nullable Integer nullIndex;

        /// Contains the root source range used by capacity diagnostics.
        private final SourceSpan span;

        /// Creates an empty string table.
        ///
        /// @param version the selected BSS format version
        /// @param span    the root source range
        private StringStore(int version, SourceSpan span) {
            this.version = version;
            this.span = Objects.requireNonNull(span, "span");
        }

        /// Returns the selected BSS format version.
        ///
        /// @return the BSS version written by this string store's serializer
        private int version() {
            return version;
        }

        /// Selects the property-name snapshot visible to one declaration value.
        ///
        /// @param properties the immutable lower-case property-name snapshot
        private void useLookupProperties(@Unmodifiable Set<String> properties) {
            lookupProperties = Objects.requireNonNull(properties, "properties");
        }

        /// Returns whether an identifier names an already registered property.
        ///
        /// @param key the unquoted identifier considered as a property lookup
        /// @return whether the current declaration can look up the identifier
        private boolean isRegisteredLookupKey(String key) {
            return lookupProperties.contains(key.toLowerCase(Locale.ROOT));
        }

        /// Normalizes a lookup key when JavaFX has already seen its property.
        ///
        /// Forward and unresolved lookup identifiers retain their original
        /// spelling, matching OpenJFX's source-order parser state.
        ///
        /// @param key the unquoted identifier used as a property lookup
        /// @return the lower-case registered property name, or the original key
        private String normalizeLookupKey(String key) {
            return isRegisteredLookupKey(key)
                    ? key.toLowerCase(Locale.ROOT)
                    : key;
        }

        /// Returns the BSS index for one string, interning it when necessary.
        ///
        /// @param value the string to intern, or {@code null}
        /// @return the stable short-backed BSS index
        private int add(@Nullable String value) {
            if (value == null) {
                if (nullIndex != null) {
                    return nullIndex;
                }
                nullIndex = addNew(null);
                return nullIndex;
            }
            value = converterClassName(value);
            @Nullable Integer existing = indices.get(value);
            if (existing != null) {
                return existing;
            }
            var index = addNew(value);
            indices.put(value, index);
            return index;
        }

        /// Returns the converter class name used by the selected BSS version.
        ///
        /// JavaFX 8 used the internal plural `converters` package. JavaFX 9
        /// made these converters public in the singular `converter` package.
        ///
        /// @param value the candidate string-table value
        /// @return the value adjusted for BSS v5 when it names a converter
        private String converterClassName(String value) {
            if (version == VERSION_5) {
                if (value.equals("javafx.css.converter.StopConverter")
                        || value.equals(
                                "javafx.css.converter.LadderConverter"
                        )
                        || value.equals(
                                "javafx.css.converter.DeriveColorConverter"
                        )) {
                    return "com.sun.javafx.css.parser."
                            + value.substring(
                                    "javafx.css.converter.".length()
                            );
                }
                if (value.startsWith("javafx.css.converter.")) {
                    return "com.sun.javafx.css.converters."
                            + value.substring("javafx.css.converter.".length());
                }
            }
            return value;
        }

        /// Adds a new table entry after enforcing BSS's signed-short limit.
        ///
        /// @param value the new string, or {@code null}
        /// @return the new entry index
        private int addNew(@Nullable String value) {
            if (values.size() >= MAXIMUM_SHORT_VALUE) {
                throw new BssSerializeException(
                        "BSS output supports at most " + MAXIMUM_SHORT_VALUE + " strings.",
                        span,
                        null
                );
            }
            var index = values.size();
            values.add(value);
            return index;
        }

        /// Writes this table using Java's modified UTF-8 BSS encoding.
        ///
        /// @param output the document output stream
        /// @throws IOException if an in-memory output stream rejects a write
        private void writeBinary(DataOutputStream output) throws IOException {
            output.writeShort(values.size());
            output.writeShort(nullIndex == null ? -1 : nullIndex);
            for (@Nullable String value : values) {
                if (value != null) {
                    output.writeUTF(value);
                }
            }
        }
    }
}
