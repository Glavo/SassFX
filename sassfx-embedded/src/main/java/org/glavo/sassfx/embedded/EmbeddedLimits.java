// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import org.jetbrains.annotations.NotNullByDefault;

/// Configures resource limits for one Embedded Sass endpoint.
///
/// All limits are enforced per
/// [EmbeddedCompiler#run(java.io.InputStream, java.io.OutputStream)] invocation.
/// A packet that exceeds
/// [#maxPacketLength()] is rejected before its body is allocated. Compilation
/// and inbound-event limits bound memory retained when a host submits work
/// faster than the endpoint can process it.
///
/// @param maxPacketLength the largest accepted framed packet in bytes
/// @param maxConcurrentCompilations the largest number of compilation workers
/// @param maxQueuedCompilations the largest number of compilations waiting for
///                              a worker
/// @param maxInboundEvents the largest number of decoded inbound packets
///                         waiting for endpoint dispatch
@NotNullByDefault
public record EmbeddedLimits(
        int maxPacketLength,
        int maxConcurrentCompilations,
        int maxQueuedCompilations,
        int maxInboundEvents
) {
    /// Provides conservative limits suitable for a local compiler process.
    public static final EmbeddedLimits DEFAULT = new EmbeddedLimits(
            64 * 1024 * 1024,
            Math.max(
                    2,
                    Math.min(16, Runtime.getRuntime().availableProcessors())
            ),
            64,
            256
    );

    /// Creates endpoint resource limits.
    ///
    /// @throws IllegalArgumentException if any limit is not positive
    public EmbeddedLimits {
        if (maxPacketLength <= 0) {
            throw new IllegalArgumentException(
                    "maxPacketLength must be positive"
            );
        }
        if (maxConcurrentCompilations <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrentCompilations must be positive"
            );
        }
        if (maxQueuedCompilations <= 0) {
            throw new IllegalArgumentException(
                    "maxQueuedCompilations must be positive"
            );
        }
        if (maxInboundEvents <= 0) {
            throw new IllegalArgumentException(
                    "maxInboundEvents must be positive"
            );
        }
    }
}
