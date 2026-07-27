// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Reports checked compilation failure together with structured diagnostics.
@NotNullByDefault
public final class SassCompilationException extends Exception {
    /// The serialization version of this exception representation.
    private static final long serialVersionUID = 1L;

    /// The immutable, nonempty diagnostics associated with the failure, with
    /// the primary error first.
    private final @Unmodifiable List<Diagnostic> diagnostics;

    /// The immutable Sass call trace associated with the failure.
    private final @Unmodifiable List<SassStackFrame> sassTrace;

    /// The immutable canonical URLs loaded before the failure.
    private final @Unmodifiable Set<URI> loadedUrls;

    /// The immutable source text loaded before the failure, keyed by canonical
    /// URL.
    private final @Unmodifiable Map<URI, String> sourceContents;

    /// Creates a compilation exception without an underlying cause.
    ///
    /// A root-stylesheet trace frame is derived from the primary diagnostic
    /// when it has a source span.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    public SassCompilationException(List<? extends Diagnostic> diagnostics) {
        this(
                diagnostics,
                defaultTrace(diagnostics),
                Set.of(),
                Map.of(),
                null
        );
    }

    /// Creates a compilation exception with an optional underlying cause.
    ///
    /// A root-stylesheet trace frame is derived from the primary diagnostic
    /// when it has a source span.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            @Nullable Throwable cause
    ) {
        this(
                diagnostics,
                defaultTrace(diagnostics),
                Set.of(),
                Map.of(),
                cause
        );
    }

    /// Creates a compilation exception with loaded-source metadata.
    ///
    /// A root-stylesheet trace frame is derived from the primary diagnostic
    /// when it has a source span.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param loadedUrls canonical URLs loaded before the failure
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            Set<? extends URI> loadedUrls,
            @Nullable Throwable cause
    ) {
        this(
                diagnostics,
                defaultTrace(diagnostics),
                loadedUrls,
                Map.of(),
                cause
        );
    }

    /// Creates a compilation exception with complete loaded-source metadata.
    ///
    /// A root-stylesheet trace frame is derived from the primary diagnostic
    /// when it has a source span. The source-content map is snapshotted and
    /// does not cause any URL to be read.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param loadedUrls canonical URLs loaded before the failure
    /// @param sourceContents loaded source text keyed by canonical URL
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            Set<? extends URI> loadedUrls,
            Map<? extends URI, ? extends String> sourceContents,
            @Nullable Throwable cause
    ) {
        this(
                diagnostics,
                defaultTrace(diagnostics),
                loadedUrls,
                sourceContents,
                cause
        );
    }

    /// Creates a compilation exception with an explicit Sass call trace.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param sassTrace the Sass call trace from the failure site outward
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the diagnostic list is empty or its
    /// first diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            List<? extends SassStackFrame> sassTrace,
            @Nullable Throwable cause
    ) {
        this(diagnostics, sassTrace, Set.of(), Map.of(), cause);
    }

    /// Creates a compilation exception with an explicit trace and loaded URLs.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param sassTrace the Sass call trace from the failure site outward
    /// @param loadedUrls canonical URLs loaded before the failure
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the diagnostic list is empty or its
    /// first diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            List<? extends SassStackFrame> sassTrace,
            Set<? extends URI> loadedUrls,
            @Nullable Throwable cause
    ) {
        this(diagnostics, sassTrace, loadedUrls, Map.of(), cause);
    }

    /// Creates a compilation exception with an explicit trace and complete
    /// loaded-source metadata.
    ///
    /// The source-content map is snapshotted. It contains only text that the
    /// compiler already loaded during this compilation; callers need not and
    /// should not re-read its URLs to render diagnostics.
    ///
    /// @param diagnostics diagnostics whose first element is the primary error
    /// @param sassTrace the Sass call trace from the failure site outward
    /// @param loadedUrls canonical URLs loaded before the failure
    /// @param sourceContents loaded source text keyed by canonical URL
    /// @param cause the underlying cause, or {@code null} when none is available
    /// @throws IllegalArgumentException if the diagnostic list is empty or its
    /// first diagnostic is not an error
    public SassCompilationException(
            List<? extends Diagnostic> diagnostics,
            List<? extends SassStackFrame> sassTrace,
            Set<? extends URI> loadedUrls,
            Map<? extends URI, ? extends String> sourceContents,
            @Nullable Throwable cause
    ) {
        super(messageOf(diagnostics), cause);
        this.diagnostics = List.copyOf(diagnostics);
        this.sassTrace = List.copyOf(sassTrace);
        this.loadedUrls = Set.copyOf(loadedUrls);
        this.sourceContents = Map.copyOf(sourceContents);
    }

    /// Returns all diagnostics associated with this failure.
    ///
    /// The primary error is first. Any remaining diagnostics retain the order
    /// supplied when this exception was created.
    ///
    /// @return an immutable, nonempty list whose first element is the primary error
    public @Unmodifiable List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /// Returns the first diagnostic associated with this failure.
    ///
    /// @return the primary diagnostic
    public Diagnostic primaryDiagnostic() {
        return diagnostics.get(0);
    }

    /// Returns the Sass call trace associated with this failure.
    ///
    /// The first element identifies the innermost active Sass member.
    ///
    /// @return an immutable list ordered from the failure site outward
    public @Unmodifiable List<SassStackFrame> sassTrace() {
        return sassTrace;
    }

    /// Returns canonical URLs loaded before the compilation failed.
    ///
    /// The set includes the root URL when one was supplied and every dependency
    /// whose canonical identity was established before the failure.
    ///
    /// @return an immutable set of canonical loaded URLs
    public @Unmodifiable Set<URI> loadedUrls() {
        return loadedUrls;
    }

    /// Returns source text loaded before the compilation failed.
    ///
    /// Keys are canonical source URLs. URL-less root input is not represented;
    /// its contents remain available from the caller's original [SassSource].
    /// The returned map is an immutable snapshot and does not perform IO.
    ///
    /// @return immutable loaded source text keyed by canonical URL
    public @Unmodifiable Map<URI, String> sourceContents() {
        return sourceContents;
    }

    /// Returns the exception message derived from the first diagnostic.
    ///
    /// @param diagnostics the diagnostics to inspect
    /// @return the primary diagnostic message
    /// @throws IllegalArgumentException if {@code diagnostics} is empty
    private static String messageOf(List<? extends Diagnostic> diagnostics) {
        return primaryOf(diagnostics).message();
    }

    /// Returns the validated primary error diagnostic.
    ///
    /// @param diagnostics the diagnostics to inspect
    /// @return the first diagnostic
    /// @throws IllegalArgumentException if the list is empty or its first
    /// diagnostic is not an error
    private static Diagnostic primaryOf(List<? extends Diagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.isEmpty()) {
            throw new IllegalArgumentException("diagnostics must not be empty");
        }
        var primary = Objects.requireNonNull(diagnostics.get(0), "diagnostics[0]");
        if (primary.severity() != DiagnosticSeverity.ERROR) {
            throw new IllegalArgumentException(
                    "the first diagnostic must have ERROR severity"
            );
        }
        return primary;
    }

    /// Creates a root trace from the primary diagnostic when possible.
    ///
    /// @param diagnostics the diagnostics to inspect
    /// @return an empty trace or one root-stylesheet frame
    private static @Unmodifiable List<SassStackFrame> defaultTrace(
            List<? extends Diagnostic> diagnostics
    ) {
        @Nullable SourceSpan span = primaryOf(diagnostics).span();
        return span == null
                ? List.of()
                : List.of(new SassStackFrame("root stylesheet", span));
    }
}
