// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.glavo.sassfx.SassFXVersion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import picocli.CommandLine;

/// Supplies the generated SassFX version to Picocli.
@NotNullByDefault
final class SassFXVersionProvider implements CommandLine.IVersionProvider {
    /// Creates a version provider.
    SassFXVersionProvider() {
    }

    /// Returns the command-line version text.
    ///
    /// @return one immutable version line
    @Override
    public String @Unmodifiable [] getVersion() {
        return new String[]{"sassfx " + SassFXVersion.current()};
    }
}
