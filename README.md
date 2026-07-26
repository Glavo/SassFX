# SCSSFX

SCSSFX is an independent Sass compiler implemented in Java 17. It compiles
SCSS, indented Sass, and plain CSS through its library API to standard CSS,
validated JavaFX CSS, or JavaFX binary stylesheets (BSS).

Product code does not load JavaFX, invoke Dart Sass or Node.js, use FFI, or
ship native libraries. The project is currently a `0.1.0-SNAPSHOT`
development build.

## Highlights

- Pure Java 17 compiler and serializers.
- SCSS, indented Sass, and plain-CSS library inputs.
- Standard CSS, JavaFX CSS, and BSS output targets.
- Configurable `JavaFXTarget` values from JavaFX 8 through JavaFX 27.
- BSS versions 5 through 9 without a JavaFX runtime dependency.
- Explicit resolver API for retained JavaFX CSS imports from application
  resource schemes.
- Structured diagnostics, loaded-URL metadata, and CSS source maps.
- Fixed Dart Sass, sass-spec, and OpenJFX compatibility oracles.

The core runtime uses Jackson Core. The command-line application additionally
uses Picocli; both are pure Java dependencies.

## Building

Use the checked-in Gradle Wrapper:

```shell
./gradlew assemble
./gradlew check
```

On Windows:

```powershell
.\gradlew.ps1 assemble
.\gradlew.ps1 check
```

Product code and artifacts target Java 17. `check` runs core and CLI unit
tests, the curated sass-spec suite, the Java 17 shaded-CLI smoke test, artifact
boundary checks, and source-isolation checks. Generating Javadoc additionally
requires a JDK 25 toolchain because Markdown-style `///` Javadoc is used.

The build produces:

| Project | Purpose | Main artifact |
| --- | --- | --- |
| `scssfx-core` | Reusable compiler API | `scssfx-core/build/libs/scssfx-core-0.1.0-SNAPSHOT.jar` |
| `scssfx-cli` | Executable command-line frontend | `scssfx-cli/build/libs/scssfx-cli-0.1.0-SNAPSHOT.jar` |

The core JAR has the automatic module name `org.glavo.scssfx` and is not a fat
JAR. The unclassified CLI JAR is a self-contained shaded application with the
automatic module name `org.glavo.scssfx.cli`. It contains relocated Jackson
and Picocli classes but no JavaFX, FFI, or native content.

No public artifact repository is configured yet. Do not treat the current
group and version as published Maven coordinates.

## Command Line

After `assemble`, run:

```shell
java -jar scssfx-cli/build/libs/scssfx-cli-0.1.0-SNAPSHOT.jar --help
```

The CLI accepts one `.scss` or `.sass` input:

```text
Usage: scssfx [OPTIONS] INPUT [OUTPUT]
```

| Option | Values and behavior |
| --- | --- |
| `--target` | `css`, `javafx-css`, or `bss`; defaults to `css` |
| `--style` | `expanded` or `compressed`; text targets only, defaults to `expanded` |
| `--javafx-target` | Integer from `8` through `27`; JavaFX targets only, defaults to `17` |
| `--javafx-compatibility` | Compatibility alias for `--javafx-target` |
| `-o`, `--output` | Writes to a file instead of standard output |
| `-h`, `--help` | Prints command help |
| `-V`, `--version` | Prints the development version |

Compile standard CSS:

```shell
java -jar scssfx-cli/build/libs/scssfx-cli-0.1.0-SNAPSHOT.jar \
  --style compressed \
  style.scss \
  -o style.css
```

Compile JavaFX 27 CSS:

```shell
java -jar scssfx-cli/build/libs/scssfx-cli-0.1.0-SNAPSHOT.jar \
  --target javafx-css \
  --javafx-target 27 \
  style.scss \
  -o style.css
```

Compile JavaFX 27 BSS:

```shell
java -jar scssfx-cli/build/libs/scssfx-cli-0.1.0-SNAPSHOT.jar \
  --target bss \
  --javafx-target 27 \
  style.scss \
  -o style.bss
```

Text output uses standard output when no destination is supplied. BSS always
requires a destination file. A positional destination may replace `-o`, but
the two forms cannot be combined. Destination parent directories are created
automatically.

The CLI currently has no stdin, watch, batch-directory, load-path, source-map,
or custom-resolver options. Its exit statuses are `0` for success, `1` for
compilation or I/O failure, and `2` for invalid usage.

## Java API

`SassCompiler` is stateless, thread-safe, and reusable. A textual target
returns `CompileResult<String>`:

```java
import org.glavo.scssfx.JavaFXCssTarget;
import org.glavo.scssfx.JavaFXTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;

import java.nio.file.Path;

var compiler = new SassCompiler();
var result = compiler.compile(
        SassSource.fromFile(Path.of("style.scss")),
        new JavaFXCssTarget(
                JavaFXTarget.JAVAFX27,
                OutputStyle.EXPANDED
        )
);

String css = result.output();
```

Standard CSS uses `CssTarget`. `JavaFXCssTarget` validates structures and
values against the selected JavaFX release without loading JavaFX classes.
Its default is JavaFX 17 with expanded output.

### BSS output

`BssTarget` returns a read-only `ByteBuffer` positioned at zero:

```java
import org.glavo.scssfx.BssTarget;
import org.glavo.scssfx.JavaFXTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;

import java.nio.file.Files;
import java.nio.file.Path;

var result = new SassCompiler().compile(
        SassSource.fromFile(Path.of("style.scss")),
        new BssTarget(JavaFXTarget.JAVAFX27)
);

var buffer = result.output().duplicate();
var bytes = new byte[buffer.remaining()];
buffer.get(bytes);
Files.write(Path.of("style.bss"), bytes);
```

The remaining bytes contain one complete BSS document. Source maps are not
available for BSS; requesting one fails with `SassCompilationException`.

### Compile options and source maps

`CompileOptions` configures CSS source maps, Sass load paths, and the optional
BSS retained-stylesheet resolver:

```java
import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.OutputStyle;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

var result = new SassCompiler().compile(
        SassSource.fromFile(Path.of("style.scss")),
        new CssTarget(OutputStyle.COMPRESSED, false),
        new CompileOptions(
                true,
                List.of(Path.of("styles"))
        )
);

String css = result.output();
String sourceMapJson = Objects.requireNonNull(result.sourceMap()).json();
```

The source map is returned separately as one version 3 JSON document. The
compiler does not write a map file or append a source-map URL automatically.

File sources infer `.scss`, `.sass`, or `.css` syntax. Other extensions require
an explicit `Syntax`. A string source may supply an absolute canonical URI so
that relative dependencies and diagnostics have a stable base.

### Retained JavaFX CSS imports

By default, BSS compilation resolves retained `@import` resources using exact
plain-CSS filenames. It first searches beside the containing file and then
uses `CompileOptions.loadPaths()`. It does not infer extensions, partials,
import-only files, or directory indexes, and it does not access the network.

`JavaFXStylesheetResolver` can explicitly provide other resource schemes:

```java
import org.glavo.scssfx.BssTarget;
import org.glavo.scssfx.CompileOptions;
import org.glavo.scssfx.JavaFXStylesheetResolver;
import org.glavo.scssfx.JavaFXTarget;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;

import java.net.URI;
import java.util.List;
import java.util.Map;

Map<URI, String> styles = Map.of(
        URI.create("memory:/theme.css"),
        "Pane { -fx-opacity: 0.75; }"
);

JavaFXStylesheetResolver resolver = (resource, baseUrl) -> {
    URI candidate = baseUrl == null
            ? URI.create(resource)
            : baseUrl.resolve(resource);
    if (!"memory".equals(candidate.getScheme())) {
        return null;
    }
    String content = styles.get(candidate);
    return content == null
            ? null
            : new JavaFXStylesheetResolver.ResolvedStylesheet(
                    candidate,
                    content
            );
};

var options = new CompileOptions(false, List.of(), resolver);
var source = SassSource.fromString(
        "@import url(\"memory:/theme.css\");",
        Syntax.SCSS,
        URI.create("memory:/entry.scss")
);
var result = new SassCompiler().compile(
        source,
        new BssTarget(JavaFXTarget.JAVAFX27),
        options
);
```

This resolver is only for retained plain-CSS imports during BSS compilation.
It is not a Sass `@use`, `@forward`, or dynamic `@import` importer. Returned
content is always parsed as plain CSS. A `null` result delegates to exact-file
lookup; an `IOException` reports a source-associated compilation failure.

The returned canonical URI must be absolute and stable. It supplies the base
for nested imports and the identity used by diagnostics, cycle detection, and
`CompileResult.loadedUrls()`. Resolution is synchronous and may be repeated.
A resolver shared by concurrent compilations must be thread-safe and is
responsible for decoding content and closing its own resources.

Textual JavaFX CSS output preserves retained imports and never calls this
resolver.

### Results and diagnostics

`CompileResult` contains:

- the typed output;
- a nullable source map;
- an immutable set of canonical loaded URLs;
- immutable non-error diagnostics in reporting order.

Root-source I/O failures throw `IOException`. Parse, evaluation, serialization,
and dependency-resolution failures throw `SassCompilationException`. Its
`primaryDiagnostic()` is the first error; `diagnostics()` also retains
non-error diagnostics emitted before failure, and `sassTrace()` runs from the
innermost active Sass member outward.

Diagnostic codes and source spans may be absent. Source locations use
zero-based UTF-16 line, column, and offset values, and spans are half-open.

## JavaFX target model

`JavaFXTarget` provides every release from `JAVAFX8` through `JAVAFX27`.
`JavaFXFeature` exposes the recorded platform capabilities for each release.
A platform feature may still be unsupported by a particular output backend.

| JavaFX target | Modeled changes | BSS |
| --- | --- | --- |
| 8 | Baseline font faces and unconditional imports | v5 |
| 9 | Public converter package names | v6 |
| 10–16 | No additional modeled change | v6 |
| 17 | No additional modeled change; extended blend modes unavailable | v6 |
| 18 | `add`, `red`, `green`, and `blue` blend modes | v6 |
| 19–22 | No additional modeled change | v6 |
| 23 | Textual transition properties | v6 |
| 24 | No additional modeled change | v6 |
| 25 | Preference media queries; one style rule per media block | v7 |
| 26 | Viewport/range media, multi-rule media, and advanced easing | v8 |
| 27 | Conditional imports and JavaFX platform media features | v9 |

The enum documentation records the detailed boundary for every individual
release.

## Current limitations

SCSSFX is still a development build. In particular:

- The BSS backend supports a validated subset of JavaFX CSS. Unsupported
  selectors, nodes, converter shapes, and values fail explicitly instead of
  producing a binary stylesheet that may not load.
- JavaFX-target selectors currently support type, universal, class, ID, and
  non-functional pseudo-class selectors with descendant and child
  combinators. Namespaces, attributes, siblings, functional pseudo-classes,
  and pseudo-elements are rejected.
- JavaFX targets reject native CSS nesting, `@supports`, and unknown at-rules.
  Font faces and retained imports must occur at the stylesheet root.
- JavaFX media conditions begin at the modeled JavaFX 25 boundary. CSS media
  types such as `screen` and `print` are not JavaFX media conditions.
- Textual JavaFX CSS supports transitions from JavaFX 23. The BSS backend
  rejects transition declarations for JavaFX 23 through 27 because those
  OpenJFX readers cannot restore the transition-specific converters.
- JavaFX 8 through 26 flatten unconditional retained imports. JavaFX 27 BSS v9
  preserves each direct imported body and condition. Imported font faces do
  not propagate to the parent stylesheet.
- The JavaFX property/value oracle matrix continues to expand. Passing the
  current fixed validation matrix is not a claim about every future JavaFX
  property, converter, or release.
- `OutputTarget` is sealed to the three built-in backends.

The current fixed sass-spec run passes 13,924 enabled fixtures with no failure;
five upstream fixtures remain disabled by their own compatibility metadata.
This result describes the pinned suite, not unspecified future Sass behavior.

## Compatibility verification

Sass language behavior is checked against pinned Dart Sass and sass-spec
snapshots. JavaFX behavior is verified using isolated runtime oracles for
JavaFX 8, 17, 18, 23, 25, 26, and 27. Oracle dependencies are test-only and
never enter product runtime variants.

See [UPSTREAM.md](UPSTREAM.md) for compatibility baselines and
[ORACLES.md](ORACLES.md) for the multi-JDK runtime verification procedure.

## License

SCSSFX is licensed under the [Mozilla Public License 2.0](LICENSE). Third-party
attribution and compatibility-source notices are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
