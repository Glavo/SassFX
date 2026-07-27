# Changelog

All notable changes to SCSSFX will be documented in this file.

## 0.1.0-SNAPSHOT — Unreleased

This is the first development release.

### Added

- Pure Java 17 compiler for SCSS, indented Sass, and plain-CSS library inputs.
- Standard CSS, validated JavaFX CSS, and JavaFX BSS output targets.
- `JavaFXTarget` release selection from JavaFX 8 through JavaFX 27.
- BSS v5–v9 serialization without a JavaFX runtime dependency.
- Versioned JavaFX media-query, conditional-import, transition, paint, font,
  URL, duration, and effect validation.
- Direct legacy JavaFX linear and radial gradients in textual CSS and BSS,
  including layered and shorthand paints, cycle methods, and property lookups.
- Explicit `JavaFXStylesheetResolver` support for retained BSS imports from
  application-defined resource schemes.
- Ordered synchronous `SassImporter` and compiler-managed `SassFileImporter`
  support with canonical URL identity, importer-owned relative loads,
  legacy-import context, load caching, and source-map URLs.
- Pure Java `SassNodePackageImporter` and CLI `--pkg-importer=node` support
  for contextual `pkg:` URLs, ancestor `node_modules` lookup, Sass package
  exports, root metadata, subpaths, partials, and import-only files.
- Synchronous Java `SassCustomFunction` callbacks with Sass signature binding,
  lossless value bridging, rest keywords, `sass:meta` visibility, dependency
  module support, source-associated failures, and concurrent compilation.
- Configurable synchronous `SassLogger` delivery, typed Dart Sass 1.101.3
  deprecation metadata, dependency warning suppression, repetitive-warning
  limits, and fatal, silence, future, and verbose deprecation policies.
  Function-unit diagnostics cover list indexes, random limits, legacy color
  constructors, color weights, channel and alpha updates, and hue adjustment.
  Legacy color-function diagnostics cover global and module channel readers
  plus all nine deprecated color adjustment functions with migration guidance.
  Global built-in diagnostics cover list, map, math, meta, selector, string,
  and color aliases, including renamed replacements, first-class references,
  and CSS-overload exclusions. Percentage `abs()` and `feature-exists()`
  diagnostics follow their dedicated deprecation categories. Color-module
  compatibility diagnostics cover numeric `invert()`, `grayscale()`, and
  `opacity()` fallbacks plus both Microsoft-filter `alpha()` forms while
  preserving their plain-CSS output. Parser and selector diagnostics cover
  Mozilla document rules, private module configuration, arguments after rest
  expansion, adjacent compounds, and bogus combinators in style rules,
  `@extend`, and selector operations.
  Importer and compile API diagnostics cover relative string source URLs,
  relative canonical importer results before loading, and successful implicit
  current-working-directory fallback loads. Relative forms remain operational
  during their Dart Sass 1.x deprecation period.
- CLI load paths, ordered platform-aware `SASS_PATH` resolution across compile,
  update/watch, and interactive modes, quiet output, dependency warning
  suppression, and fatal, silence, future, and verbose deprecation options.
  Parser and evaluation-time deprecations use the same policy in immediate,
  watch, and interactive modes; option warnings are emitted once per
  invocation.
- Dart Sass-compatible CLI `-s` style alias, negatable input, source-map
  content, quiet, quiet-dependency, and verbose flags, plus hidden
  `--precision` and `--async` compatibility switches used by upstream tooling.
- CLI stdin and magic `-` input, plain-CSS roots, multiple `input:output`
  mappings, recursive directory compilation, partial exclusion, and
  process-compatible usage, Sass-data, and IO exit statuses. Usage failures
  use Dart Sass's standard-output diagnostic-plus-usage presentation, while
  Sass and IO failures remain on standard error.
- CLI source-map sidecars and embedded maps, relative and absolute source
  URLs, imported-source ordering, on-disk path-case preservation, aligned
  embedded source contents, UTF-8 charset markers, browser-readable error CSS,
  Dart Sass-compatible error-message string escaping, atomic output
  replacement, and stop-on-error behavior.
- CLI Unicode and ASCII diagnostic frames, forced or terminal-detected ANSI
  styling, complete file/stdin source lines, deprecation identifiers, optional
  Java implementation traces, named-color interpolation warnings with
  dependency provenance, and traced unexpected-failure status 255.
- CLI `--update`, `--watch`/`-w`, and `--[no-]poll` modes with transitive file
  freshness checks, recursive native or polling observation, debounced change
  batches, directory-entrypoint discovery, candidate-level import-resolution
  tracking, selective dependency recompilation, missing/conflicting/fallback
  dependency recovery, shared-dependency fan-out, `meta.load-css()` tracking,
  module-loop recovery, unrelated-file isolation, and owned output/source-map
  deletion.
- CLI `--interactive`/`-i` SassScript shell with persistent variables, modules,
  load paths, package imports, deprecation state, recoverable line failures,
  configured/global/built-in `@use`, and Dart Sass-compatible prompt, stream,
  and diagnostic behavior.
- Pure Java Embedded Sass Protocol 3.2.0 executable and CLI `--embedded`
  endpoint with length-delimited framing, version negotiation, concurrent
  compilation IDs, diagnostics, source maps, string or path compilation,
  contents and file importer callbacks, global and host function callbacks,
  recursive Sass value conversion, argument-list access tracking, and opaque
  compiler function and mixin identity. Source-content embedding, color and
  ASCII alert presentation, structured failure spans, and missing-file
  failures follow the corresponding protocol request fields. Contents
  importers validate non-canonical scheme placement and lowercase grammar,
  propagate containing URLs only for contextual schemes, and reject canonical
  results that reuse those schemes. Contents/file callbacks validate returned
  URLs, preserve interleaved declaration order, propagate null and error
  results, and retain owner-first relative resolution across opaque canonical
  URL schemes.
- Structured diagnostics, multi-frame Sass traces, loaded URLs, immutable
  failure-source snapshots, and CSS source maps. CLI and Embedded failures
  render root, path, and imported source context from the captured compilation
  text without re-reading URLs.
- Separate reusable core library and shaded embedded and command-line
  artifacts.
- Fixed sass-spec and multi-version OpenJFX runtime compatibility oracles.

### Compatibility

- The enabled pinned sass-spec set passes 13,924 fixtures with no failures.
- Runtime oracles cover JavaFX 8, 17, 18, 23, 25, 26, and 27.
- JavaFX BSS format selection is v5 for JavaFX 8, v6 for JavaFX 9–24,
  v7 for JavaFX 25, v8 for JavaFX 26, and v9 for JavaFX 27.

### Known limitations

- The release remains a development snapshot and is not published to a public
  artifact repository.
- The BSS backend rejects unsupported JavaFX selector, converter, and value
  shapes instead of emitting potentially unreadable binary output.
- JavaFX 23–27 transition declarations are available in textual CSS but are
  rejected by BSS because the corresponding OpenJFX readers cannot restore
  their transition converters.
- The JavaFX property/value runtime-oracle matrix remains incomplete.
- The CLI does not expose application-defined importer or function callbacks.
