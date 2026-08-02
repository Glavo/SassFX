# Upstream Compatibility Baselines

SassFX is an independent Java implementation. Its behavior is verified
against the following fixed upstream releases.

| Component | Baseline | Purpose |
| --- | --- | --- |
| Dart Sass | `1.102.0` (`45d1efe6517319ecd7b1409f1fa8355f969b0547`) | Sass language and diagnostic behavior |
| Embedded Sass Protocol | `3.2.0` (`embedded-protocol-3.2.0`) | Binary host protocol and message schema |
| sass-spec | `67c3b83a26fd3f9772dd0d3e318fe32aefb38eef` | Sass language conformance suite |
| JavaFX | `8u352` | JavaFX CSS and BSS version 5 oracle |
| JavaFX | `17.0.20` | JavaFX CSS and BSS version 6 baseline |
| JavaFX | `18.0.2` | Extended blend-mode boundary |
| JavaFX | `23.0.2` | Transition boundary |
| JavaFX | `25.0.4` | Preference media queries and BSS version 7 |
| JavaFX | `26.0.2` | Viewport media, advanced easing, and BSS version 8 |
| JavaFX | `27-ea+25` | Conditional imports and BSS version 9 |

Local upstream source checkouts are reference material only. Builds, tests,
and published artifacts must remain self-contained.

## Dart Sass test inventory

The [Dart Sass 1.102.0 test coverage manifest](gradle/verification/dart-sass-1.102.0-tests.tsv)
accounts for all 59 Dart files under the upstream `test` tree. The pinned
inventory contains 1,068 lexical `test()` calls and 351 lexical `group()`
calls; parameterized Dart helpers may generate additional runtime cases. Each
entry identifies the Java test source that owns the observable contract and
uses one of four dispositions:

- `PORTED` is a direct translation of the implementation-specific cases.
- `EQUIVALENT` exercises the same observable contract through the Java API.
- `ADAPTED` replaces behavior tied to the Dart, browser, or Node runtime with
  the corresponding Java surface.
- `SUPPORT` identifies an upstream helper or runtime wrapper with no standalone
  test cases.

The ordinary core test task validates the inventory checksum, aggregate
counts, dispositions, and every mapped repository path. This makes additions
or removals from the pinned upstream suite an explicit compatibility change
rather than an undocumented test gap.

The pinned sass-spec selection contains 13,890 enabled upstream cases and 36
SassFX-owned integration cases; all of them pass. Five additional sass-spec
cases retain their own `todo: dart-sass` marking and are skipped for the pinned
Dart Sass version. They are upstream exclusions, not unsupported behavior in
the Dart Sass 1.102.0 compatibility baseline.

OpenJFX source code is not copied into SassFX. JavaFX CSS and BSS support is an
independent implementation validated through public behavior and test-only
compatibility oracles.

The isolated JavaFX CSS oracles are run with:

```shell
./gradlew :sassfx-core:verifyAllJavaFXCssOracles \
  -PjavaFX8OracleJavaHome=<javafx-8-jdk>
```

The modular tasks resolve pinned platform-specific OpenJFX artifacts and use a
compatible JDK for each release. The JavaFX 8 oracle combines a Java 17 input
generator with a separate Java 8 process. Oracle source sets and dependencies
are excluded from runtime and publication variants.

See [ORACLES.md](ORACLES.md) for Java-home overrides, offline artifact layout,
and the exact verification model.
