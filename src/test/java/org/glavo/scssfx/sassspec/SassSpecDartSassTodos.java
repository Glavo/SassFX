// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.sassspec;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Applies sass-spec {@code options.yml} dart-sass todo markings as skips.
///
/// The official suite does not require dart-sass to pass cases listed under
/// {@code :todo: - dart-sass}. At the scssfx pin, those cases are skipped with
/// an explicit reason rather than treated as language failures.
@NotNullByDefault
final class SassSpecDartSassTodos {
    private SassSpecDartSassTodos() {
    }

    /// Reclassifies a run case as skip when its archive options mark dart-sass todo.
    ///
    /// @param archive the HRX archive containing the case
    /// @param fixture the manifest case
    /// @return the original case, or a skip with a pin-aligned reason
    static SassSpecManifest.Case apply(
            HrxArchive archive,
            SassSpecManifest.Case fixture
    ) {
        if (fixture.action() != SassSpecManifest.Action.RUN) {
            return fixture;
        }
        @Nullable String options = archive.content(fixture.directory() + "/options.yml");
        if (options == null || !isDartSassTodo(options)) {
            return fixture;
        }
        return new SassSpecManifest.Case(
                fixture.directory(),
                SassSpecManifest.Action.SKIP,
                fixture.category(),
                "sass-spec options.yml marks todo for dart-sass at pin 1.101.3",
                List.of(),
                List.of()
        );
    }

    /// Returns whether an options.yml body lists dart-sass under {@code :todo:}.
    ///
    /// @param optionsYaml the raw options.yml contents
    /// @return whether dart-sass is a listed todo engine
    static boolean isDartSassTodo(String optionsYaml) {
        var lines = optionsYaml.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        var inTodo = false;
        for (var line : lines) {
            var stripped = line.strip();
            if (stripped.startsWith("#") || stripped.isEmpty() || stripped.equals("---")) {
                continue;
            }
            if (stripped.equals(":todo:") || stripped.equals("todo:")) {
                inTodo = true;
                continue;
            }
            if (inTodo) {
                if (stripped.startsWith("-")) {
                    var item = stripped.substring(1).strip();
                    if (item.equals("dart-sass")
                            || item.equals("\"dart-sass\"")
                            || item.equals("'dart-sass'")) {
                        return true;
                    }
                    continue;
                }
                if (!stripped.isEmpty() && !line.isEmpty()
                        && !Character.isWhitespace(line.charAt(0))) {
                    inTodo = false;
                }
            }
            if (stripped.contains("dart-sass")
                    && (stripped.startsWith(":todo:") || stripped.startsWith("todo:"))) {
                return true;
            }
        }
        return false;
    }
}
