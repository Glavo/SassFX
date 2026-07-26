# Upstream Compatibility Baselines

SCSSFX is an independent Java implementation whose compatibility work is
validated against fixed upstream snapshots.

| Component | Baseline | Purpose |
| --- | --- | --- |
| Dart Sass | `1.101.3` (`e8c12331ea5304a1d641d6a6bd4cb526cb3800b9`) | Sass language and diagnostic behavior |
| sass-spec | `24e61bf508f5b48968546fbf1a4c16af61048709` | Sass language conformance suite |
| JavaFX | `8u352` | JavaFX CSS and BSS version 5 oracle |
| JavaFX | `17.0.20` | JavaFX CSS and BSS version 6 baseline |
| JavaFX | `18.0.2` | Extended blend-mode boundary |
| JavaFX | `23.0.2` | Transition boundary |
| JavaFX | `25.0.4` | Preference media queries and BSS version 7 |
| JavaFX | `26.0.2` | Viewport media, advanced easing, and BSS version 8 |
| JavaFX | `27-ea+25` | Conditional imports and BSS version 9 |

Upstream source checkouts are development-only references. The build,
tests, and published artifacts must remain self-contained and must not depend
on a local upstream checkout.

OpenJFX source code is not copied into SCSSFX. JavaFX CSS and BSS support is an
independent implementation validated through public behavior and test-only
compatibility oracles.

The isolated JavaFX CSS oracles are run with:

```shell
./gradlew :scssfx-core:verifyAllJavaFxCssOracles \
  -PjavaFx8OracleJavaHome=<javafx-8-jdk>
```

The modular tasks resolve pinned platform-specific OpenJFX artifacts and use
JDK launchers compatible with each release's class-file version. The JavaFX 8
oracle uses a separate Java 8 process and a Java 17 input generator. Oracle
source sets and dependencies are not included in product runtime or
publication variants.

See [ORACLES.md](ORACLES.md) for Java-home overrides, offline artifact layout,
and the exact verification model.
