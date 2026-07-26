// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.sourcemap;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Encodes integers with the Base64 VLQ format used by version 3 source maps.
@ApiStatus.Internal
@NotNullByDefault
public final class Vlq {
    /// Contains the Base64 alphabet used by source-map VLQ segments.
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    /// Prevents instantiation.
    private Vlq() {
    }

    /// Appends one signed integer encoded as Base64 VLQ.
    ///
    /// @param value  the integer to encode
    /// @param buffer the destination
    public static void encode(int value, StringBuilder buffer) {
        var vlq = value < 0 ? ((-value) << 1) | 1 : value << 1;
        do {
            var digit = vlq & 0x1f;
            vlq >>>= 5;
            if (vlq != 0) {
                digit |= 0x20;
            }
            buffer.append(ALPHABET[digit]);
        } while (vlq != 0);
    }
}
