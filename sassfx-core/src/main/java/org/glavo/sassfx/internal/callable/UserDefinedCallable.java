// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.callable;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.ast.ParameterList;
import org.glavo.sassfx.internal.ast.SassStatement;
import org.glavo.sassfx.internal.evaluate.Environment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/// A mixin, function, or content block captured with its definition environment.
@ApiStatus.Internal
@NotNullByDefault
public final class UserDefinedCallable implements Callable {
    /// Contains the normalized callable name.
    private final String name;

    /// Contains the declared parameters.
    private final ParameterList parameters;

    /// Contains the body statements.
    private final @Unmodifiable List<SassStatement> children;

    /// Contains the definition-time environment closure.
    private final Environment environment;

    /// Contains the declaration source span.
    private final SourceSpan span;

    /// Records whether a mixin body accepts content blocks.
    private final boolean acceptsContent;

    /// Records whether the definition originated in a dependency stylesheet.
    private final boolean inDependency;

    /// Creates a user-defined callable.
    ///
    /// @param name            the normalized name
    /// @param parameters      the declared parameters
    /// @param children        the body statements
    /// @param environment     the definition-time closure
    /// @param span            the declaration span
    /// @param acceptsContent  whether content blocks are accepted
    /// @param inDependency whether compiler warnings in the body are dependency warnings
    public UserDefinedCallable(
            String name,
            ParameterList parameters,
            List<SassStatement> children,
            Environment environment,
            SourceSpan span,
            boolean acceptsContent,
            boolean inDependency
    ) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        this.children = List.copyOf(children);
        this.environment = Objects.requireNonNull(environment, "environment");
        this.span = Objects.requireNonNull(span, "span");
        this.acceptsContent = acceptsContent;
        this.inDependency = inDependency;
    }

    /// Returns the normalized callable name.
    ///
    /// @return the hyphenated name
    @Override
    public String name() {
        return name;
    }

    /// Returns the declared parameters.
    ///
    /// @return the parameter list
    public ParameterList parameters() {
        return parameters;
    }

    /// Returns the body statements.
    ///
    /// @return the immutable children
    public @Unmodifiable List<SassStatement> children() {
        return children;
    }

    /// Returns the definition-time environment closure.
    ///
    /// @return the captured environment
    public Environment environment() {
        return environment;
    }

    /// Returns the declaration source span.
    ///
    /// @return the span
    public SourceSpan span() {
        return span;
    }

    /// Returns whether this mixin accepts a content block.
    ///
    /// @return whether content is accepted
    public boolean acceptsContent() {
        return acceptsContent;
    }

    /// Returns whether the definition originated in a dependency stylesheet.
    ///
    /// @return whether compiler warnings in the body are dependency warnings
    public boolean inDependency() {
        return inDependency;
    }
}
