// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.css;

import org.glavo.sassfx.SourceSpan;
import org.glavo.sassfx.internal.value.SassValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A plain-CSS `name: value` declaration.
@ApiStatus.Internal
@NotNullByDefault
public final class CssDeclaration extends AbstractCssNode implements CssNode {
    /// Contains the resolved property name.
    private final CssValue<String> name;

    /// Contains the evaluated property value.
    private final CssValue<SassValue> value;

    /// Records whether the value was originally parsed as SassScript.
    private final boolean parsedAsSassScript;

    /// Creates a CSS declaration.
    ///
    /// @param name               the resolved property name
    /// @param value              the evaluated property value
    /// @param span               the source range of the originating declaration
    /// @param parsedAsSassScript whether the value used SassScript grammar
    public CssDeclaration(
            CssValue<String> name,
            CssValue<SassValue> value,
            SourceSpan span,
            boolean parsedAsSassScript
    ) {
        super(span);
        this.name = Objects.requireNonNull(name, "name");
        this.value = Objects.requireNonNull(value, "value");
        this.parsedAsSassScript = parsedAsSassScript;
        if (!parsedAsSassScript
                && !(value.value() instanceof org.glavo.sassfx.internal.value.SassString)) {
            throw new IllegalArgumentException(
                    "a non-SassScript declaration value must be a SassString"
            );
        }
    }

    /// Returns the resolved property name.
    ///
    /// @return the name value
    public CssValue<String> name() {
        return name;
    }

    /// Returns the evaluated property value.
    ///
    /// @return the value
    public CssValue<SassValue> value() {
        return value;
    }

    /// Returns whether the value was originally parsed as SassScript.
    ///
    /// @return whether SassScript grammar produced the value
    public boolean parsedAsSassScript() {
        return parsedAsSassScript;
    }

    /// Returns whether this declaration names a CSS custom property.
    ///
    /// @return whether the name begins with `--`
    public boolean isCustomProperty() {
        return name.value().startsWith("--");
    }

    /// Returns false because declarations are always emitted when present.
    ///
    /// @return {@code false}
    @Override
    public boolean isInvisible() {
        return false;
    }
}
