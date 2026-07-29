# SassFX

SassFX is a Java 17 implementation of Sass. It compiles SCSS, indented Sass,
and plain CSS to standard CSS, validated JavaFX CSS, or JavaFX binary
stylesheets (BSS).

The compiler does not load JavaFX, invoke Dart Sass or Node.js, use FFI, or
ship native libraries. Local builds use `0.1.0-SNAPSHOT` unless
`-PsassfxVersion=<version>` or `SASSFX_VERSION` supplies a release version.

## Highlights

- Pure Java 17 compiler and serializers.
- SCSS, indented Sass, and plain-CSS library inputs.
- Standard CSS, JavaFX CSS, and BSS output targets.
- Configurable `JavaFXTarget` values from JavaFX 8 through JavaFX 27.
- BSS versions 5 through 9 without a JavaFX runtime dependency.
- Explicit resolver API for retained JavaFX CSS imports from application
  resource schemes.
- Structured diagnostics, configurable logging and deprecation processing,
  loaded-URL metadata, and CSS source maps.
- Cacheable Gradle task for CSS, JavaFX CSS, and BSS generation.
- Embedded Sass Protocol 3.2.0 framing, version negotiation, concurrent
  compilation dispatch, diagnostics, and compile responses.
- Reproducible conformance checks against pinned Dart Sass, sass-spec, and
  OpenJFX releases.

The core runtime uses Gson. The embedded endpoint additionally uses
the generated Sass Embedded Protocol messages and Protocol Buffers Java. The
command-line application uses Picocli and includes the endpoint. All runtime
dependencies are pure Java.

## Building

Use the checked-in Gradle Wrapper:

```shell
./gradlew assemble
./gradlew check
./gradlew verifyPublishedConsumer
```

On Windows:

```powershell
.\gradlew.ps1 assemble
.\gradlew.ps1 check
.\gradlew.ps1 verifyPublishedConsumer
```

Product code and artifacts target Java 17. `check` runs the module tests, the
pinned sass-spec corpus and project-owned fixtures, shaded-application smoke
tests, artifact boundary checks, and source-isolation checks. Generating
Javadoc additionally requires a JDK 25 toolchain because the source uses
Markdown-style `///` documentation comments.

The build produces:

| Project | Purpose | Main artifact |
| --- | --- | --- |
| `sassfx-core` | Reusable compiler API | `sassfx-core/build/libs/sassfx-core-0.1.0-SNAPSHOT.jar` |
| `sassfx-embedded` | Embedded Sass Protocol executable | `sassfx-embedded/build/libs/sassfx-embedded-0.1.0-SNAPSHOT.jar` |
| `sassfx-cli` | Executable command-line frontend | `sassfx-cli/build/libs/sassfx-cli-0.1.0-SNAPSHOT.jar` |
| `sassfx-gradle-plugin` | Gradle build integration | `sassfx-gradle-plugin/build/libs/sassfx-gradle-plugin-0.1.0-SNAPSHOT.jar` |

The core JAR has the automatic module name `org.glavo.sassfx` and is not a fat
JAR. The unclassified embedded, CLI, and Gradle plugin JARs are self-contained
shaded artifacts with automatic module names `org.glavo.sassfx.embedded`,
`org.glavo.sassfx.cli`, and `org.glavo.sassfx.gradle`. They contain relocated
runtime dependencies but no JavaFX, FFI, or native content.

All four projects are configured for signed Maven Central publication with
sources, Javadoc, license, developer, and SCM metadata. CLI, Embedded, and the
Gradle plugin use their self-contained shaded JARs as the main artifacts. The
Gradle plugin is also configured for Plugin Portal publication.
`verifyPublishedConsumer` stages every publication in an isolated repository,
resolves the plugin and library from a consumer build, compiles Java and SCSS,
and runs both executable artifacts. Release tags use the version derived from
the tag; configuring publication does not imply that a particular version has
already been released.

## Gradle Plugin

The `org.glavo.sassfx` plugin registers a cacheable `compileScss` task. Its
defaults compile `.scss`, `.sass`, and `.css` files under `src/main/scss` to
`build/generated/sassfx/main`. Basenames beginning with `_` are tracked as
inputs but are treated as partials and do not receive their own output.

```kotlin
plugins {
    java
    id("org.glavo.sassfx") version "<version>"
}

sassfx {
    target.set("css/javafx@21")
    style.set("compressed")
    charset.set(true)
    loadPaths.from(layout.projectDirectory.dir("src/shared/scss"))
}
```

The target accepts only `css`, `css/javafx@8` through `css/javafx@27`, or
`bss/javafx@8` through `bss/javafx@27`. Text targets produce `.css`; BSS
targets produce `.bss`. Source-relative directory structure is preserved.
`sourceDirectory` and `outputDirectory` may be replaced through the extension.

When the Java plugin is present, `processResources` automatically includes the
generated tree and depends on `compileScss`. Additional independent
compilations may use the public task type:

```kotlin
tasks.register<org.glavo.sassfx.gradle.SassFXCompile>("compileThemeBss") {
    sourceDirectory.set(layout.projectDirectory.dir("src/theme/scss"))
    outputDirectory.set(layout.buildDirectory.dir("generated/sassfx/theme"))
    target.set("bss/javafx@27")
}
```

The task tracks the source and load-path trees, supports the Gradle build and
configuration caches, removes stale files, detects output-path collisions,
and replaces the output tree only after every entrypoint compiles
successfully. Release versions are published through the Gradle Plugin Portal
workflow.

## Command Line

After `assemble`, run:

```shell
java -jar sassfx-cli/build/libs/sassfx-cli-0.1.0-SNAPSHOT.jar --help
```

The CLI accepts file, standard-input, mapped-file, and recursive-directory
compilations:

```text
Usage: sassfx [OPTIONS] INPUT [OUTPUT]
       sassfx [OPTIONS] --stdin [OUTPUT]
       sassfx [OPTIONS] INPUT:OUTPUT...
       sassfx [OPTIONS] DIR[:OUTPUT_DIR]...
       sassfx [OPTIONS] --interactive
       sassfx --embedded
```

| Option | Values and behavior |
| --- | --- |
| `--target` | `css`, `css/javafx@8` through `css/javafx@27`, or `bss/javafx@8` through `bss/javafx@27`; defaults to `css` |
| `-s`, `--style` | `expanded` or `compressed`; text targets only, defaults to `expanded` |
| `--[no-]charset` | Emits `@charset` for expanded non-ASCII output or a UTF-8 BOM for compressed output; enabled by default |
| `--[no-]source-map` | Generates source maps for file output; enabled by default |
| `--source-map-urls` | Uses `relative` or `absolute` source URLs; defaults to `relative` |
| `--[no-]embed-sources` | Includes original source text in generated maps |
| `--[no-]embed-source-map` | Embeds the map as a UTF-8 JSON data URL |
| `--[no-]error-css` | Controls browser-readable error stylesheets; defaults on for file output and off for stdout |
| `--[no-]stop-on-error` | Stops a multi-input invocation after its first Sass or IO failure |
| `--update` | Compiles only mapped destinations older than their dependencies |
| `-w`, `--watch` | Recompiles affected mapped entrypoints after filesystem changes |
| `--[no-]poll` | Selects metadata polling or native notifications for watch mode |
| `-i`, `--interactive` | Runs a persistent line-oriented SassScript shell |
| `--embedded` | Serves the binary Embedded Sass Protocol over stdin and stdout |
| `--[no-]stdin` | Reads the root stylesheet from standard input |
| `--[no-]indented` | Parses root inputs using the indented Sass syntax |
| `-I`, `--load-path` | Adds a Sass load path; may be repeated |
| `-p`, `--pkg-importer` | Enables the built-in `node` importer for `pkg:` URLs; may be repeated |
| `-q`, `--[no-]quiet` | Suppresses warnings, deprecations, and debug messages |
| `--[no-]quiet-deps` | Suppresses compiler warnings from load-path dependencies |
| `--[no-]verbose` | Prints every repeated deprecation warning |
| `--fatal-deprecation` | Promotes an ID, or every ID active by a Sass version, to an error |
| `--silence-deprecation` | Suppresses a deprecation ID |
| `--future-deprecation` | Explicitly enables a future deprecation ID |
| `-c`, `--[no-]color` | Controls ANSI styling for messages; defaults on when stdout has an attached console |
| `--[no-]unicode` | Controls Unicode diagnostic frame glyphs; enabled by default |
| `--[no-]trace` | Appends Java implementation stack traces to Sass and IO failures |
| `-o`, `--output` | Writes to a file instead of standard output |
| `-h`, `--help` | Prints command help |
| `-V`, `--version` | Prints the embedded implementation version |

Compile standard CSS:

```shell
java -jar sassfx-cli/build/libs/sassfx-cli-0.1.0-SNAPSHOT.jar \
  --style compressed \
  style.scss \
  -o style.css
```

Compile JavaFX 27 CSS:

```shell
java -jar sassfx-cli/build/libs/sassfx-cli-0.1.0-SNAPSHOT.jar \
  --target css/javafx@27 \
  style.scss \
  -o style.css
```

Compile JavaFX 27 BSS:

```shell
java -jar sassfx-cli/build/libs/sassfx-cli-0.1.0-SNAPSHOT.jar \
  --target bss/javafx@27 \
  style.scss \
  -o style.bss
```

Explicit file inputs use indented Sass for lowercase `.sass`, plain CSS for
lowercase `.css`, and SCSS for every other extension. The magic input `-` is
equivalent to stdin; stdin-relative loads resolve from the process working
directory.

`SASS_PATH` adds filesystem load paths separated by the platform path
separator (`;` on Windows and `:` elsewhere). Explicit `-I`/`--load-path`
entries take precedence, followed by `SASS_PATH` entries in declaration order.
Relative paths and empty entries resolve from the process working directory.
The same resolution order applies to ordinary compilation, update/watch mode,
and the interactive shell; `--quiet-deps` classifies environment-loaded
stylesheets as load-path dependencies.

Multiple roots use `INPUT:OUTPUT` mappings. A mapped directory is traversed
recursively while preserving its relative tree; lowercase `.scss`, `.sass`,
and `.css` entrypoints are compiled, while basenames beginning with `_` and
other extensions are ignored. A standalone directory compiles Sass
entrypoints in place and leaves existing CSS files unchanged. Windows drive
colons are not treated as mapping separators.

Text output uses standard output only for one root without a destination. BSS
always requires a destination file and directory-derived BSS destinations use
the `.bss` extension. A positional destination may replace `-o`, but `-o`
cannot be combined with mappings or directory batches. Destination parent
directories are created automatically. Batch compilation continues after an
expected Sass or IO failure unless `--stop-on-error` is selected.

Use `--update` with file or directory mappings to compile only destinations
that are missing or older than their root or any transitive file dependency.
Fresh destinations are left byte-for-byte unchanged and produce no status
line, including fresh siblings of an entrypoint that must be rebuilt. A shared
dependency rebuilds every affected destination. Failed updates apply the same
error-CSS or output-deletion policy as immediate compilation. The magic stdin
mapping `-:OUTPUT` is always compiled. Explicit `--stdin` and stdout
destinations are rejected in update mode.

Use `--watch` or `-w` to perform the same initial freshness check and then
continue recompiling affected entrypoints. Added directory entrypoints are
discovered recursively; removing an entrypoint deletes its exact output and
source-map sidecar. Native recursive filesystem notifications are used by
default, while `--poll` selects pure-Java metadata polling. `--poll` and
`--no-poll` are valid only with `--watch`. The watch-ready banner and deletion
messages remain visible under `--quiet`; successful compilation status lines
do not. Filesystem importer candidate paths are retained as an incremental
resolution graph, so missing dependencies, candidate conflicts, load-path
fallbacks, and precedence changes recover without recompiling entrypoints that
cannot be affected. Dependency tracking covers `@import`, `@use`, `@forward`,
and `meta.load-css()`. Root replacement and module-loop introduction or
removal are recoverable; unrelated files do not trigger recompilation.
`--stop-on-error` terminates watch mode after the first failed batch.

Use `--interactive` or `-i` to evaluate one SassScript expression, variable
declaration, or `@use` rule per physical input line. Variables, module
namespaces, module caches, and deprecation state persist until stdin closes.
Each input line is echoed with the `>> ` prompt; expressions and declarations
print their inspected value, while `@use` and blank lines produce no value.
Errors are reported to stdout and do not terminate the session. Warnings,
deprecations, and debug messages use stderr and honor the normal diagnostic
options. Relative modules resolve from the process working directory, and
load paths, the Node package importer, color, Unicode, trace, quiet, and
deprecation controls remain available.
Interactive `@use` supports built-in modules, `as *`, and `with (...)`
configuration with the same persistent namespace and module-cache semantics as
file compilation.

Textual file output creates a compact `<output>.map` sidecar by default and
appends the corresponding source-map comment. `--no-source-map` disables new
map output without deleting an existing sidecar. Embedded maps use percent-
encoded UTF-8 JSON. Source contents may be embedded independently, and stdin
is represented by a UTF-8 contents data URL. Source-map options that cannot
produce a usable stdout result are rejected as usage errors. BSS does not
support source maps or charset markers.

On a Sass failure, textual file output is replaced with a browser-readable
error stylesheet by default. Stdout remains empty unless `--error-css` is
explicitly selected. `--no-error-css` removes an existing failed textual
destination but leaves an existing map sidecar unchanged. Error CSS is not
available for BSS. Error stylesheets use an ASCII diagnostic frame in their
leading comment, independently serialize the displayed diagnostic with Dart
Sass's quote and escape rules, and leave an existing map sidecar untouched
until a later successful compilation replaces it.

Diagnostics use complete UTF-8 source lines for file and stdin roots. Unicode
frames are enabled by default; `--no-unicode` selects the equivalent ASCII
gutter. Buffered and redirected output is uncolored by default, while
`--color` and `--no-color` provide deterministic overrides. `--trace` appends
Java implementation frames after ordinary Sass and IO errors without changing
their exit status. Unexpected implementation failures always include a trace
and exit with status `255`. Error CSS never contains ANSI escapes. Named colors
interpolated into selectors, declaration names, media queries, at-root
queries, extend targets, or unknown at-rule values produce Dart
Sass-compatible compiler warnings; ordinary SassScript strings and declaration
values do not.

Legacy color channel readers and the deprecated `adjust-hue`, `lighten`,
`darken`, two-argument `saturate`, `desaturate`, `opacify`, `fade-in`,
`transparentize`, and `fade-out` functions report Dart Sass-compatible
`color-functions` diagnostics with migration suggestions. Plain-CSS filter
forms such as one-argument `saturate()` remain unaffected.

Deprecated global aliases report their exact replacement in `sass:color`,
`sass:list`, `sass:map`, `sass:math`, `sass:meta`, `sass:selector`, or
`sass:string`. This metadata remains attached to first-class global function
references and is removed from module exports. Ambiguous color-filter
functions report only when their arguments select the Sass implementation;
percentage `abs()` uses its dedicated `abs-percent` diagnostic.

Pass `--pkg-importer=node` to resolve `pkg:` URLs with Node package lookup.
The importer starts beside the containing file, or at the process working
directory for stdin and other URL-less roots, and searches each ancestor's
`node_modules` directory. It honors Sass package `exports`, `sass`, and `style`
metadata without executing JavaScript or package scripts.

The CLI does not expose application-defined importer and function callbacks;
those are Java API features. Its exit statuses are `0` for success, `64` for
invalid usage, `65` for Sass data errors, `66` for IO failures, and `255` for
unexpected implementation failures. Interactive line failures are recoverable
and the shell exits with status `0` when stdin closes. Invalid-usage
diagnostics and the following usage text are written to standard output, while
Sass compilation and IO failures are written to standard error.

## Embedded Sass Protocol

The standalone endpoint serves Embedded Sass Protocol 3.2.0 over stdin and
stdout:

```shell
java -jar sassfx-embedded/build/libs/sassfx-embedded-0.1.0-SNAPSHOT.jar
```

The shaded CLI exposes the same endpoint:

```shell
java -jar sassfx-cli/build/libs/sassfx-cli-0.1.0-SNAPSHOT.jar --embedded
```

Pass `--version` to either form to print a JSON version document instead of
opening the binary protocol stream. The endpoint accepts length-delimited
protobuf messages, reserves compilation ID zero for version negotiation, and
may process distinct nonzero compilation IDs concurrently. Closing stdin
cancels all in-flight work. Protocol violations emit a fatal protocol error
and exit with status `76`; unexpected endpoint failures use status `70`.

String and path inputs support load paths, Node package imports, output style,
charset handling, source maps, loaded URLs, logs, and deprecation settings.
Contents importers, file importers, global functions, host function values,
and compiler-owned function and mixin values use synchronous callbacks routed
to the compilation that requested them.

The value codec preserves numbers and units, strings, booleans, null, lists,
maps, argument lists, colors, calculations, and opaque callable identities.
It also preserves protocol metadata that CSS cannot represent, including
string quoting, list separators and brackets, map order, compound unit order,
missing color channels, and request-local argument-list identities. Invalid
values, callback IDs, result variants, and signatures follow the protocol's
documented boundary between compilation failures and fatal connection errors.

Contents-importer non-canonical schemes use Dart Sass's lowercase scheme
grammar, control containing-URL propagation, and may not be returned as
canonical results. Invalid descriptor placement is a fatal protocol parameter
error, while invalid scheme text is a compilation failure. Contents and file
callback results preserve declared importer order, validate absolute and
`file:` URL requirements with protocol-compatible failures, propagate null and
host errors without closing the connection, and give the importer that loaded
a stylesheet first chance to resolve its relative dependencies.
Source-map contents are captured from the sources actually loaded for the
compilation and never recovered by dereferencing importer display URLs.
`source_map_include_sources`, `alert_color`, and `alert_ascii` control embedded
source text and terminal-oriented diagnostic presentation independently of
the structured diagnostic fields.

## Java API

`SassCompiler` is stateless, thread-safe, and reusable. A textual target
returns `CompileResult<String>`:

```java
import org.glavo.sassfx.JavaFXCssTarget;
import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;

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
import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;

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

`CompileOptions` configures CSS source maps, ordered custom Sass importers and
functions, Sass load paths, and the optional BSS retained-stylesheet resolver:

```java
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.OutputStyle;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;

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
an explicit `Syntax`. A string source may supply a canonical URI so that
relative dependencies and diagnostics have a stable base. Relative URIs remain
accepted for Dart Sass 1.x compatibility but report
`compile-string-relative-url`; callers should use an absolute URI.

### Custom Sass importers

`SassImporter` provides the synchronous `canonicalize`/`load` contract used by
`@use`, `@forward`, dynamic `@import`, and `meta.load-css()`. Custom importers
are consulted in `CompileOptions.importers()` order before filesystem load
paths:

```java
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.SassCanonicalizeContext;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassImporterResult;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;

Map<URI, SassImporterResult> sources = Map.of(
        URI.create("memory:///theme.scss"),
        new SassImporterResult("$accent: royalblue;", Syntax.SCSS)
);

SassImporter importer = new SassImporter() {
    @Override
    public @Nullable URI canonicalize(
            URI url,
            SassCanonicalizeContext context
    ) {
        if (url.equals(URI.create("theme"))) {
            return URI.create("memory:///theme.scss");
        }
        return sources.containsKey(url) ? url : null;
    }

    @Override
    public @Nullable SassImporterResult load(URI canonicalUrl) {
        return sources.get(canonicalUrl);
    }
};

var options = new CompileOptions(
        false,
        List.of(),
        null,
        List.of(importer)
);
```

A canonical URL must be absolute and stable. It is the identity used for
module caching, cycle detection, relative-load ownership, and
`CompileResult.loadedUrls()`. Relative results remain loadable for Dart Sass
1.x compatibility but report `relative-canonical` before `load()` is invoked.
Returning `null` from `canonicalize` delegates to the next importer; returning
`null` from `load` is a terminal not-found result.
`SassCanonicalizeContext.fromImport()` distinguishes legacy `@import`, and
`containingUrl()` supplies the canonical containing URL when applicable.

After containing-file lookup, custom importers, and explicit load paths decline
a relative request, the compiler retains Dart Sass's compatibility fallback
through the process current working directory. A successful fallback reports
`fs-importer-cwd`. Add `Path.of(".")` explicitly to
`CompileOptions.loadPaths()` to preserve that search behavior without the
deprecation.

`SassImporterResult.sourceMapUrl()` may provide a separate absolute URL for
source maps. When omitted, the compiler generates a UTF-8 `data:` URL from the
returned contents. Importer callbacks are synchronous, may be repeated, and
may run concurrently when one options instance is shared across compilations.

`SassFileImporter` is the filesystem-backed variant. Its `findFileUrl` callback
maps a Sass URL to an absolute `file:` URL, while the compiler performs standard
extension, partial, import-only, and directory-index resolution and reads the
selected file:

```java
import org.glavo.sassfx.SassFileImporter;

import java.nio.file.Path;

Path packages = Path.of("packages").toAbsolutePath();
SassFileImporter packageFiles = (url, context) -> {
    if (!"pkg".equals(url.getScheme())) {
        return null;
    }
    return packages.resolve(url.getSchemeSpecificPart()).toUri();
};
```

File importers and contents importers share the same ordered
`CompileOptions.importers()` list. Absolute `file:` requests bypass
`findFileUrl`; a returned file URL that has no matching Sass candidate declines
the request and allows the next importer to run.

`SassNodePackageImporter` provides the built-in `pkg:` implementation without
a Node.js runtime:

```java
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.SassNodePackageImporter;

import java.nio.file.Path;
import java.util.List;

var options = new CompileOptions(
        false,
        List.of(),
        null,
        List.of(new SassNodePackageImporter(Path.of(".")))
);
```

For each request, lookup begins beside its containing file when that file has a
canonical `file:` URL; otherwise it begins at the configured entry-point
directory. The closest ancestor containing
`node_modules/<package>/package.json` owns the request. Package `exports` take
precedence over root `sass`, `style`, and `index` entry points and over
filesystem subpaths. Export conditions are evaluated in manifest order for
`sass`, `style`, and `default`; exact and wildcard subpaths, arrays, partials,
directory indexes, and legacy import-only files are supported.

The importer reads UTF-8 manifests and stylesheets directly from the local
filesystem. It does not execute JavaScript, resolve Node `main` entries, run
package scripts, access the network, or consult Sass load paths. Instances are
immutable and safe to share across concurrent compilations.

### Custom Sass functions

`SassCustomFunction` registers a synchronous Java callback with a complete Sass
signature. Sass binds positional and keyword arguments, evaluates defaults, and
passes values to the callback in declaration order:

```java
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.SassCustomFunction;
import org.glavo.sassfx.SassValue;

import java.util.List;

var pow = new SassCustomFunction(
        "java-pow($base, $exponent: 2)",
        arguments -> SassValue.number(Math.pow(
                arguments.get(0).numberValue(),
                arguments.get(1).numberValue()
        ))
);

var options = new CompileOptions(
        false,
        List.of(),
        null,
        List.of(),
        List.of(pow)
);
```

A rest parameter is passed as the final `SassValueType.ARGUMENT_LIST` value.
Callbacks that accept leftover keywords must call `SassValue.keywords()` to
mark them consumed. Custom functions are visible from loaded Sass modules and
through `sass:meta`; stylesheet functions and built-in calculation behavior
retain Sass precedence. Plain CSS sources do not invoke custom functions.

`SassValue` preserves every evaluator value kind without converting through
CSS text. It provides factories and typed accessors for common scalar, list,
map, and Color Level 4 values. `SassColorSpace` exposes all 16 public Sass
spaces; `SassValue.color(...)` preserves missing channels and requires an
explicit space. Requiring the space avoids the ambiguous legacy constructor
forms covered by Dart Sass's `null-alpha` and `color-4-api` deprecations, so
those deprecated overloads are intentionally absent from the Java API.
Opaque calculations, functions, and mixins may be returned directly. A
callback must return a non-null value. Thrown exceptions become
source-associated compilation failures and remain in the cause chain. One
callback instance may run concurrently when compile options are shared, so
callback implementations must be thread-safe.

### Logging and deprecation controls

`SassDiagnosticOptions` controls event delivery and deprecation policy for one
compilation. Retained events remain available in `CompileResult.diagnostics()`;
the logger receives the same processed warnings, deprecations, and debug
messages synchronously:

```java
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.SassDeprecation;
import org.glavo.sassfx.SassDiagnosticOptions;

import java.util.List;
import java.util.Set;

var diagnostics = new SassDiagnosticOptions(
        event -> System.err.println(event.diagnostic().message()),
        true,
        false,
        Set.of(SassDeprecation.SLASH_DIV),
        Set.of(),
        Set.of()
);

var options = new CompileOptions(
        false,
        List.of(),
        null,
        List.of(),
        List.of(),
        diagnostics
);
```

`quietDeps` applies to compiler warnings emitted by custom-importer and
load-path dependencies, including transitively loaded modules. It does not
suppress explicit Sass `@warn` or `@debug` statements. By default, each
deprecation type is reported at most five times and a successful compilation
adds an omission summary; `verbose` disables that limit. Fatal deprecations
take precedence over silencing, while dependency suppression takes precedence
over fatal processing. Evaluation-time deprecations use the same processing
pipeline as parser deprecations. This includes legacy units accepted by list
indexes, `math.random()`, HSL/HWB constructors, color weights, color channel
updates, alpha updates, and hue adjustment. The `sass:color` plain-CSS
compatibility fallbacks for numeric `invert()`, `grayscale()`, and `opacity()`,
plus Microsoft-filter `alpha()` calls, report the `color-module-compat`
category while preserving their CSS output. Parser and selector processing
also reports deprecated Mozilla document rules, private module configuration,
arguments placed after rest expansion, adjacent compounds, and bogus
combinators across style rules, `@extend`, and selector operations.
Importer and compile API processing reports relative string URLs, relative
canonical importer results, and successful implicit current-working-directory
loads at their Dart Sass trigger points.

`SassDeprecation` is the typed Dart Sass 1.102.0 deprecation registry. It
exposes the command-line ID, activation and obsolescence versions, status, and
description for each entry. Logger callbacks may run concurrently when compile
options are shared and must be thread-safe. A runtime exception thrown by a
logger aborts compilation and propagates unchanged.

The CLI applies these controls consistently to immediate compilation, watch
recompilation, and the interactive shell. Non-fatal warnings about conflicting
or unnecessary deprecation options are printed once per CLI invocation.

### Retained JavaFX CSS imports

By default, BSS compilation resolves retained `@import` resources using exact
plain-CSS filenames. It first searches beside the containing file and then
uses `CompileOptions.loadPaths()`. It does not infer extensions, partials,
import-only files, or directory indexes, and it does not access the network.

`JavaFXStylesheetResolver` can explicitly provide other resource schemes:

```java
import org.glavo.sassfx.BssTarget;
import org.glavo.sassfx.CompileOptions;
import org.glavo.sassfx.JavaFXStylesheetResolver;
import org.glavo.sassfx.JavaFXTarget;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;

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
innermost active Sass member outward. `sourceContents()` is an immutable
snapshot of URL-addressable root and dependency text loaded before failure, so
diagnostic consumers can render the original source without re-reading a file
or invoking an importer again.

Diagnostic codes and source spans may be absent. Source locations use
zero-based UTF-16 line, column, and offset values, and spans are half-open.

## JavaFX target model

`JavaFXTarget` provides every release from `JAVAFX8` through `JAVAFX27`.
`JavaFXFeature` exposes the recorded platform capabilities for each release.
A platform feature may still be unsupported by a particular output backend.

| JavaFX target | Modeled changes | BSS |
| --- | --- | --- |
| 8 | Baseline font faces, unconditional imports, and legacy gradients | v5 |
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

## Limitations

The current release has the following boundaries:

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
- The pinned JavaFX 8–27 property dispatch and every non-transition converter
  family have executable CSS/BSS coverage. Features introduced after JavaFX
  27 are outside this release's target range.
- `OutputTarget` is sealed to the three built-in backends.

The current fixed sass-spec run passes 13,926 enabled fixtures with no failure;
five upstream fixtures remain disabled by their own compatibility metadata.
This result describes the pinned suite, not unspecified future Sass behavior.

## Verification

Sass language behavior is checked against pinned Dart Sass and sass-spec
snapshots. JavaFX behavior is verified using isolated runtime oracles for
JavaFX 8, 17, 18, 23, 25, 26, and 27. Oracle dependencies are test-only and
never enter product runtime variants.

See [UPSTREAM.md](UPSTREAM.md) for compatibility baselines and
[ORACLES.md](ORACLES.md) for the multi-JDK runtime verification procedure.

## License

SassFX is licensed under the [Mozilla Public License 2.0](LICENSE). Third-party
attribution and compatibility-source notices are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
