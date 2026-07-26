# Upstream Compatibility Baselines

SCSSFX is an independent Java implementation whose compatibility work is
validated against fixed upstream snapshots.

| Component | Baseline | Purpose |
| --- | --- | --- |
| Dart Sass | `1.101.3` (`e8c12331ea5304a1d641d6a6bd4cb526cb3800b9`) | Sass language and diagnostic behavior |
| sass-spec | `24e61bf508f5b48968546fbf1a4c16af61048709` | Sass language conformance suite |
| JavaFX | `17.0.20` | JavaFX CSS and BSS version 6 oracle |
| JavaFX | `27-ea+25` | JavaFX CSS and BSS version 9 oracle |

Upstream source checkouts are development-only references. The build,
tests, and published artifacts must remain self-contained and must not depend
on a local upstream checkout.

OpenJFX source code is not copied into SCSSFX. JavaFX CSS and BSS support is an
independent implementation validated through public behavior and test-only
compatibility oracles.

The isolated JavaFX CSS oracles are run with:

```shell
./gradlew :scssfx-core:verifyJavaFxCssOracles
```

The task resolves the pinned platform-specific OpenJFX artifacts, runs the
JavaFX 17 oracle on a Java 17 toolchain, and runs the JavaFX 27 oracle on a
Java 25 toolchain. The `javaFxOracle` source set and its dependencies are not
included in product runtime or publication variants.

For offline development, `-PjavaFxOracleDirectory=<directory>` selects a
directory containing `17/javafx-base.jar`, `17/javafx-graphics.jar`,
`27/javafx-base.jar`, and `27/javafx-graphics.jar`. If the selected Java 25
toolchain bundles an older JavaFX module, use
`-PjavaFx27OracleJavaHome=<java-home>` to select a Java 25 installation without
bundled JavaFX modules.
