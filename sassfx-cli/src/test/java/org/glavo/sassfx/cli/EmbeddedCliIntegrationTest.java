// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.cli;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies that the primary CLI exposes the binary Embedded Sass endpoint.
@NotNullByDefault
final class EmbeddedCliIntegrationTest {
    /// The protocol field number of an inbound version request.
    private static final int INBOUND_VERSION_REQUEST_FIELD = 7;

    /// The protocol field number of an outbound version response.
    private static final int OUTBOUND_VERSION_RESPONSE_FIELD = 8;

    /// Sends one raw protocol 3.2.0 version request through the CLI streams.
    @Test
    void dispatchesRawEmbeddedVersionRequest(@TempDir Path directory)
            throws IOException {
        var requestId = 123;
        var binaryOutput = new ByteArrayOutputStream();
        var command = new SassFXMain(
                new ByteArrayInputStream(versionRequest(requestId)),
                binaryOutput,
                directory
        );
        var textOutput = new StringWriter();
        var error = new StringWriter();
        var commandLine = SassFXMain.configure(new CommandLine(command))
                .setOut(new PrintWriter(textOutput, true))
                .setErr(new PrintWriter(error, true));

        assertEquals(0, commandLine.execute("--embedded"));
        assertEquals("", textOutput.toString());
        assertEquals("", error.toString());

        var response = readFrame(binaryOutput.toByteArray());
        assertEquals(0, response.compilationId());
        var wrapper = new ByteArrayInputStream(response.message());
        assertEquals(
                (OUTBOUND_VERSION_RESPONSE_FIELD << 3) | 2,
                readVarint(wrapper)
        );
        var versionMessage = readBytes(
                wrapper,
                Math.toIntExact(readVarint(wrapper))
        );
        assertEquals(0, wrapper.available());

        var fields = readVersionResponse(versionMessage);
        assertEquals("3.2.0", fields.strings().get(1));
        assertEquals("1.101.3", fields.strings().get(2));
        assertEquals("0.1.0-SNAPSHOT", fields.strings().get(3));
        assertEquals("sassfx", fields.strings().get(4));
        assertEquals(requestId, fields.id());
    }

    /// Rejects ordinary CLI options when Embedded Sass mode is selected.
    @Test
    void rejectsAdditionalEmbeddedArguments(@TempDir Path directory) {
        var binaryOutput = new ByteArrayOutputStream();
        var command = new SassFXMain(
                new ByteArrayInputStream(new byte[0]),
                binaryOutput,
                directory
        );
        var output = new StringWriter();
        var error = new StringWriter();
        var commandLine = SassFXMain.configure(new CommandLine(command))
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(error, true));

        assertEquals(
                64,
                commandLine.execute("--embedded", "--style", "compressed")
        );
        assertEquals("", output.toString());
        assertArrayEquals(new byte[0], binaryOutput.toByteArray());
        assertTrue(error.toString().contains(
                "--embedded is not intended to be executed with additional arguments"
        ));
    }

    /// Encodes a framed protocol 3.2.0 version request.
    ///
    /// @param requestId the request ID
    /// @return the complete length-delimited packet
    private static byte @Unmodifiable [] versionRequest(int requestId) {
        var message = new ByteArrayOutputStream();
        writeVarint(
                message,
                (INBOUND_VERSION_REQUEST_FIELD << 3) | 2
        );
        var request = new ByteArrayOutputStream();
        writeVarint(request, 1 << 3);
        writeVarint(request, requestId);
        writeVarint(message, request.size());
        message.writeBytes(request.toByteArray());

        var packet = new ByteArrayOutputStream();
        writeVarint(packet, 0);
        packet.writeBytes(message.toByteArray());
        var framed = new ByteArrayOutputStream();
        writeVarint(framed, packet.size());
        framed.writeBytes(packet.toByteArray());
        return framed.toByteArray();
    }

    /// Reads one complete length-delimited protocol packet.
    ///
    /// @param bytes the encoded stream
    /// @return the decoded packet
    /// @throws IOException if the stream is malformed or contains extra data
    private static Packet readFrame(byte @Unmodifiable [] bytes)
            throws IOException {
        var input = new ByteArrayInputStream(bytes);
        var packet = new ByteArrayInputStream(
                readBytes(input, Math.toIntExact(readVarint(input)))
        );
        if (input.available() != 0) {
            throw new IOException("embedded output contains multiple frames");
        }
        var compilationId = readVarint(packet);
        var message = readBytes(packet, packet.available());
        return new Packet(compilationId, message);
    }

    /// Decodes the fields of one protocol version response.
    ///
    /// @param bytes the nested VersionResponse message
    /// @return the decoded version fields
    /// @throws IOException if a field has an unsupported or malformed encoding
    private static VersionFields readVersionResponse(
            byte @Unmodifiable [] bytes
    ) throws IOException {
        var input = new ByteArrayInputStream(bytes);
        var strings = new LinkedHashMap<Integer, String>();
        var id = -1L;
        while (input.available() != 0) {
            var tag = readVarint(input);
            var field = Math.toIntExact(tag >>> 3);
            var wireType = Math.toIntExact(tag & 7);
            if (field >= 1 && field <= 4 && wireType == 2) {
                var contents = readBytes(
                        input,
                        Math.toIntExact(readVarint(input))
                );
                strings.put(
                        field,
                        new String(contents, java.nio.charset.StandardCharsets.UTF_8)
                );
            } else if (field == 5 && wireType == 0) {
                id = readVarint(input);
            } else {
                throw new IOException(
                        "unexpected VersionResponse field " + field
                                + " with wire type " + wireType
                );
            }
        }
        return new VersionFields(strings, id);
    }

    /// Reads exactly the requested byte count.
    ///
    /// @param input the source stream
    /// @param length the byte count
    /// @return the requested bytes
    /// @throws IOException if the stream ends early
    private static byte @Unmodifiable [] readBytes(
            ByteArrayInputStream input,
            int length
    ) throws IOException {
        var bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("truncated protocol field");
        }
        return bytes;
    }

    /// Reads one unsigned protobuf varint.
    ///
    /// @param input the source stream
    /// @return the decoded value
    /// @throws IOException if the varint is truncated or wider than 64 bits
    private static long readVarint(ByteArrayInputStream input)
            throws IOException {
        var result = 0L;
        for (var shift = 0; shift < 64; shift += 7) {
            var next = input.read();
            if (next < 0) {
                throw new EOFException("truncated varint");
            }
            result |= (long) (next & 0x7f) << shift;
            if ((next & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("varint is wider than 64 bits");
    }

    /// Writes one unsigned protobuf varint.
    ///
    /// @param output the destination
    /// @param value the nonnegative value
    private static void writeVarint(
            ByteArrayOutputStream output,
            long value
    ) {
        var remaining = value;
        do {
            var next = (int) (remaining & 0x7f);
            remaining >>>= 7;
            output.write(remaining == 0 ? next : next | 0x80);
        } while (remaining != 0);
    }

    /// Contains one decoded protocol packet.
    ///
    /// @param compilationId the unsigned compilation ID
    /// @param message the protobuf wrapper bytes
    @NotNullByDefault
    private record Packet(
            long compilationId,
            byte @Unmodifiable [] message
    ) {
        /// Creates an immutable packet.
        private Packet {
            message = Arrays.copyOf(message, message.length);
        }
    }

    /// Contains fields decoded from a VersionResponse message.
    ///
    /// @param strings string fields keyed by protocol field number
    /// @param id the unsigned request ID
    @NotNullByDefault
    private record VersionFields(
            @Unmodifiable Map<Integer, String> strings,
            long id
    ) {
        /// Creates an immutable version-field snapshot.
        private VersionFields {
            strings = Map.copyOf(strings);
        }
    }
}
