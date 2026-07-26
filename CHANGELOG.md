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
- Explicit `JavaFXStylesheetResolver` support for retained BSS imports from
  application-defined resource schemes.
- Structured diagnostics, Sass traces, loaded URLs, and CSS source maps.
- Separate reusable core library and shaded command-line artifacts.
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
- The CLI does not yet expose load paths, source maps, stdin, watch mode, or
  custom resource resolvers.
