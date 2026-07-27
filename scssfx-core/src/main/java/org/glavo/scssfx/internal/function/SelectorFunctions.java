// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.function;

import org.glavo.scssfx.SourceLocation;
import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.ast.selector.ComplexSelector;
import org.glavo.scssfx.internal.ast.selector.ComplexSelectorComponent;
import org.glavo.scssfx.internal.ast.selector.CompoundSelector;
import org.glavo.scssfx.internal.ast.selector.ParentSelector;
import org.glavo.scssfx.internal.ast.selector.SelectorAlgebra;
import org.glavo.scssfx.internal.ast.selector.SelectorList;
import org.glavo.scssfx.internal.ast.selector.SimpleSelector;
import org.glavo.scssfx.internal.ast.selector.TypeSelector;
import org.glavo.scssfx.internal.ast.selector.UniversalSelector;
import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.callable.BuiltInCallable.Param;
import org.glavo.scssfx.internal.value.ListSeparator;
import org.glavo.scssfx.internal.value.SassArgumentList;
import org.glavo.scssfx.internal.value.SassBoolean;
import org.glavo.scssfx.internal.value.SassList;
import org.glavo.scssfx.internal.value.SassNull;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.glavo.scssfx.internal.value.SassValueException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Implements the pure selector functions exported by {@code sass:selector}.
///
/// The functions convert Sass's nested selector-list value representation to
/// the compiler's selector AST, perform operations using that AST, and convert
/// the result back to ordinary Sass strings and lists.
@ApiStatus.Internal
@NotNullByDefault
public final class SelectorFunctions {
    /// Identifies the synthetic source location used for function-created selectors.
    private static final SourceLocation ORIGIN = new SourceLocation(0, 0, 0);

    /// Contains the stable deprecation identifier for invalid combinators.
    private static final String BOGUS_COMBINATORS_CODE = "bogus-combinators";

    /// Prevents instantiation.
    private SelectorFunctions() {
    }

    /// Returns the selector functions supported by the current selector AST.
    ///
    /// @return an immutable selector function table
    public static @Unmodifiable Map<String, BuiltInCallable> module() {
        var functions = new LinkedHashMap<String, BuiltInCallable>();
        register(functions, BuiltInCallable.contextual(
                "parse",
                List.of(Param.required("selector")),
                1,
                SelectorFunctions::parse
        ));
        register(functions, BuiltInCallable.contextual(
                "simple-selectors",
                List.of(Param.required("selector")),
                1,
                SelectorFunctions::simpleSelectors
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "nest",
                List.of(),
                "selectors",
                0,
                SelectorFunctions::nest
        ));
        register(functions, BuiltInCallable.contextualWithRest(
                "append",
                List.of(),
                "selectors",
                0,
                SelectorFunctions::append
        ));
        register(functions, BuiltInCallable.contextual(
                "unify",
                List.of(
                        Param.required("selector1"),
                        Param.required("selector2")
                ),
                2,
                SelectorFunctions::unify
        ));
        register(functions, BuiltInCallable.contextual(
                "is-superselector",
                List.of(
                        Param.required("super"),
                        Param.required("sub")
                ),
                2,
                SelectorFunctions::isSuperselector
        ));
        register(functions, BuiltInCallable.contextual(
                "extend",
                List.of(
                        Param.required("selector"),
                        Param.required("extendee"),
                        Param.required("extender")
                ),
                3,
                SelectorFunctions::extend
        ));
        register(functions, BuiltInCallable.contextual(
                "replace",
                List.of(
                        Param.required("selector"),
                        Param.required("original"),
                        Param.required("replacement")
                ),
                3,
                SelectorFunctions::replace
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(functions));
    }

    /// Registers one callable under its normalized name.
    ///
    /// @param functions the mutable function table
    /// @param callable the callable to register
    private static void register(
            LinkedHashMap<String, BuiltInCallable> functions,
            BuiltInCallable callable
    ) {
        functions.put(callable.name(), callable);
    }

    /// Parses one selector value and returns its structural Sass list form.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the one selector argument
    /// @return the parsed selector list represented as Sass lists
    private static SassValue parse(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        return asSassList(parseSelector(
                context,
                args.get(0),
                "selector",
                false
        ));
    }

    /// Returns the simple selectors in one compound selector.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the one compound-selector argument
    /// @return a comma-separated list of unquoted simple selector strings
    private static SassValue simpleSelectors(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var value = args.get(0);
        var selector = parseSelector(context, value, "selector", false);
        if (selector.components().size() != 1) {
            throw notCompoundSelector(value);
        }
        var complex = selector.components().get(0);
        if (!complex.leadingCombinators().isEmpty() || complex.components().size() != 1) {
            throw notCompoundSelector(value);
        }
        var component = complex.components().get(0);
        if (!component.combinators().isEmpty()) {
            throw notCompoundSelector(value);
        }

        var result = new ArrayList<SassValue>();
        for (var simple : component.selector().components()) {
            result.add(new SassString(simple.toCssString(), false));
        }
        return new SassList(result, ListSeparator.COMMA, false);
    }

    /// Nests every selector argument within the preceding argument.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the rest argument list containing one or more selectors
    /// @return the resulting selector list represented as Sass lists
    private static SassValue nest(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var values = restValues(args);
        if (values.isEmpty()) {
            throw new SassValueException("$selectors: At least one selector must be passed.");
        }
        var result = parseSelector(context, values.get(0), null, true);
        rejectTopLevelParentSuffix(result);
        for (var index = 1; index < values.size(); index++) {
            result = parseSelector(
                    context,
                    values.get(index),
                    null,
                    true
            ).nestWithin(result);
        }
        return asSassList(result);
    }

    /// Appends every selector argument to the preceding argument's last compound.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the rest argument list containing one or more selectors
    /// @return the resulting selector list represented as Sass lists
    private static SassValue append(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var values = restValues(args);
        if (values.isEmpty()) {
            throw new SassValueException("$selectors: At least one selector must be passed.");
        }
        var result = parseSelector(context, values.get(0), null, false);
        for (var index = 1; index < values.size(); index++) {
            result = appendSelector(
                    result,
                    parseSelector(context, values.get(index), null, false)
            );
        }
        return asSassList(result);
    }

    /// Returns the selector intersection of two selector-list values.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the two selector arguments
    /// @return the unified selector list, or Sass {@code null}
    private static SassValue unify(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var first = parseSelector(context, args.get(0), "selector1", false);
        var second = parseSelector(context, args.get(1), "selector2", false);
        warnIfBogus(context, first, "selector1");
        warnIfBogus(context, second, "selector2");
        @Nullable SelectorList unified = SelectorAlgebra.unify(
                first,
                second
        );
        return unified == null ? SassNull.NULL : asSassList(unified);
    }

    /// Returns whether one selector-list value is a superselector of another.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the superselector and subselector arguments
    /// @return the Sass boolean result
    private static SassValue isSuperselector(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var first = parseSelector(context, args.get(0), "super", false);
        var second = parseSelector(context, args.get(1), "sub", false);
        warnIfBogus(context, first, "super");
        warnIfBogus(context, second, "sub");
        return SassBoolean.of(SelectorAlgebra.isSuperselector(
                first,
                second
        ));
    }

    /// Extends selector-list values while retaining the original alternatives.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the selector, extendee, and extender arguments
    /// @return the extended selector list
    private static SassValue extend(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var selector = parseSelector(context, args.get(0), "selector", false);
        var extendee = parseSelector(context, args.get(1), "extendee", false);
        var extender = parseSelector(context, args.get(2), "extender", false);
        warnIfBogus(context, selector, "selector");
        warnIfBogus(context, extendee, "extendee");
        warnIfBogus(context, extender, "extender");
        return asSassList(SelectorAlgebra.extend(
                selector,
                extendee,
                extender
        ));
    }

    /// Replaces selector-list values without retaining matched originals.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param args the selector, original, and replacement arguments
    /// @return the transformed selector list
    private static SassValue replace(
            BuiltInCallable.Context context,
            List<SassValue> args
    ) {
        var selector = parseSelector(context, args.get(0), "selector", false);
        var original = parseSelector(context, args.get(1), "original", false);
        var replacement = parseSelector(
                context,
                args.get(2),
                "replacement",
                false
        );
        warnIfBogus(context, selector, "selector");
        warnIfBogus(context, original, "original");
        warnIfBogus(context, replacement, "replacement");
        return asSassList(SelectorAlgebra.replace(
                selector,
                original,
                replacement
        ));
    }

    /// Returns the positional selector arguments bound to a rest parameter.
    ///
    /// @param args the bound built-in arguments
    /// @return the immutable positional selector values
    private static @Unmodifiable List<SassValue> restValues(List<SassValue> args) {
        if (args.size() == 1 && args.get(0) instanceof SassArgumentList argumentList) {
            return argumentList.asList();
        }
        throw new AssertionError("selector rest arguments were not bound as an argument list");
    }

    /// Parses a Sass value as a selector list.
    ///
    /// @param context the invocation receiving selector deprecations
    /// @param value the Sass selector value
    /// @param name the argument name for diagnostics, or {@code null} to omit
    ///             {@code $name:} prefixes (as {@code selector.append}/{@code nest} do)
    /// @param allowParent whether parent selectors are permitted
    /// @return the parsed selector list
    private static SelectorList parseSelector(
            BuiltInCallable.Context context,
            SassValue value,
            @Nullable String name,
            boolean allowParent
    ) {
        var text = selectorText(value, name);

        SelectorList selector;
        try {
            selector = SelectorList.parse(
                    text,
                    syntheticSpan(text),
                    false,
                    false,
                    diagnostic -> context.deprecate(
                            diagnostic.message(),
                            Objects.requireNonNull(
                                    diagnostic.code(),
                                    "selector deprecation code"
                            )
                    )
            );
        } catch (SassValueException exception) {
            throw prefixSelectorError(name, exception);
        }
        // Opaque raw pseudo arguments may contain a literal {@code &} character
        // that is not a parent-selector AST node (e.g. {@code :c(*&^)}). Reject
        // only structural parent selectors ({@code &} compounds). Nesting still
        // fails later via {@link SelectorList#replaceParentSelectors} when an
        // opaque argument truly holds an unresolved parent marker.
        if (!allowParent && selector.parentSelectorCount() != 0) {
            throw prefixSelectorMessage(name, "Parent selectors aren't allowed here.");
        }
        return selector;
    }

    /// Reports one selector value that contains bogus combinators.
    ///
    /// @param context the invocation receiving the diagnostic
    /// @param selector the parsed selector value
    /// @param name the argument name without a dollar sign
    private static void warnIfBogus(
            BuiltInCallable.Context context,
            SelectorList selector,
            String name
    ) {
        if (selector.components().stream().noneMatch(
                ComplexSelector::isBogusIncludingLeading
        )) {
            return;
        }
        context.deprecate(
                "$" + name + ": " + selector.toCssString()
                        + " is not valid CSS.\n"
                        + "This will be an error in Dart Sass 2.0.0.\n\n"
                        + "More info: https://sass-lang.com/d/bogus-combinators",
                BOGUS_COMBINATORS_CODE
        );
    }

    /// Prefixes a selector-function diagnostic when a parameter name is present.
    ///
    /// @param name      the parameter name without a dollar sign, or {@code null}
    /// @param exception the underlying parse failure
    /// @return a possibly parameter-scoped exception
    private static SassValueException prefixSelectorError(
            @Nullable String name,
            SassValueException exception
    ) {
        var message = Objects.requireNonNull(exception.getMessage(), "selector failure");
        if (name == null || message.startsWith("$")) {
            return exception;
        }
        return new SassValueException("$" + name + ": " + message);
    }

    /// Creates a selector-function diagnostic with optional parameter scoping.
    ///
    /// @param name    the parameter name without a dollar sign, or {@code null}
    /// @param message the diagnostic body
    /// @return the value exception to throw
    private static SassValueException prefixSelectorMessage(
            @Nullable String name,
            String message
    ) {
        return name == null
                ? new SassValueException(message)
                : new SassValueException("$" + name + ": " + message);
    }

    /// Rejects top-level parent selectors that carry an unresolved suffix.
    ///
    /// @param selector the first selector passed to {@code selector.nest()}
    private static void rejectTopLevelParentSuffix(SelectorList selector) {
        if (selector.hasParentSelectorSuffix()) {
                        throw new SassValueException(
                                "A top-level selector may not contain a parent selector with a suffix."
                        );
                    }
                }

    /// Converts a selector value to source text accepted by the selector parser.
    ///
    /// @param value the Sass selector value
    /// @param name the argument name for diagnostics
    /// @return the selector source text
    /// @throws SassValueException if the value is not a selector string or list structure
    private static String selectorText(SassValue value, @Nullable String name) {
        if (value instanceof SassString string) {
            return string.text();
        }
        if (value instanceof SassList list) {
            // Diagnostics always cite the original top-level value so nested
            // failures report the caller-visible inspect form (e.g. {@code (c,)}).
            return selectorText(list, name, value);
        }
        throw invalidSelector(value, name);
    }

    /// Converts a selector-list Sass list to source text.
    ///
    /// @param list the Sass list representation
    /// @param name the argument name for diagnostics, or {@code null}
    /// @param root the original top-level selector value for error messages
    /// @return the selector source text
    private static String selectorText(
            SassList list,
            @Nullable String name,
            SassValue root
    ) {
        if (list.contents().isEmpty() || list.separator() == ListSeparator.SLASH) {
            throw invalidSelector(root, name);
        }
        if (list.separator() == ListSeparator.COMMA) {
            var complexes = new ArrayList<String>();
            for (var complex : list.contents()) {
                if (complex instanceof SassString string) {
                    complexes.add(string.text());
                } else if (complex instanceof SassList nested
                        && nested.separator() == ListSeparator.SPACE) {
                    complexes.add(selectorText(nested, name, root));
                } else {
                    throw invalidSelector(root, name);
                }
            }
            return String.join(", ", complexes);
        }

        var compounds = new ArrayList<String>();
        for (var compound : list.contents()) {
            if (!(compound instanceof SassString string)) {
                throw invalidSelector(root, name);
            }
            compounds.add(string.text());
        }
        return String.join(" ", compounds);
    }

    /// Creates an invalid-selector diagnostic.
    ///
    /// @param value the rejected Sass value
    /// @param name  the argument name for diagnostics, or {@code null}
    /// @return the value exception to throw
    private static SassValueException invalidSelector(SassValue value, @Nullable String name) {
        // Match dart-sass inspect: singleton comma/slash lists already include
        // parentheses, while other unbracketed lists need an outer pair.
        var rendered = value.toString();
        if (value instanceof SassList list
                && !list.hasBrackets()
                && !(rendered.startsWith("(") && rendered.endsWith(")"))) {
            rendered = "(" + rendered + ")";
        }
        var body = rendered + " is not a valid selector: it must be a string,\n"
                + "a list of strings, or a list of lists of strings.";
        return name == null
                ? new SassValueException(body)
                : new SassValueException("$" + name + ": " + body);
    }

    /// Creates a non-compound-selector diagnostic.
    ///
    /// @param value the rejected selector value
    /// @return the value exception to throw
    private static SassValueException notCompoundSelector(SassValue value) {
        return new SassValueException(
                "$selector: " + value + " is not a compound selector."
        );
    }

    /// Returns a selector list that appends {@code child} to every {@code parent} selector.
    ///
    /// @param parent the already accumulated selector list
    /// @param child the selector list to append
    /// @return the appended selector list
    private static SelectorList appendSelector(SelectorList parent, SelectorList child) {
        var transformed = new ArrayList<ComplexSelector>();
        for (var complex : child.components()) {
            transformed.add(prependParent(complex, parent));
        }
        return new SelectorList(transformed, child.span()).nestWithin(parent);
    }

    /// Makes one child complex selector explicit about the parent it will append to.
    ///
    /// @param child the parsed child complex selector
    /// @param parent the parent list used only for diagnostics
    /// @return a complex selector beginning with a parent selector
    /// @throws SassValueException if the child cannot be appended
    private static ComplexSelector prependParent(ComplexSelector child, SelectorList parent) {
        if (!child.leadingCombinators().isEmpty() || child.components().isEmpty()) {
            throw cannotAppend(child, parent);
        }
        var firstComponent = child.components().get(0);
        var compound = firstComponent.selector();
        var simples = compound.components();
        var first = simples.get(0);
        if (first instanceof UniversalSelector
                || first instanceof TypeSelector type && !type.name().isUnqualified()) {
            throw cannotAppend(child, parent);
        }

        var transformedSimples = new ArrayList<SimpleSelector>();
        if (first instanceof TypeSelector type) {
            transformedSimples.add(new ParentSelector(type.name().name(), type.span()));
            transformedSimples.addAll(simples.subList(1, simples.size()));
        } else {
            transformedSimples.add(new ParentSelector(null, first.span()));
            transformedSimples.addAll(simples);
        }

        var transformedComponents = new ArrayList<ComplexSelectorComponent>();
        transformedComponents.add(new ComplexSelectorComponent(
                new CompoundSelector(transformedSimples, compound.span()),
                firstComponent.combinators(),
                firstComponent.span()
        ));
        transformedComponents.addAll(child.components().subList(1, child.components().size()));
        return new ComplexSelector(List.of(), transformedComponents, child.span());
    }


    /// Creates an append-operation diagnostic.
    ///
    /// @param child the selector that cannot be appended
    /// @param parent the selector list being appended to
    /// @return the value exception to throw
    private static SassValueException cannotAppend(ComplexSelector child, SelectorList parent) {
        return new SassValueException(
                "Can't append " + child.toCssString() + " to " + parent.toCssString() + "."
        );
    }

    /// Converts a selector AST to Sass's nested selector-list value representation.
    ///
    /// @param selector the selector AST to convert
    /// @return a comma-separated list of space-separated complex-selector lists
    private static SassList asSassList(SelectorList selector) {
        var complexes = new ArrayList<SassValue>();
        for (var complex : selector.components()) {
            var parts = new ArrayList<SassValue>();
            for (var combinator : complex.leadingCombinators()) {
                parts.add(new SassString(combinator.css(), false));
            }
            for (var component : complex.components()) {
                parts.add(new SassString(component.selector().toCssString(), false));
                for (var combinator : component.combinators()) {
                    parts.add(new SassString(combinator.css(), false));
                }
            }
            complexes.add(new SassList(parts, ListSeparator.SPACE, false));
        }
        return new SassList(complexes, ListSeparator.COMMA, false);
    }

    /// Creates a zero-length-base synthetic span for one selector source string.
    ///
    /// @param text the exact selector text
    /// @return a span covering that text
    private static SourceSpan syntheticSpan(String text) {
        return new SourceSpan(
                URI.create("sass:selector"),
                ORIGIN,
                new SourceLocation(0, text.length(), text.length()),
                text
        );
    }
}
