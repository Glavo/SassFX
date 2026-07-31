# JavaFX Runtime Oracles

SassFX implements JavaFX CSS and BSS without linking JavaFX into the product.
Dedicated oracle source sets compare its output with real OpenJFX releases;
they are excluded from runtime classpaths, publication variants, and
distributable JARs.

## Version Matrix

| Target | OpenJFX artifact | BSS | Minimum oracle JDK | Boundary |
|---|---:|---:|---:|---|
| 8 | JDK-provided JavaFX 8 | 5 | Java 8 with JavaFX | v5 converters and reader |
| 17 | 17.0.20 | 6 | 17 | pre-fix blend modes |
| 18 | 18.0.2 | 6 | 17 | extended blend modes |
| 23 | 23.0.2 | 6 | 21 | CSS transitions |
| 25 | 25.0.4 | 7 | 23 | user-preference media queries |
| 26 | 26.0.2 | 8 | 24 | viewport media, multiple rules, advanced easing |
| 27 | 27-ea+25 | 9 | 25 | conditional imports and platform media |

JavaFX 17 through 27 run in isolated processes with one matching
`javafx-base` and `javafx-graphics` pair on the module path. Use a JDK
distribution that does not bundle JavaFX, because bundled modules may shadow
or conflict with the pinned artifacts.

Gradle-provisioned launchers are pinned to Adoptium so a locally installed
full JDK cannot supply an older JavaFX module. At startup, each modular oracle
also reads `javafx.runtime.version` from the loaded `javafx.base` module and
fails if it does not match the requested target.

Each task accepts a version-specific override:

```text
-PjavaFX17OracleJavaHome=<jdk>
-PjavaFX18OracleJavaHome=<jdk>
-PjavaFX23OracleJavaHome=<jdk>
-PjavaFX25OracleJavaHome=<jdk>
-PjavaFX26OracleJavaHome=<jdk>
-PjavaFX27OracleJavaHome=<jdk>
```

`-PjavaFXOracleJavaHome=<jdk>` supplies one compatible JDK for every modular
oracle. Use a distribution that does not bundle JavaFX.

Run the modular matrix with:

```text
./gradlew :sassfx-core:verifyJavaFXCssOracles
```

## JavaFX 8

JavaFX 8 cannot load the Java 17 SassFX classes. Its oracle therefore uses two
processes:

1. A Java 17 generator writes textual CSS and SassFX BSS v5.
2. A Java 8 helper, compiled to class-file version 52, reflectively invokes the
   JavaFX 8 BSS writer, compares both documents byte-for-byte, and loads the
   SassFX document with the real v5 reader.

The Java 8 JDK must include JavaFX 8. Configure it explicitly:

```text
./gradlew :sassfx-core:verifyJavaFX8CssOracle -PjavaFX8OracleJavaHome=<javafx-8-jdk>
```

Run every configured oracle with:

```text
./gradlew :sassfx-core:verifyAllJavaFXCssOracles -PjavaFX8OracleJavaHome=<javafx-8-jdk>
```

CI should pin the JavaFX 8 vendor, update release, archive checksum, and
`javaFX8OracleJavaHome` instead of using an arbitrary installed JDK.

## Offline Artifacts

`-PjavaFXOracleDirectory=<directory>` replaces Maven resolution. The directory
must contain `javafx-base.jar` and `javafx-graphics.jar` under each modular
version directory:

```text
<directory>/17/javafx-base.jar
<directory>/17/javafx-graphics.jar
...
<directory>/27/javafx-base.jar
<directory>/27/javafx-graphics.jar
```

JavaFX 23 through 27 emit expected warnings while the oracle deliberately
loads transition BSS. Those releases write transition converter names but do
not reconstruct the converters in `Stylesheet.loadBinary`; SassFX verifies
this upstream limitation and rejects transition declarations for BSS output.

## Property Coverage

Every modular oracle compares accepted textual CSS with the pinned parser.
Selected fixtures additionally compare the complete BSS document
byte-for-byte, including the header, string table, converter graph, selectors,
declarations, media payload, and font faces.

Across textual and byte-exact checks, the executable matrix currently covers:

- type, class, ID, ordinary pseudo-class, and functional pseudo-class
  selectors, including JavaFX token concatenation and `dir(...)` orientation;
- case-insensitive declaration names and source-ordered property lookup
  normalization, including self, prior, forward, unresolved, and import-local
  lookup state, plus rejection of declaration names outside OpenJFX's ASCII
  identifier grammar;
- legacy value tokenization, including ASCII identifiers and units, rejection
  of modern CSS escapes and non-ASCII unquoted tokens, Unicode string and URL
  payloads, multiline ordinary strings, JavaFX block and line comments, and
  marker-free non-ASCII JavaFX CSS;
- structural trivia boundaries in functional pseudo-classes, import
  arguments, and media conditions, including LF-terminated line comments and
  product-side rejection of comments that would consume stylesheet structure;
- font-face URL, local, and reference sources, optional format hints,
  descriptor token concatenation, comment boundaries, byte-exact BSS
  persistence, and rejection of unquoted local names that OpenJFX silently
  concatenates;
- font family, size, style, weight, and shorthand parsing across JavaFX 8–27,
  including quoted generic-family normalization, keyword and angle sizes,
  retained shorthand slots, discarded line height and small-caps, and
  rejection of surplus terms that OpenJFX silently ignores;
- JavaFX 23–27 transition text conversion, including target-specific easing
  functions and rejection of parser inputs that fail during conversion or
  contain surplus tokens that OpenJFX silently discards;
- scalar sizes, durations, URLs, blend modes, shadow effects, and
  generic quoted strings, including empty strings and special keyword/color
  recognition in root and imported plain-CSS stylesheets;
- global declaration keywords, font-smoothing and versioned blend-mode string
  storage, stroke cap/join/type enums, and stroke dash arrays, including
  byte-exact JavaFX 8–17 generic blend lookup and color behavior, JavaFX 18+
  direct storage, enum canonicalization, and rejection of silently discarded
  terms;
- plain-CSS declaration evaluation, including named colors in layered paints
  and registered-property precedence at the top level and inside gradients
  and shadow effects;
- generic single sizes and space-separated size sequences across every
  non-time JavaFX `SizeUnits` value, including negative and mixed-unit series;
- padding, region and border-image insets, border widths, border-image widths,
  border-image slices, and background and border radii, including
  one-to-four-value expansion, layered property lookups, horizontal and
  vertical radius axes, trailing `/`, per-corner pixel-zero normalization,
  optional trailing `fill`, byte-exact CSS/BSS parity, and rejection of fifth
  values, repeated `/` markers, or layers that OpenJFX silently discards;
- case-insensitive function-name prefix dispatch for colors, effects,
  gradients, image patterns, ladders, Region references, and border
  `segments(...)`, plus rejection of unsupported leading functions;
- solid, lookup, derived, ladder, gradient, image-pattern, and repeating
  image-pattern paints, plus `region("selector")` references;
- Region background and border colors, insets, radii, widths, images,
  positions, repeats, sizes, slices, and stroke styles;
- JavaFX 25–27 media-query framing and JavaFX 27 conditional imports,
  including nested import parser-state isolation.

The oracle does not submit a consuming media line comment directly to JavaFX
25: that parser path grows its media-token list until the process exhausts
memory. Instead, safe line-comment cases run against the pinned parser and the
oracle separately requires SassFX to reject each dangerous boundary case.

`BssTargetTest.coversEverySupportedConverterFamilyWithPinnedFixtures` checks
the pinned OpenJFX bytes and a composite fixture against every converter class
emitted by the serializer. The executable matrix covers scalar, sequence,
font, enum, duration, string, URL, boolean, insets, paint, stop, derive,
ladder, effect, background-layout, border-image, radii, margins, border-paint,
and border-style converter trees.

The JavaFX 8 fixture covers the same Region converter families in BSS v5 and
also guards its observable gradient-cycle quirk: both linear and radial
`repeat` gradients are stored as `REFLECT`.

All pinned releases also verify `region(...)`'s historical parser behavior:
the first argument must be quoted but may be empty, later arguments are
ignored, a case-insensitive `region` function-name prefix is sufficient, and
the resulting `SPECIAL-REGION-URL:` value uses `StringConverter`.
