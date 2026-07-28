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

The byte-exact matrix currently covers:

- scalar sizes, durations, URLs, fonts, blend modes, shadow effects, and
  generic quoted strings, including empty strings and special keyword/color
  recognition;
- generic single sizes and space-separated size sequences across every
  non-time JavaFX `SizeUnits` value, including negative and mixed-unit series;
- solid, lookup, derived, ladder, gradient, image-pattern, and repeating
  image-pattern paints, plus `region("selector")` references;
- Region background and border colors, insets, radii, widths, images,
  positions, repeats, sizes, slices, and stroke styles;
- JavaFX 25–27 media-query framing and JavaFX 27 conditional imports.

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
