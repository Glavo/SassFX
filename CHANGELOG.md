# Changelog

Notable changes to SassFX are recorded here.

## 0.1.0-SNAPSHOT — Unreleased

The first development release provides a pure Java 17 Sass implementation
with library, command-line, Embedded Sass, and Gradle interfaces.

### Compiler and Java API

- Compiles SCSS, indented Sass, and plain CSS without Dart, Node.js, FFI,
  native libraries, or a JavaFX runtime.
- Produces standard CSS, validated JavaFX CSS, and JavaFX binary stylesheets.
- Provides immutable compile results with diagnostics, loaded URLs, source
  maps, and retained source text for failure reporting.
- Supports synchronous custom importers, file importers, Node package
  resolution, custom functions, logging, and deprecation policies.
- Exposes explicit containing-URL access tracking for importer adapters while
  keeping evaluator values behind the typed custom-function boundary.
- Implements the Dart Sass 1.102.0 language and diagnostic baseline, including
  modules, legacy imports, calculations, modern color spaces, first-class
  functions and mixins, selector operations, and deprecation metadata.
- Matches Dart Sass 1.102.0 legacy RGB percentage serialization, plain-CSS
  `if()` branch serialization, and the Rec. 2020 gamma 2.4 transfer curve.

### Command line

- Supports file, standard-input, mapped-file, and recursive-directory
  compilation with atomic output replacement.
- Provides source-map sidecars and embedded maps, browser-readable error CSS,
  configurable diagnostics, load paths, `SASS_PATH`, and the Node package
  importer.
- Supports `--update`, native or polling `--watch`, selective dependency
  recompilation, and a persistent interactive SassScript shell.
- Uses a single strict `--target` value: `css`, `css/javafx@8` through
  `css/javafx@27`, or `bss/javafx@8` through `bss/javafx@27`.
- Supports GraalVM Native Image builds with end-to-end CSS, BSS, version, and
  Embedded Protocol smoke checks on Linux and Windows.

### Embedded Sass Protocol

- Implements Embedded Sass Protocol 3.2.0 framing, version negotiation, and
  concurrent compilation dispatch.
- Supports string and path inputs, diagnostics, source maps, importers, host
  functions, compiler function and mixin values, and recursive Sass value
  conversion.
- Preserves protocol metadata such as string quoting, list separators, map
  order, compound units, missing color channels, and argument-list identity.

### Gradle plugin

- Adds the `org.glavo.sassfx` plugin and cacheable `compileScss` task.
- Tracks source, partial, and load-path trees; removes stale output; detects
  output collisions; and publishes generated files only after the complete
  task succeeds.
- Supports Gradle build caching, configuration caching, and automatic Java
  resource integration.

### JavaFX output

- Models JavaFX targets 8 through 27 and BSS versions 5 through 9.
- Validates selectors, at-rules, media conditions, conditional imports,
  transitions, properties, and values before serialization.
- Serializes every supported non-transition JavaFX converter family,
  including paints, fonts, effects, URLs, durations, backgrounds, borders,
  and retained stylesheet imports.
- Resolves retained imports from application-defined resource schemes without
  adding a JavaFX runtime dependency.

### Verification

- The pinned sass-spec corpus passes 13,926 enabled fixtures; five upstream
  fixtures remain skipped by their own metadata.
- Runtime oracles cover JavaFX 8, 17, 18, 23, 25, 26, and 27.
- Artifact checks keep JavaFX, FFI, native content, local upstream checkouts,
  and frontend-only dependencies out of the core runtime.
- Staged-publication verification resolves the core library and Gradle plugin
  from an isolated repository and runs the published CLI and Embedded JARs.

### Delivery

- Stages Maven publications for the core library, CLI, Embedded endpoint, and
  Gradle plugin, then validates, signs, and deploys them with JReleaser.
- Configures Gradle Plugin Portal publication for `org.glavo.sassfx`.
- Creates a GitHub Release with generated notes after both artifact
  repositories accept the release.
- Adds Linux and Windows CI, the full JavaFX oracle matrix, and tag-driven
  release automation.

### Known limitations

- JavaFX CSS and BSS intentionally reject unsupported syntax and value shapes
  instead of producing output that may fail at runtime.
- JavaFX 23–27 transition declarations are supported in textual CSS but not
  BSS because the corresponding OpenJFX readers do not reconstruct their
  transition converters.
- The command line does not provide application-defined importer or custom
  function callbacks; those integrations are available through the Java API
  and Embedded Sass Protocol.
