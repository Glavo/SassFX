# Changelog

Notable user-visible changes to SassFX are recorded here.

## 0.1.0 — Unreleased

The initial release provides a pure Java 17 Sass implementation with library,
command-line, Embedded Sass, and Gradle interfaces.

### Compiler and Java API

- Compiles SCSS, indented Sass, and plain CSS without Dart, Node.js, FFI,
  native libraries, or a JavaFX runtime.
- Implements the Dart Sass 1.102.0 language and deprecation baseline, including
  modules, legacy imports, calculations, modern color spaces, selectors, and
  first-class functions and mixins.
- Produces standard CSS, JavaFX CSS, and JavaFX binary stylesheets.
- Provides structured diagnostics, loaded-URL metadata, source maps, custom
  importers, Node package resolution, custom functions, and dependency
  tracking.
- Exposes immutable compile options through `CompileOptions.DEFAULT` and
  focused derivation methods.

### Frontends

- Provides a Sass-compatible CLI for file, standard-input, mapped-directory,
  update, watch, and interactive workflows.
- Implements Embedded Sass Protocol 3.2.0 with concurrent compilation,
  importer and host-function callbacks, diagnostics, source maps, and typed
  Sass values.
- Adds the `org.glavo.sassfx` Gradle plugin and cacheable `compileScss` task,
  including Java resource integration and stale-output cleanup.
- Supports optional GraalVM Native Image builds for the complete CLI and
  Embedded Protocol endpoint.

### JavaFX output

- Targets JavaFX 8 through 27 without linking against JavaFX at runtime.
- Validates JavaFX selectors, at-rules, properties, values, media conditions,
  imports, fonts, effects, paints, transitions, and converter-specific value
  shapes.
- Writes BSS versions 5 through 9 and supports application-defined retained
  stylesheet resolution through `BssTarget`.
- Preserves OpenJFX parsing and serialization behavior covered by the pinned
  runtime oracle matrix.

### Verification and delivery

- Runs 13,926 enabled fixtures from the pinned sass-spec corpus; five upstream
  fixtures remain skipped by their own metadata.
- Checks JavaFX output against pinned JavaFX 8, 17, 18, 23, 25, 26, and 27
  runtimes.
- Verifies that published artifacts contain no JavaFX runtime, FFI, native
  content, local upstream checkout, or frontend-only core dependency.
- Publishes Maven artifacts with JReleaser, the Gradle plugin through the
  Plugin Portal, and a GitHub Release from version tags.

### Known limitations

- JavaFX CSS and BSS reject syntax or value shapes whose meaning cannot be
  preserved by the selected JavaFX target.
- JavaFX 23 through 27 transition declarations are supported in textual CSS
  but not BSS because those runtimes cannot reconstruct the corresponding
  transition converters.
- The CLI does not provide application-defined importer or custom-function
  callbacks; those integrations are available through the Java API and
  Embedded Sass Protocol.
