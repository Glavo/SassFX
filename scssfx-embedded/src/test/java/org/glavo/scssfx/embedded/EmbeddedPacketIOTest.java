// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Embedded Sass length and compilation-ID framing.
@NotNullByDefault
final class EmbeddedPacketIOTest {
    /// Verifies boundary compilation IDs use canonical unsigned varints.
    @Test
    void writesCompilationIdVarintBoundaries() throws IOException {
        assertEncoding(0, bytes(2, 0, 1));
        assertEncoding(127, bytes(2, 127, 1));
        assertEncoding(128, bytes(3, 0x80, 1, 1));
        assertEncoding(300, bytes(3, 0xac, 2, 1));
        assertEncoding(
                0xffff_ffffL,
                bytes(6, 0xff, 0xff, 0xff, 0xff, 0x0f, 1)
        );
    }

    /// Verifies a multi-byte packet length is written and read correctly.
    @Test
    void writesMultiBytePacketLength() throws IOException {
        var body = new byte[299];
        Arrays.fill(body, (byte) 1);
        var output = new ByteArrayOutputStream();

        EmbeddedPacketIO.write(output, 0, body);

        var encoded = output.toByteArray();
        assertEquals(0xac, Byte.toUnsignedInt(encoded[0]));
        assertEquals(2, Byte.toUnsignedInt(encoded[1]));
        var packet = assertInstanceOf(
                EmbeddedPacketIO.Packet.class,
                EmbeddedPacketIO.read(new ByteArrayInputStream(encoded))
        );
        assertEquals(0, packet.compilationId());
        assertArrayEquals(body, packet.message());
    }

    /// Verifies framing remains correct when the stream supplies one byte per read.
    @Test
    void readsFromSingleByteChunks() throws IOException {
        var output = new ByteArrayOutputStream();
        var body = new byte[300];
        Arrays.fill(body, (byte) 7);
        EmbeddedPacketIO.write(output, 300, body);

        var packet = assertInstanceOf(
                EmbeddedPacketIO.Packet.class,
                EmbeddedPacketIO.read(
                        new OneByteInputStream(output.toByteArray())
                )
        );

        assertEquals(300, packet.compilationId());
        assertArrayEquals(body, packet.message());
    }

    /// Verifies adjacent frames are decoded independently and clean EOF is reported.
    @Test
    void readsMultiplePackets() throws IOException {
        var output = new ByteArrayOutputStream();
        EmbeddedPacketIO.write(output, 1, bytes(10));
        EmbeddedPacketIO.write(output, 2, bytes(20, 30));
        EmbeddedPacketIO.write(output, 3, bytes(40, 50, 60));
        var input = new ByteArrayInputStream(output.toByteArray());

        assertPacket(
                assertInstanceOf(
                        EmbeddedPacketIO.Packet.class,
                        EmbeddedPacketIO.read(input)
                ),
                1,
                bytes(10)
        );
        assertPacket(
                assertInstanceOf(
                        EmbeddedPacketIO.Packet.class,
                        EmbeddedPacketIO.read(input)
                ),
                2,
                bytes(20, 30)
        );
        assertPacket(
                assertInstanceOf(
                        EmbeddedPacketIO.Packet.class,
                        EmbeddedPacketIO.read(input)
                ),
                3,
                bytes(40, 50, 60)
        );
        assertNull(EmbeddedPacketIO.read(input));
    }

    /// Verifies a truncated length varint is rejected.
    @Test
    void rejectsTruncatedLengthVarint() {
        var failure = assertThrows(
                EOFException.class,
                () -> EmbeddedPacketIO.read(
                        new ByteArrayInputStream(bytes(0x80))
                )
        );
        assertTrue(failure.getMessage().contains("Truncated"));
    }

    /// Verifies a frame shorter than its declared length is rejected.
    @Test
    void rejectsTruncatedPacket() {
        var failure = assertThrows(
                EOFException.class,
                () -> EmbeddedPacketIO.read(
                        new ByteArrayInputStream(bytes(3, 1, 10))
                )
        );
        assertTrue(failure.getMessage().contains("Truncated"));
    }

    /// Verifies a packet-length varint wider than 53 bits is rejected.
    @Test
    void rejectsOverflowingPacketLength() {
        var failure = assertThrows(
                IOException.class,
                () -> EmbeddedPacketIO.read(new ByteArrayInputStream(bytes(
                        0x80, 0x80, 0x80, 0x80,
                        0x80, 0x80, 0x80, 0x10
                )))
        );
        assertTrue(failure.getMessage().contains("53 bits"));
    }

    /// Verifies a compilation-ID varint wider than 32 bits is rejected.
    @Test
    void rejectsOverflowingCompilationId() {
        var failure = assertThrows(
                IOException.class,
                () -> EmbeddedPacketIO.read(new ByteArrayInputStream(bytes(
                        6,
                        0x80, 0x80, 0x80, 0x80, 0x10,
                        1
                )))
        );
        assertTrue(failure.getMessage().contains("32 bits"));
    }

    /// Verifies an empty packet is rejected because it has no compilation ID.
    @Test
    void rejectsEmptyPacket() {
        var failure = assertThrows(
                IOException.class,
                () -> EmbeddedPacketIO.read(
                        new ByteArrayInputStream(bytes(0))
                )
        );
        assertTrue(failure.getMessage().contains("no compilation ID"));
    }

    /// Verifies a packet containing only a compilation ID is rejected.
    @Test
    void rejectsMissingProtobufBody() {
        var failure = assertThrows(
                IOException.class,
                () -> EmbeddedPacketIO.read(
                        new ByteArrayInputStream(bytes(1, 0))
                )
        );
        assertTrue(failure.getMessage().contains("no protobuf body"));
    }

    /// Writes one packet and checks its exact byte representation and round trip.
    ///
    /// @param compilationId the compilation ID
    /// @param expected the complete expected frame
    private static void assertEncoding(
            long compilationId,
            byte @Unmodifiable [] expected
    ) throws IOException {
        var output = new ByteArrayOutputStream();
        EmbeddedPacketIO.write(output, compilationId, bytes(1));
        assertArrayEquals(expected, output.toByteArray());

        var packet = assertInstanceOf(
                EmbeddedPacketIO.Packet.class,
                EmbeddedPacketIO.read(
                        new ByteArrayInputStream(output.toByteArray())
                )
        );
        assertEquals(compilationId, packet.compilationId());
        assertArrayEquals(bytes(1), packet.message());
    }

    /// Checks one decoded packet.
    ///
    /// @param packet the decoded packet
    /// @param compilationId the expected compilation ID
    /// @param message the expected protobuf body
    private static void assertPacket(
            EmbeddedPacketIO.Packet packet,
            long compilationId,
            byte @Unmodifiable [] message
    ) {
        assertEquals(compilationId, packet.compilationId());
        assertArrayEquals(message, packet.message());
    }

    /// Converts unsigned byte literals to a byte array.
    ///
    /// @param values values in the range representable by their low eight bits
    /// @return a newly allocated byte array
    private static byte @Unmodifiable [] bytes(int... values) {
        var result = new byte[values.length];
        for (var index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }

    /// Exposes an array as an input stream that returns at most one byte per call.
    @NotNullByDefault
    private static final class OneByteInputStream extends InputStream {
        /// Supplies the underlying bytes.
        private final ByteArrayInputStream input;

        /// Creates a stream over a snapshot of the supplied bytes.
        ///
        /// @param contents the bytes to expose
        private OneByteInputStream(byte @Unmodifiable [] contents) {
            input = new ByteArrayInputStream(
                    Arrays.copyOf(contents, contents.length)
            );
        }

        /// Reads the next byte.
        ///
        /// @return the next unsigned byte, or {@code -1} at EOF
        @Override
        public int read() {
            return input.read();
        }

        /// Reads at most one byte into the destination.
        ///
        /// @param destination the destination array
        /// @param offset the first destination index
        /// @param length the maximum requested byte count
        /// @return one, zero, or {@code -1} according to stream state
        @Override
        public int read(byte[] destination, int offset, int length) {
            return input.read(destination, offset, Math.min(length, 1));
        }
    }
}
