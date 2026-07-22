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
    void recordPassed() {
        passed.incrementAndGet();
    }

    /// Records one failed executable fixture.
    void recordFailed() {
        failed.incrementAndGet();
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
            generator.writeObjectFieldStart("skippedByCategory");
            for (Map.Entry<String, Integer> entry : actualSkippedByCategory().entrySet()) {
                generator.writeNumberField(entry.getKey(), entry.getValue());
            }
            generator.writeEndObject();
            generator.writeEndObject();
        }
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
