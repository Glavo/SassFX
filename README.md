# SassFX

SassFX is a pure Java 17 Sass compiler. It compiles SCSS, indented Sass, and
plain CSS to standard CSS, validated JavaFX CSS, or JavaFX binary stylesheets
(BSS).

SassFX does not invoke Dart Sass or Node.js, use FFI, load JavaFX, or ship
native libraries.

## Features

- Pure Java compiler and frontends targeting Java 17.
- Standard CSS, JavaFX CSS, and JavaFX BSS output.
- JavaFX 8 through 27 target models and BSS versions 5 through 9.
- Java API with source maps, diagnostics, custom importers, and custom
  functions.
- Cacheable Gradle plugin for compiling project resources.
- Command-line and Embedded Sass Protocol frontends.

## Modules

| Module | Purpose |
| --- | --- |
| `sassfx-core` | Compiler and reusable Java API |
| `sassfx-cli` | Standalone command-line application |
| `sassfx-embedded` | Embedded Sass Protocol endpoint for host tools |
| `sassfx-gradle-plugin` | Gradle build integration |

The core artifact is a regular library JAR. The CLI, Embedded endpoint, and
Gradle plugin are published as self-contained JARs with relocated runtime
dependencies.

## Gradle Plugin

Apply the plugin and configure the desired output target:

```kotlin
plugins {
    java
    id("org.glavo.sassfx") version "<version>"
}

sassfx {
    target.set("css/javafx@21")
    style.set("compressed")
    loadPaths.from(layout.projectDirectory.dir("src/shared/scss"))
}
```

By default, `compileScss` compiles entrypoints under `src/main/scss` into
`build/generated/sassfx/main`. When the Java plugin is present, the generated
files are included in `processResources` automatically.

The target selector accepts:

| Selector | Output |
| --- | --- |
| `css` | Standard CSS |
| `css/javafx@8` through `css/javafx@27` | Validated JavaFX CSS |
| `bss/javafx@8` through `bss/javafx@27` | JavaFX BSS |

## Java API

Add the core library from Maven Central:

```kotlin
dependencies {
    implementation("org.glavo:sassfx-core:<version>")
}
```

Compile a stylesheet to standard CSS:

```java
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;

import java.nio.file.Files;
import java.nio.file.Path;

var result = new SassCompiler().compile(
        SassSource.fromFile(Path.of("style.scss")),
        CssTarget.DEFAULT
);

Files.writeString(Path.of("style.css"), result.output());
```

Use `JavaFXCssTarget` for validated JavaFX CSS and `BssTarget` for binary
stylesheets. `CompileOptions` configures source maps, load paths, diagnostics,
custom importers, and custom Sass functions.

## Command Line

Run the self-contained CLI JAR:

```shell
java -jar sassfx-cli-<version>.jar style.scss -o style.css
```

Select a JavaFX target when needed:

```shell
java -jar sassfx-cli-<version>.jar \
  --target bss/javafx@27 \
  style.scss \
  -o style.bss
```

Use `--help` for the complete option reference:

```shell
java -jar sassfx-cli-<version>.jar --help
```

## Embedded Sass

The `sassfx-embedded` artifact provides a standalone Embedded Sass Protocol
endpoint:

```shell
java -jar sassfx-embedded-<version>.jar
```

The CLI artifact exposes the same endpoint through `--embedded`. This
interface is intended for Sass host implementations; ordinary application
builds should normally use the Gradle plugin, CLI, or Java API.

## Building

Use the checked-in Gradle Wrapper:

```shell
./gradlew check
```

On Windows:

```powershell
.\gradlew.ps1 check
```

Product code and artifacts target Java 17. Generating Javadoc requires a JDK
25 toolchain because the sources use Markdown-style `///` documentation
comments.

## Project Documentation

- [CHANGELOG.md](CHANGELOG.md) records user-visible changes and current
  limitations.
- [UPSTREAM.md](UPSTREAM.md) records pinned Sass compatibility baselines.
- [ORACLES.md](ORACLES.md) describes JavaFX runtime verification.
- [RELEASING.md](RELEASING.md) describes the release workflow.
- [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) contains attribution and
  compatibility-source notices.

## License

SassFX is licensed under the [Mozilla Public License 2.0](LICENSE).
