// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/// Collects stable execution and coverage statistics for one fixture manifest.
///
/// Counts are split by corpus ownership (upstream pin vs owned curated) and by
/// assertion kind (CSS output vs diagnostic) so a single compatibility ratio
/// cannot hide structural risk.
@NotNullByDefault
final class SassSpecSummary {
    /// Creates JSON reports for completed fixture runs.
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /// Counts all manifest-selected fixtures.
    private final int total;

    /// Counts fixtures selected for execution rather than an explicit skip.
    private final int enabled;

    /// Holds planned skip counts by sorted compatibility category.
    private final @Unmodifiable Map<String, Integer> plannedSkippedByCategory;

    /// Counts successfully completed executable fixtures.
    private final AtomicInteger passed = new AtomicInteger();

    /// Counts executable fixtures that failed an assertion or compilation expectation.
    private final AtomicInteger failed = new AtomicInteger();

    /// Counts fixtures aborted through their explicit skip declaration.
    private final AtomicInteger skipped = new AtomicInteger();

    /// Aggregates actual aborted fixtures by category without relying on dynamic-test order.
    private final ConcurrentMap<String, AtomicInteger> skippedByCategory = new ConcurrentHashMap<>();

    /// Counts upstream executable fixtures.
    private final AtomicInteger upstreamTotal = new AtomicInteger();

    /// Counts passed upstream executable fixtures.
    private final AtomicInteger upstreamPassed = new AtomicInteger();

    /// Counts failed upstream executable fixtures.
    private final AtomicInteger upstreamFailed = new AtomicInteger();

    /// Counts owned (non-upstream) executable fixtures.
    private final AtomicInteger ownedTotal = new AtomicInteger();

    /// Counts passed owned executable fixtures.
    private final AtomicInteger ownedPassed = new AtomicInteger();

    /// Counts failed owned executable fixtures.
    private final AtomicInteger ownedFailed = new AtomicInteger();

    /// Counts output-CSS assertion passes.
    private final AtomicInteger outputPassed = new AtomicInteger();

    /// Counts output-CSS assertion failures.
    private final AtomicInteger outputFailed = new AtomicInteger();

    /// Counts diagnostic assertion passes.
    private final AtomicInteger diagnosticPassed = new AtomicInteger();

    /// Counts diagnostic assertion failures.
    private final AtomicInteger diagnosticFailed = new AtomicInteger();

    /// Creates a summary with a fixed planned fixture population.
    ///
    /// @param fixtures the manifest-selected fixture cases
    SassSpecSummary(List<? extends SassSpecManifest.Case> fixtures) {
        Objects.requireNonNull(fixtures, "fixtures");
        var plannedSkips = new TreeMap<String, Integer>();
        int executable = 0;
        for (SassSpecManifest.Case fixture : fixtures) {
            Objects.requireNonNull(fixture, "fixture");
            if (fixture.action() == SassSpecManifest.Action.RUN) {
                executable++;
            } else {
                plannedSkips.merge(fixture.category(), 1, Integer::sum);
            }
        }

        total = fixtures.size();
        enabled = executable;
        plannedSkippedByCategory = Collections.unmodifiableMap(new TreeMap<>(plannedSkips));
    }

    /// Records one successfully completed executable fixture.
    ///
    /// @param upstream       whether the fixture is from the upstream pin corpus
    /// @param outputAssertion whether the fixture asserted CSS output (vs diagnostic)
    void recordPassed(boolean upstream, boolean outputAssertion) {
        passed.incrementAndGet();
        if (upstream) {
            upstreamTotal.incrementAndGet();
            upstreamPassed.incrementAndGet();
        } else {
            ownedTotal.incrementAndGet();
            ownedPassed.incrementAndGet();
        }
        if (outputAssertion) {
            outputPassed.incrementAndGet();
        } else {
            diagnosticPassed.incrementAndGet();
        }
    }

    /// Records one successfully completed executable fixture without corpus detail.
    void recordPassed() {
        recordPassed(true, true);
    }

    /// Records one failed executable fixture.
    ///
    /// @param upstream       whether the fixture is from the upstream pin corpus
    /// @param outputAssertion whether the failure was an output mismatch
    void recordFailed(boolean upstream, boolean outputAssertion) {
        failed.incrementAndGet();
        if (upstream) {
            upstreamTotal.incrementAndGet();
            upstreamFailed.incrementAndGet();
        } else {
            ownedTotal.incrementAndGet();
            ownedFailed.incrementAndGet();
        }
        if (outputAssertion) {
            outputFailed.incrementAndGet();
        } else {
            diagnosticFailed.incrementAndGet();
        }
    }

    /// Records one failed executable fixture without corpus detail.
    void recordFailed() {
        recordFailed(true, true);
    }

    /// Records one explicitly skipped fixture and its compatibility category.
    ///
    /// @param category the manifest-declared skip category
    void recordSkipped(String category) {
        Objects.requireNonNull(category, "category");
        skipped.incrementAndGet();
        skippedByCategory.computeIfAbsent(category, ignored -> new AtomicInteger()).incrementAndGet();
    }

    /// Returns a stable line that describes the fixture plan before execution.
    ///
    /// @return the planned coverage summary
    String plannedLine() {
        return "sass-spec plan: total=" + total +
                " enabled=" + enabled +
                " skipped=" + (total - enabled) +
                " coverage=" + percentage(enabled, total) +
                " skippedByCategory=" + plannedSkippedByCategory;
    }

    /// Returns a stable line that describes completed fixture execution.
    ///
    /// @return the completed compatibility summary
    String completedLine() {
        int passedCount = passed.get();
        int failedCount = failed.get();
        return "sass-spec result: total=" + total +
                " enabled=" + enabled +
                " passed=" + passedCount +
                " failed=" + failedCount +
                " skipped=" + skipped.get() +
                " compatibility=" + percentage(passedCount, passedCount + failedCount) +
                " upstreamCompat=" + percentage(upstreamPassed.get(), upstreamPassed.get() + upstreamFailed.get()) +
                " ownedCompat=" + percentage(ownedPassed.get(), ownedPassed.get() + ownedFailed.get()) +
                " outputCompat=" + percentage(outputPassed.get(), outputPassed.get() + outputFailed.get()) +
                " diagnosticCompat=" + percentage(
                        diagnosticPassed.get(),
                        diagnosticPassed.get() + diagnosticFailed.get()
                ) +
                " coverage=" + percentage(enabled, total) +
                " skippedByCategory=" + actualSkippedByCategory();
    }

    /// Writes the completed summary as a deterministic JSON report.
    ///
    /// @param reportDirectory the directory that will contain {@code summary.json}
    /// @throws IOException if the report directory or file cannot be written
    void writeReport(Path reportDirectory) throws IOException {
        Objects.requireNonNull(reportDirectory, "reportDirectory");
        Files.createDirectories(reportDirectory);

        int passedCount = passed.get();
        int failedCount = failed.get();
        try (
                var writer = Files.newBufferedWriter(
                        reportDirectory.resolve("summary.json"),
                        StandardCharsets.UTF_8
                );
                JsonGenerator generator = JSON_FACTORY.createGenerator(writer)
        ) {
            generator.writeStartObject();
            generator.writeNumberField("total", total);
            generator.writeNumberField("enabled", enabled);
            generator.writeNumberField("passed", passedCount);
            generator.writeNumberField("failed", failedCount);
            generator.writeNumberField("skipped", skipped.get());
            generator.writeNumberField("compatibility", ratio(passedCount, passedCount + failedCount));
            generator.writeNumberField("coverage", ratio(enabled, total));
            writeCorpus(generator, "upstream", upstreamTotal.get(), upstreamPassed.get(), upstreamFailed.get());
            writeCorpus(generator, "owned", ownedTotal.get(), ownedPassed.get(), ownedFailed.get());
            writeKind(generator, "output", outputPassed.get(), outputFailed.get());
            writeKind(generator, "diagnostic", diagnosticPassed.get(), diagnosticFailed.get());
            generator.writeObjectFieldStart("skippedByCategory");
            for (Map.Entry<String, Integer> entry : actualSkippedByCategory().entrySet()) {
                generator.writeNumberField(entry.getKey(), entry.getValue());
            }
            generator.writeEndObject();
            generator.writeEndObject();
        }
    }

    /// Writes one corpus ownership block.
    private static void writeCorpus(
            JsonGenerator generator,
            String name,
            int totalCount,
            int passedCount,
            int failedCount
    ) throws IOException {
        generator.writeObjectFieldStart(name);
        generator.writeNumberField("total", totalCount);
        generator.writeNumberField("passed", passedCount);
        generator.writeNumberField("failed", failedCount);
        generator.writeNumberField("compatibility", ratio(passedCount, passedCount + failedCount));
        generator.writeEndObject();
    }

    /// Writes one assertion-kind block.
    private static void writeKind(
            JsonGenerator generator,
            String name,
            int passedCount,
            int failedCount
    ) throws IOException {
        generator.writeObjectFieldStart(name);
        generator.writeNumberField("passed", passedCount);
        generator.writeNumberField("failed", failedCount);
        generator.writeNumberField("compatibility", ratio(passedCount, passedCount + failedCount));
        generator.writeEndObject();
    }

    /// Returns actual skip counts in deterministic category order.
    ///
    /// @return an immutable sorted category map
    private @Unmodifiable Map<String, Integer> actualSkippedByCategory() {
        var values = new TreeMap<String, Integer>();
        for (Map.Entry<String, AtomicInteger> entry : skippedByCategory.entrySet()) {
            values.put(entry.getKey(), entry.getValue().get());
        }
        return Collections.unmodifiableMap(values);
    }

    /// Formats one ratio as a locale-independent percentage.
    ///
    /// @param numerator the achieved count
    /// @param denominator the available count
    /// @return the percentage with one decimal place
    private static String percentage(int numerator, int denominator) {
        return String.format(Locale.ROOT, "%.1f%%", ratio(numerator, denominator) * 100.0d);
    }

    /// Calculates one safe ratio for a possibly empty denominator.
    ///
    /// @param numerator the achieved count
    /// @param denominator the available count
    /// @return zero for an empty denominator, otherwise the mathematical ratio
    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }
}
