// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Tracks configuration values that may be consumed by module variables.
///
/// A configuration is mutable so a single explicit configuration can pass
/// through a chain of plain {@code @forward} rules. Its opaque identity remains
/// stable after values are consumed and distinguishes that chain from a later,
/// independent {@code @use ... with} clause.
@ApiStatus.Internal
@NotNullByDefault
public final class ModuleConfiguration {
    /// Contains the shared empty, implicit configuration.
    private static final ModuleConfiguration EMPTY =
            new ModuleConfiguration(new Object(), false, Map.of());

    /// Identifies the clause that originally created this configuration.
    private final Object originalIdentity;

    /// Records whether unused values must be diagnosed by the creating use rule.
    private final boolean explicit;

    /// Contains values that have not yet been consumed, in source order.
    private final LinkedHashMap<String, ConfiguredValue> values;

    /// Creates a configuration snapshot.
    private ModuleConfiguration(
            Object originalIdentity,
            boolean explicit,
            Map<String, ConfiguredValue> values
    ) {
        this.originalIdentity = Objects.requireNonNull(
                originalIdentity,
                "originalIdentity"
        );
        this.explicit = explicit;
        Objects.requireNonNull(values, "values");
        this.values = new LinkedHashMap<>(values.size());
        for (var entry : values.entrySet()) {
            var name = Objects.requireNonNull(entry.getKey(), "configuration name");
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "configuration name must not be empty"
                );
            }
            this.values.put(
                    name,
                    Objects.requireNonNull(entry.getValue(), "configured value")
            );
        }
    }

    /// Returns the shared empty configuration.
    ///
    /// @return an implicit configuration with no values
    public static ModuleConfiguration empty() {
        return EMPTY;
    }

    /// Creates an explicit configuration for one {@code with} clause.
    ///
    /// @param values evaluated values in source order
    /// @return a configuration with a new opaque origin
    /// @throws IllegalArgumentException if {@code values} is empty
    public static ModuleConfiguration explicit(
            Map<String, ConfiguredValue> values
    ) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "explicit configuration must not be empty"
            );
        }
        return new ModuleConfiguration(new Object(), true, values);
    }

    /// Returns whether this configuration came from an explicit use clause.
    ///
    /// @return {@code true} when unused values must be diagnosed
    public boolean isExplicit() {
        return explicit;
    }

    /// Returns whether no unconsumed values remain.
    ///
    /// @return {@code true} when every value has been consumed
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /// Removes and returns one configured value.
    ///
    /// @param name the normalized variable name
    /// @return the configured value, or {@code null} when absent
    public @Nullable ConfiguredValue consume(String name) {
        Objects.requireNonNull(name, "name");
        return values.remove(name);
    }

    /// Returns the first unconsumed value without modifying this configuration.
    ///
    /// @return the first value in source order, or {@code null} when empty
    public @Nullable ConfiguredValue firstUnused() {
        var iterator = values.values().iterator();
        return iterator.hasNext() ? iterator.next() : null;
    }

    /// Returns the currently unconsumed variable names.
    ///
    /// @return an immutable source-order snapshot
    public @Unmodifiable Set<String> names() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values.keySet()));
    }

    /// Returns whether another configuration originated from the same clause.
    ///
    /// @param other the configuration to compare
    /// @return {@code true} only for the same opaque origin
    public boolean sameOriginal(ModuleConfiguration other) {
        Objects.requireNonNull(other, "other");
        return originalIdentity == other.originalIdentity;
    }
}
