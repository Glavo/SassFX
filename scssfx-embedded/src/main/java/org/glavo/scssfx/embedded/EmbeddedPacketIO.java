// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/// Reads and writes length-delimited Embedded Sass protocol packets.
///
/// Each frame contains an unsigned 53-bit length followed by an unsigned
/// 32-bit compilation ID and a protobuf message. Instances are not required
/// because all framing operations are stateless.
@NotNullByDefault
final class EmbeddedPacketIO {
    /// The largest packet that can be represented by one Java byte array.
    private static final long MAX_JAVA_PACKET_LENGTH = Integer.MAX_VALUE - 8L;

    /// Prevents instantiation.
    private EmbeddedPacketIO() {
    }

    /// Reads one complete packet.
    ///
    /// The method blocks until one frame is complete, EOF is observed before a
    /// new frame, or an IO/framing failure occurs. EOF after any prefix byte or
    /// packet byte is treated as a truncated-frame failure.
    ///
    /// @param input the byte stream to read
    /// @return the decoded packet, or {@code null} at clean EOF
    /// @throws IOException if the stream fails or the frame is malformed
    static @Nullable Packet read(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        var packetLength = readVarint(input, 53, true);
        if (packetLength < 0) {
            return null;
        }
        if (packetLength == 0) {
            throw new IOException("Embedded Sass packet has no compilation ID.");
        }
        if (packetLength > MAX_JAVA_PACKET_LENGTH) {
            throw new IOException(
                    "Embedded Sass packet exceeds the Java array size limit."
            );
        }

        var packet = input.readNBytes((int) packetLength);
        if (packet.length != packetLength) {
            throw new EOFException("Truncated Embedded Sass packet.");
        }
        var packetInput = new ByteArrayInputStream(packet);
        var compilationId = readVarint(packetInput, 32, false);
        var message = packetInput.readAllBytes();
        if (message.length == 0) {
            throw new IOException("Embedded Sass packet has no protobuf body.");
        }
        return new Packet(compilationId, message);
    }

    /// Writes one complete packet and flushes it.
    ///
    /// @param output the byte stream to write
    /// @param compilationId the unsigned 32-bit compilation ID
    /// @param message the encoded protobuf body
    /// @throws IOException if the stream fails
    static void write(
            OutputStream output,
            long compilationId,
            byte @Unmodifiable [] message
    ) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(message, "message");
        if (compilationId < 0 || compilationId > 0xffff_ffffL) {
            throw new IllegalArgumentException(
                    "compilationId must be an unsigned 32-bit integer"
            );
        }

        var packet = new ByteArrayOutputStream(message.length + 5);
        writeVarint(packet, compilationId);
        packet.write(message);
        writeVarint(output, packet.size());
        packet.writeTo(output);
        output.flush();
    }

    /// Reads one bounded unsigned base-128 integer.
    ///
    /// @param input the source stream
    /// @param bits the maximum bit width
    /// @param cleanEofAllowed whether EOF before the first byte returns a sentinel
    /// @return the decoded value, or {@code -1} for permitted clean EOF
    /// @throws IOException if the integer is truncated or exceeds its bit width
    private static long readVarint(
            InputStream input,
            int bits,
            boolean cleanEofAllowed
    ) throws IOException {
        var result = 0L;
        for (var shift = 0; shift < bits; shift += 7) {
            var current = input.read();
            if (current < 0) {
                if (shift == 0 && cleanEofAllowed) {
                    return -1;
                }
                throw new EOFException("Truncated Embedded Sass varint.");
            }

            var payload = current & 0x7f;
            var availableBits = bits - shift;
            if (availableBits < 7 && payload >= 1 << availableBits) {
                throw new IOException(
                        "Embedded Sass varint exceeds " + bits + " bits."
                );
            }
            result |= (long) payload << shift;
            if ((current & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException(
                "Embedded Sass varint exceeds " + bits + " bits."
        );
    }

    /// Writes one non-negative integer in unsigned base-128 form.
    ///
    /// @param output the destination
    /// @param value the non-negative value
    /// @throws IOException if the stream fails
    private static void writeVarint(OutputStream output, long value)
            throws IOException {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        var remaining = value;
        do {
            var current = (int) (remaining & 0x7f);
            remaining >>>= 7;
            output.write(remaining == 0 ? current : current | 0x80);
        } while (remaining != 0);
    }

    /// Contains one decoded packet.
    ///
    /// @param compilationId the unsigned 32-bit compilation ID
    /// @param message the encoded protobuf body
    @NotNullByDefault
    record Packet(
            long compilationId,
            byte @Unmodifiable [] message
    ) {
        /// Creates an immutable packet snapshot.
        Packet {
            if (compilationId < 0 || compilationId > 0xffff_ffffL) {
                throw new IllegalArgumentException(
                        "compilationId must be an unsigned 32-bit integer"
                );
            }
            message = Arrays.copyOf(message, message.length);
        }

        /// Returns an immutable copy of the protobuf body.
        ///
        /// @return a newly allocated body copy
        @Override
        public byte @Unmodifiable [] message() {
            return Arrays.copyOf(message, message.length);
        }
    }
}
