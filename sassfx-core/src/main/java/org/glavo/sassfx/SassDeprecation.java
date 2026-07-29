// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Identifies a Dart Sass 1.102.0 deprecation category.
///
/// Each value exposes the stable kebab-case identifier accepted by Sass
/// command-line and embedded protocol options. Version metadata follows the
/// pinned Dart Sass deprecation registry.
@NotNullByDefault
public enum SassDeprecation {
    /// Passing a string directly to {@code meta.call()}.
    CALL_STRING("call-string", "0.0.0", null, false),

    /// Using {@code @elseif}.
    ELSEIF("elseif", "1.3.2", null, false),

    /// Using {@code @-moz-document}.
    MOZ_DOCUMENT("moz-document", "1.7.2", null, false),

    /// Returning relative canonical URLs from an importer.
    RELATIVE_CANONICAL("relative-canonical", "1.14.2", null, false),

    /// Declaring new variables with {@code !global}.
    NEW_GLOBAL("new-global", "1.17.2", null, false),

    /// Using color-module functions in place of plain CSS functions.
    COLOR_MODULE_COMPAT("color-module-compat", "1.23.0", null, false),

    /// Using slash division outside calculations.
    SLASH_DIV("slash-div", "1.33.0", null, false),

    /// Using leading, trailing, or repeated combinators.
    BOGUS_COMBINATORS("bogus-combinators", "1.54.0", null, false),

    /// Using ambiguous unary plus or minus syntax.
    STRICT_UNARY("strict-unary", "1.55.0", null, false),

    /// Passing invalid units to built-in functions.
    FUNCTION_UNITS("function-units", "1.56.0", null, false),

    /// Repeating {@code !default} or {@code !global} on one declaration.
    DUPLICATE_VAR_FLAGS("duplicate-var-flags", "1.62.0", null, false),

    /// Passing null alpha through a language API.
    NULL_ALPHA("null-alpha", "1.62.3", null, false),

    /// Passing percentages to the global {@code abs()} function.
    ABS_PERCENT("abs-percent", "1.65.0", null, false),

    /// Using the working directory as an implicit load path.
    FS_IMPORTER_CWD("fs-importer-cwd", "1.73.0", null, false),

    /// Declaring functions or mixins whose names begin with {@code --}.
    CSS_FUNCTION_MIXIN("css-function-mixin", "1.76.0", "1.94.0", false),

    /// Placing declarations after or between nested rules.
    MIXED_DECLS("mixed-decls", "1.77.7", "1.92.0", false),

    /// Calling {@code meta.feature-exists()}.
    FEATURE_EXISTS("feature-exists", "1.78.0", null, false),

    /// Using deprecated forms of {@code sass:color} functions.
    COLOR_4_API("color-4-api", "1.79.0", null, false),

    /// Calling legacy global color functions.
    COLOR_FUNCTIONS("color-functions", "1.79.0", null, false),

    /// Using the legacy JavaScript API.
    LEGACY_JS_API("legacy-js-api", "1.79.0", null, false),

    /// Using dynamic Sass {@code @import}.
    IMPORT("import", "1.80.0", null, false),

    /// Calling a global built-in available from a built-in module.
    GLOBAL_BUILTIN("global-builtin", "1.80.0", null, false),

    /// Declaring functions named {@code type}.
    TYPE_FUNCTION("type-function", "1.86.0", "1.92.0", false),

    /// Passing a relative URL to string compilation.
    COMPILE_STRING_RELATIVE_URL(
            "compile-string-relative-url",
            "1.88.0",
            null,
            false
    ),

    /// Declaring a rest parameter before a later argument.
    MISPLACED_REST("misplaced-rest", "1.91.0", null, false),

    /// Configuring private variables through module loading.
    WITH_PRIVATE("with-private", "1.92.0", null, false),

    /// Using the legacy Sass {@code if()} function syntax.
    IF_FUNCTION("if-function", "1.95.0", null, false),

    /// Declaring uppercase reserved CSS function names.
    FUNCTION_NAME("function-name", "1.98.0", null, false),

    /// Using adjacent compounds such as {@code [class]a}.
    ADJACENT_COMPOUNDS("adjacent-compounds", "1.100.0", null, false),

    /// A deprecation explicitly emitted by user-authored integration code.
    USER_AUTHORED("user-authored", null, null, false),

    /// The unused historical calculation-interpolation identifier.
    CALC_INTERP("calc-interp", null, null, false);

    /// Contains the stable command-line and protocol identifier.
    private final String id;

    /// Contains the first deprecating Dart Sass version, or {@code null}.
    private final @Nullable String deprecatedIn;

    /// Contains the version that removed the behavior, or {@code null}.
    private final @Nullable String obsoleteIn;

    /// Records whether explicit opt-in is required.
    private final boolean future;

    /// Creates one deprecation metadata value.
    SassDeprecation(
            String id,
            @Nullable String deprecatedIn,
            @Nullable String obsoleteIn,
            boolean future
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.deprecatedIn = deprecatedIn;
        this.obsoleteIn = obsoleteIn;
        this.future = future;
    }

    /// Returns the stable kebab-case identifier.
    ///
    /// @return the command-line and protocol identifier
    public String id() {
        return id;
    }

    /// Returns the first Dart Sass version that deprecated the behavior.
    ///
    /// @return a semantic version string, or {@code null} when not
    /// version-associated
    public @Nullable String deprecatedIn() {
        return deprecatedIn;
    }

    /// Returns the Dart Sass version that removed the deprecated behavior.
    ///
    /// @return a semantic version string, or {@code null} while active
    public @Nullable String obsoleteIn() {
        return obsoleteIn;
    }

    /// Returns whether this category requires future-deprecation opt-in.
    ///
    /// @return whether the deprecation is not active by default
    public boolean isFuture() {
        return future;
    }

    /// Returns the current lifecycle status in Dart Sass 1.102.0.
    ///
    /// @return the deprecation status
    public SassDeprecationStatus status() {
        if (this == USER_AUTHORED) {
            return SassDeprecationStatus.USER;
        }
        if (future) {
            return SassDeprecationStatus.FUTURE;
        }
        return obsoleteIn == null
                ? SassDeprecationStatus.ACTIVE
                : SassDeprecationStatus.OBSOLETE;
    }

    /// Returns the short registry description shown by Sass tooling.
    ///
    /// @return the description, or {@code null} for hidden registry entries
    public @Nullable String description() {
        return switch (this) {
            case CALL_STRING -> "Passing a string directly to meta.call().";
            case ELSEIF -> "@elseif.";
            case MOZ_DOCUMENT -> "@-moz-document.";
            case RELATIVE_CANONICAL ->
                    "Imports using relative canonical URLs.";
            case NEW_GLOBAL -> "Declaring new variables with !global.";
            case COLOR_MODULE_COMPAT ->
                    "Using color module functions in place of plain CSS functions.";
            case SLASH_DIV -> "/ operator for division.";
            case BOGUS_COMBINATORS ->
                    "Leading, trailing, and repeated combinators.";
            case STRICT_UNARY -> "Ambiguous + and - operators.";
            case FUNCTION_UNITS ->
                    "Passing invalid units to built-in functions.";
            case DUPLICATE_VAR_FLAGS ->
                    "Using !default or !global multiple times for one variable.";
            case NULL_ALPHA -> "Passing null as alpha in the language API.";
            case ABS_PERCENT ->
                    "Passing percentages to the Sass abs() function.";
            case FS_IMPORTER_CWD ->
                    "Using the current working directory as an implicit load path.";
            case CSS_FUNCTION_MIXIN ->
                    "Function and mixin names beginning with --.";
            case MIXED_DECLS ->
                    "Declarations after or between nested rules.";
            case FEATURE_EXISTS -> "meta.feature-exists";
            case COLOR_4_API ->
                    "Certain uses of built-in sass:color functions.";
            case COLOR_FUNCTIONS ->
                    "Using global color functions instead of sass:color.";
            case LEGACY_JS_API -> "Legacy JS API.";
            case IMPORT -> "@import rules.";
            case GLOBAL_BUILTIN ->
                    "Global built-in functions that are available in sass: modules.";
            case TYPE_FUNCTION -> "Functions named \"type\".";
            case COMPILE_STRING_RELATIVE_URL ->
                    "Passing a relative URL to string compilation.";
            case MISPLACED_REST ->
                    "A rest parameter before a positional or named parameter.";
            case WITH_PRIVATE ->
                    "Configuring private variables in module loading.";
            case IF_FUNCTION ->
                    "The Sass if($condition, $if-true, $if-false) function.";
            case FUNCTION_NAME -> "Uppercase reserved function names.";
            case ADJACENT_COMPOUNDS ->
                    "Adjacent compound selectors like [class]a.";
            case USER_AUTHORED, CALC_INTERP -> null;
        };
    }

    /// Returns the category with the given stable identifier.
    ///
    /// @param id the kebab-case identifier
    /// @return the matching category, or {@code null} when unknown
    public static @Nullable SassDeprecation fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (var deprecation : values()) {
            if (deprecation.id.equals(id)) {
                return deprecation;
            }
        }
        return null;
    }

    /// Returns active deprecations introduced by or before a version.
    ///
    /// Obsolete and non-versioned categories are excluded.
    ///
    /// @param major the non-negative major version
    /// @param minor the non-negative minor version
    /// @param patch the non-negative patch version
    /// @return an immutable deprecation set
    public static @Unmodifiable Set<SassDeprecation> forVersion(
            int major,
            int minor,
            int patch
    ) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException(
                    "version components must be non-negative"
            );
        }
        return Arrays.stream(values())
                .filter(value -> value.deprecatedIn != null)
                .filter(value -> value.obsoleteIn == null)
                .filter(value -> compareVersion(
                        Objects.requireNonNull(value.deprecatedIn),
                        major,
                        minor,
                        patch
                ) <= 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    /// Compares a three-component registry version with an explicit version.
    private static int compareVersion(
            String version,
            int major,
            int minor,
            int patch
    ) {
        var parts = version.split("\\.", -1);
        var leftMajor = Integer.parseInt(parts[0]);
        var leftMinor = Integer.parseInt(parts[1]);
        var leftPatch = Integer.parseInt(parts[2]);
        var comparison = Integer.compare(leftMajor, major);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(leftMinor, minor);
        return comparison != 0
                ? comparison
                : Integer.compare(leftPatch, patch);
    }
}
