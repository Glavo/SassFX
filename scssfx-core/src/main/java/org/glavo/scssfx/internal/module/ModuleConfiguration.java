// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.module;

import org.glavo.scssfx.internal.ast.ForwardRule;
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

/// Tracks configuration values consumed through module-forward projections.
///
/// Projected configurations map names at a module boundary to a shared backing
/// map. Consumption therefore propagates to the originating outer configuration
/// while its opaque identity remains stable.
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

    /// Contains unconsumed values under the root configuration names.
    private final LinkedHashMap<String, ConfiguredValue> backingValues;

    /// Maps names at this module boundary to names in the backing map.
    private final LinkedHashMap<String, String> projectedNames;

    /// Creates a root configuration whose visible and backing names are identical.
    ///
    /// @param originalIdentity the opaque origin identity
    /// @param explicit         whether unused values require a diagnostic
    /// @param values           values in source order
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
        this.backingValues = new LinkedHashMap<>(values.size());
        this.projectedNames = new LinkedHashMap<>(values.size());
        for (var entry : values.entrySet()) {
            var name = validateName(entry.getKey());
            this.backingValues.put(
                    name,
                    Objects.requireNonNull(entry.getValue(), "configured value")
            );
            this.projectedNames.put(name, name);
        }
    }

    /// Creates a projected view over an existing backing map.
    ///
    /// @param originalIdentity the opaque origin identity
    /// @param explicit         whether unused values require a diagnostic
    /// @param backingValues    the shared backing values
    /// @param projectedNames   boundary names mapped to backing names
    private ModuleConfiguration(
            Object originalIdentity,
            boolean explicit,
            LinkedHashMap<String, ConfiguredValue> backingValues,
            Map<String, String> projectedNames
    ) {
        this.originalIdentity = Objects.requireNonNull(
                originalIdentity,
                "originalIdentity"
        );
        this.explicit = explicit;
        this.backingValues = Objects.requireNonNull(
                backingValues,
                "backingValues"
        );
        Objects.requireNonNull(projectedNames, "projectedNames");
        this.projectedNames = new LinkedHashMap<>(projectedNames.size());
        for (var entry : projectedNames.entrySet()) {
            this.projectedNames.put(
                    validateName(entry.getKey()),
                    validateName(entry.getValue())
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

    /// Creates an implicit configuration from visible environment variables.
    ///
    /// Used when an {@code @import}-ed stylesheet contains {@code @forward}
    /// rules so importer variables configure downstream {@code !default}
    /// declarations, matching dart-sass {@code toImplicitConfiguration}.
    ///
    /// @param values values in source order; may be empty
    /// @return an implicit configuration, or the shared empty instance
    public static ModuleConfiguration implicit(
            Map<String, ConfiguredValue> values
    ) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return EMPTY;
        }
        return new ModuleConfiguration(new Object(), false, values);
    }

    /// Copies visible values for a forward rule that owns configuration.
    ///
    /// The returned configuration has an independent backing map and a fresh
    /// identity. It is explicit when the adjusted outer configuration is
    /// explicit or empty.
    ///
    /// @param adjusted the outer configuration after forward projection
    /// @return a writable configuration for loading the forwarded module
    public static ModuleConfiguration forForward(
            ModuleConfiguration adjusted
    ) {
        Objects.requireNonNull(adjusted, "adjusted");
        var values = new LinkedHashMap<String, ConfiguredValue>();
        for (var entry : adjusted.projectedNames.entrySet()) {
            @Nullable ConfiguredValue value = adjusted.backingValues.get(
                    entry.getValue()
            );
            if (value != null) {
                values.put(entry.getKey(), value);
            }
        }
        return new ModuleConfiguration(
                new Object(),
                adjusted.explicit || adjusted.isEmpty(),
                values
        );
    }

    /// Returns a configuration projected through one forward rule.
    ///
    /// A prefix selects outer names beginning with that prefix and removes it
    /// before the target module sees the name. Variable filters are then
    /// applied to the unprefixed names.
    ///
    /// @param rule the forwarding rule
    /// @return a view sharing this configuration's backing values and identity
    public ModuleConfiguration throughForward(ForwardRule rule) {
        Objects.requireNonNull(rule, "rule");
        if (isEmpty()) {
            return this;
        }
        if (rule.prefix() == null
                && rule.shownVariables() == null
                && rule.hiddenVariables() == null) {
            return this;
        }

        var names = new LinkedHashMap<String, String>();
        for (var entry : projectedNames.entrySet()) {
            if (!backingValues.containsKey(entry.getValue())) {
                continue;
            }
            var name = entry.getKey();
            if (rule.prefix() != null) {
                if (!name.startsWith(rule.prefix())) {
                    continue;
                }
                name = name.substring(rule.prefix().length());
            }
            if (isVisible(
                    name,
                    rule.shownVariables(),
                    rule.hiddenVariables()
            )) {
                names.put(name, entry.getValue());
            }
        }
        return new ModuleConfiguration(
                originalIdentity,
                explicit,
                backingValues,
                names
        );
    }

    /// Returns whether this configuration came from an explicit use clause.
    ///
    /// @return {@code true} when unused values must be diagnosed
    public boolean isExplicit() {
        return explicit;
    }

    /// Returns whether no visible unconsumed values remain.
    ///
    /// @return {@code true} when every visible value has been consumed
    public boolean isEmpty() {
        return firstUnused() == null;
    }

    /// Returns whether one visible unconsumed value exists.
    ///
    /// @param name the normalized boundary name
    /// @return whether the name currently maps to a backing value
    public boolean contains(String name) {
        Objects.requireNonNull(name, "name");
        @Nullable String backingName = projectedNames.get(name);
        return backingName != null && backingValues.containsKey(backingName);
    }

    /// Removes and returns one configured value.
    ///
    /// Removing a projected name also removes its shared backing value.
    ///
    /// @param name the normalized variable name at this module boundary
    /// @return the configured value, or {@code null} when absent
    public @Nullable ConfiguredValue consume(String name) {
        Objects.requireNonNull(name, "name");
        @Nullable String backingName = projectedNames.remove(name);
        return backingName == null ? null : backingValues.remove(backingName);
    }

    /// Adds or replaces a value owned by this configuration root.
    ///
    /// @param name  the normalized variable name
    /// @param value the configured value
    public void put(String name, ConfiguredValue value) {
        name = validateName(name);
        backingValues.put(name, Objects.requireNonNull(value, "value"));
        projectedNames.put(name, name);
    }

    /// Discards values whose visible names are not retained.
    ///
    /// This is used on a fresh forward-owned configuration after inherited
    /// consumption has been propagated to the outer configuration.
    ///
    /// @param retainedNames names owned by the forward rule
    public void retainOnly(Set<String> retainedNames) {
        Objects.requireNonNull(retainedNames, "retainedNames");
        var iterator = projectedNames.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (retainedNames.contains(entry.getKey())) {
                continue;
            }
            backingValues.remove(entry.getValue());
            iterator.remove();
        }
    }

    /// Returns the first visible unconsumed value without modifying this configuration.
    ///
    /// @return the first value in source order, or {@code null} when empty
    public @Nullable ConfiguredValue firstUnused() {
        @Nullable Map.Entry<String, ConfiguredValue> entry = firstUnusedEntry();
        return entry == null ? null : entry.getValue();
    }

    /// Returns the first visible unconsumed name and value without modifying this
    /// configuration.
    ///
    /// @return the first name/value pair in source order, or {@code null} when empty
    public @Nullable Map.Entry<String, ConfiguredValue> firstUnusedEntry() {
        for (var entry : projectedNames.entrySet()) {
            @Nullable ConfiguredValue value = backingValues.get(entry.getValue());
            if (value != null) {
                return Map.entry(entry.getKey(), value);
            }
        }
        return null;
    }

    /// Returns the currently visible unconsumed variable names.
    ///
    /// @return an immutable source-order snapshot
    public @Unmodifiable Set<String> names() {
        var names = new LinkedHashSet<String>();
        for (var entry : projectedNames.entrySet()) {
            if (backingValues.containsKey(entry.getValue())) {
                names.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(names);
    }

    /// Returns whether another configuration originated from the same clause.
    ///
    /// @param other the configuration to compare
    /// @return {@code true} only for the same opaque origin
    public boolean sameOriginal(ModuleConfiguration other) {
        Objects.requireNonNull(other, "other");
        return originalIdentity == other.originalIdentity;
    }

    /// Returns whether one unprefixed name passes a variable filter.
    ///
    /// @param name   the unprefixed configuration name
    /// @param shown  the allowlist, or {@code null}
    /// @param hidden the blocklist, or {@code null}
    /// @return whether the name remains visible
    private static boolean isVisible(
            String name,
            @Nullable Set<String> shown,
            @Nullable Set<String> hidden
    ) {
        return shown != null
                ? shown.contains(name)
                : hidden == null || !hidden.contains(name);
    }

    /// Validates and returns one normalized configuration name.
    ///
    /// @param name the prospective name
    /// @return the same name
    /// @throws IllegalArgumentException if the name is empty
    private static String validateName(String name) {
        Objects.requireNonNull(name, "configuration name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "configuration name must not be empty"
            );
        }
        return name;
    }
}
