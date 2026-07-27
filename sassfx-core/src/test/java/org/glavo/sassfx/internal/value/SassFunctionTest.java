// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.value;

import org.glavo.sassfx.internal.callable.BuiltInCallable;
import org.glavo.sassfx.internal.callable.PlainCssCallable;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies the value-layer contract of first-class Sass function references.
@NotNullByDefault
final class SassFunctionTest {
    /// Retains callable equality while rejecting CSS output and cross-compilation invocation.
    @Test
    void representsCallableReferencesWithoutCssSerialization() {
        var callable = BuiltInCallable.of("identity", List.of("value"), values -> values.get(0));
        var token = new Object();
        var function = new SassFunction(callable, token);

        assertSame(function, function.assertFunction());
        assertEquals("get-function(\"identity\")", function.toString());
        assertEquals(
                "get-function(\"identity\") isn't a valid CSS value.",
                assertThrows(SassValueException.class, function::toCssString).getMessage()
        );
        assertEquals(
                "get-function(\"identity\") does not belong to current compilation.",
                assertThrows(
                        SassValueException.class,
                        () -> function.assertCompilationContext(new Object())
                ).getMessage()
        );
        assertEquals(function, new SassFunction(callable, new Object()));
        assertEquals(function.hashCode(), new SassFunction(callable, new Object()).hashCode());
        assertEquals(
                new SassFunction(new PlainCssCallable("var"), token),
                new SassFunction(new PlainCssCallable("var"), new Object())
        );
    }
}