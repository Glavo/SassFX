package example;

import org.glavo.sassfx.SassCompiler;
import org.jetbrains.annotations.NotNullByDefault;

/// Verifies that the published compiler API is available to Java consumers.
@NotNullByDefault
public final class Consumer {
    /// Prevents instantiation.
    private Consumer() {
    }

    /// Returns a compiler obtained from the published library artifact.
    ///
    /// @return a new compiler
    public static SassCompiler compiler() {
        return new SassCompiler();
    }
}
