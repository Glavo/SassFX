// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import org.jetbrains.annotations.NotNullByDefault;

/// Provides the standalone Embedded Sass compiler process entry point.
@NotNullByDefault
public final class ScssfxEmbeddedMain {
    /// Prevents instantiation.
    private ScssfxEmbeddedMain() {
    }

    /// Runs the endpoint over standard input and standard output.
    ///
    /// @param args no arguments for protocol mode, or only {@code --version}
    public static void main(String[] args) {
        if (args.length == 1 && "--version".equals(args[0])) {
            System.out.println(EmbeddedCompiler.versionJson());
            return;
        }
        if (args.length != 0) {
            System.err.println(
                    "scssfx-embedded accepts only --version."
            );
            System.exit(64);
        }
        System.exit(new EmbeddedCompiler().run(System.in, System.out));
    }
}
