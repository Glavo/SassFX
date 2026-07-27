// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Receives warning, deprecation, and debug events during compilation.
///
/// A logger may be invoked concurrently when the same compile options are
/// shared by concurrent compilations. Implementations must be thread-safe.
/// Events are delivered synchronously on the compiling thread. A runtime
/// exception thrown by the logger aborts compilation and propagates unchanged.
@FunctionalInterface
@NotNullByDefault
public interface SassLogger {
    /// A logger that discards every event.
    SassLogger NO_OP = event -> {
    };

    /// Receives one processed compiler event.
    ///
    /// Suppressed dependency warnings, silenced deprecations, and repetitive
    /// deprecations beyond the configured limit are not delivered.
    ///
    /// @param event the immutable log event
    void log(SassLogEvent event);
}
