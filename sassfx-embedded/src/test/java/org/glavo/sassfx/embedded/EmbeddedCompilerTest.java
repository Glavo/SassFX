// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import com.google.protobuf.Descriptors;
import com.sass_lang.embedded_protocol.Value;
import org.glavo.sassfx.SassFXVersion;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Verifies Embedded Sass version metadata and the pinned protocol schema.
@NotNullByDefault
final class EmbeddedCompilerTest {
    /// Verifies every standalone version JSON field and its stable layout.
    @Test
    void reportsVersionJsonFields() {
        assertEquals(
                """
                        {
                          "protocolVersion": "3.2.0",
                          "compilerVersion": "1.102.0",
                          "implementationVersion": "%s",
                          "implementationName": "sassfx",
                          "id": 0
                        }""".formatted(SassFXVersion.current()),
                EmbeddedCompiler.versionJson()
        );
    }

    /// Verifies the standalone entry point prints the same version document.
    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void standaloneMainPrintsVersionJson() {
        var original = System.out;
        var output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(
                    output,
                    true,
                    StandardCharsets.UTF_8
            ));
            SassFXEmbeddedMain.main(new String[]{"--version"});
        } finally {
            System.setOut(original);
        }

        assertEquals(
                EmbeddedCompiler.versionJson() + System.lineSeparator(),
                output.toString(StandardCharsets.UTF_8)
        );
    }

    /// Verifies protocol 3.2.0 color and compiler-mixin wire fields.
    @Test
    void exposesProtocol320ValueFields() {
        var value = Value.getDescriptor();
        var color = assertField(
                value,
                "color",
                14,
                Descriptors.FieldDescriptor.JavaType.MESSAGE
        );
        assertEquals("Color", color.getMessageType().getName());
        assertField(
                color.getMessageType(),
                "space",
                1,
                Descriptors.FieldDescriptor.JavaType.STRING
        );
        assertField(
                color.getMessageType(),
                "channel1",
                2,
                Descriptors.FieldDescriptor.JavaType.DOUBLE
        );
        assertField(
                color.getMessageType(),
                "channel2",
                3,
                Descriptors.FieldDescriptor.JavaType.DOUBLE
        );
        assertField(
                color.getMessageType(),
                "channel3",
                4,
                Descriptors.FieldDescriptor.JavaType.DOUBLE
        );
        assertField(
                color.getMessageType(),
                "alpha",
                5,
                Descriptors.FieldDescriptor.JavaType.DOUBLE
        );

        var compilerMixin = assertField(
                value,
                "compiler_mixin",
                13,
                Descriptors.FieldDescriptor.JavaType.MESSAGE
        );
        assertEquals(
                "CompilerMixin",
                compilerMixin.getMessageType().getName()
        );
        assertField(
                compilerMixin.getMessageType(),
                "id",
                1,
                Descriptors.FieldDescriptor.JavaType.INT
        );
    }

    /// Returns and verifies one descriptor field.
    ///
    /// @param message the containing message descriptor
    /// @param name the protobuf field name
    /// @param number the expected wire field number
    /// @param javaType the expected generated Java type
    /// @return the non-null field descriptor
    private static Descriptors.FieldDescriptor assertField(
            Descriptors.Descriptor message,
            String name,
            int number,
            Descriptors.FieldDescriptor.JavaType javaType
    ) {
        var field = message.findFieldByName(name);
        assertNotNull(field);
        assertEquals(number, field.getNumber());
        assertEquals(javaType, field.getJavaType());
        return field;
    }
}
