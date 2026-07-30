// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.callable;

import org.glavo.sassfx.Syntax;
import org.glavo.sassfx.internal.ast.FunctionRule;
import org.glavo.sassfx.internal.ast.ParameterList;
import org.glavo.sassfx.internal.parse.ParseException;
import org.glavo.sassfx.internal.parse.StylesheetParser;
import org.glavo.sassfx.internal.source.SourceFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// Adapts one public Java custom function to evaluator callable semantics.
@ApiStatus.Internal
@NotNullByDefault
public final class CustomFunctionCallable implements Callable {
    /// Contains the active evaluator identity while a Java callback runs.
    private static final ThreadLocal<@Nullable Object>
            CALLBACK_COMPILATION_CONTEXT = new ThreadLocal<>();

    /// Contains the normalized callable name.
    private final String name;

    /// Contains the parsed Sass parameter declaration.
    private final ParameterList parameters;

    /// Contains the public-value conversion and callback bridge.
    private final CustomFunctionBridge bridge;

    /// Creates a parsed custom function.
    private CustomFunctionCallable(
            String name,
            ParameterList parameters,
            CustomFunctionBridge bridge
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.bridge = Objects.requireNonNull(bridge, "bridge");
    }

    /// Parses and validates one bridged public function signature.
    ///
    /// @param bridge the public callback bridge
    /// @return the evaluator callable
    /// @throws IllegalArgumentException if the signature is not one complete
    /// function signature
    public static CustomFunctionCallable parse(CustomFunctionBridge bridge) {
        Objects.requireNonNull(bridge, "bridge");
        var signature = bridge.signature();
        var source = new SourceFile(
                "@function " + signature + " { @return null; }",
                null
        );
        try {
            var stylesheet = StylesheetParser.parse(source, Syntax.SCSS);
            if (stylesheet.children().size() != 1
                    || !(stylesheet.children().get(0) instanceof FunctionRule rule)) {
                throw new IllegalArgumentException(
                        "Invalid custom function signature: " + signature
                );
            }
            return new CustomFunctionCallable(
                    rule.name(),
                    rule.parameters(),
                    bridge
            );
        } catch (ParseException failure) {
            throw new IllegalArgumentException(
                    "Invalid custom function signature \""
                            + signature + "\": " + failure.getMessage(),
                    failure
            );
        }
    }

    /// Returns the normalized function name.
    ///
    /// @return the hyphenated name
    @Override
    public String name() {
        return name;
    }

    /// Returns the parsed parameters.
    ///
    /// @return the parameter declaration
    public ParameterList parameters() {
        return parameters;
    }

    /// Invokes the bridged public callback.
    ///
    /// @param arguments already-bound evaluator values
    /// @param compilationContext the active evaluator identity
    /// @return the non-{@code null} evaluator result
    /// @throws Exception if the callback fails
    /// @throws IllegalStateException if the callback returns {@code null}
    public org.glavo.sassfx.internal.value.SassValue invoke(
            @Unmodifiable List<org.glavo.sassfx.internal.value.SassValue> arguments,
            Object compilationContext
    ) throws Exception {
        Objects.requireNonNull(compilationContext, "compilationContext");
        @Nullable var previous = CALLBACK_COMPILATION_CONTEXT.get();
        CALLBACK_COMPILATION_CONTEXT.set(compilationContext);
        try {
            @Nullable var result = bridge.invoke(arguments);
            if (result == null) {
                throw new IllegalStateException(
                        "Invalid return value for custom function \""
                                + name + "\": null is not a SassValue."
                );
            }
            return result;
        } finally {
            if (previous == null) {
                CALLBACK_COMPILATION_CONTEXT.remove();
            } else {
                CALLBACK_COMPILATION_CONTEXT.set(previous);
            }
        }
    }

    /// Returns the evaluator identity for the currently executing callback.
    ///
    /// @return the active compilation identity
    /// @throws IllegalStateException if called outside a custom callback
    public static Object callbackCompilationContext() {
        @Nullable var context = CALLBACK_COMPILATION_CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException(
                    "No Sass custom function callback is active."
            );
        }
        return context;
    }
}
