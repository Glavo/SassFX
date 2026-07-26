// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.glavo.scssfx.internal.callable.BuiltInCallable;
import org.glavo.scssfx.internal.callable.PlainCssCallable;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies the value-layer contract of first-class Sass mixin references.
@NotNullByDefault
final class SassMixinTest {
    /// Retains callable equality while rejecting CSS output and cross-compilation inclusion.
    @Test
    void representsCallableReferencesWithoutCssSerialization() {
        var callable = BuiltInCallable.of("identity", List.of("value"), values -> values.get(0));
        var token = new Object();
        var mixin = new SassMixin(callable, token);

        assertSame(mixin, mixin.assertMixin());
        assertEquals("get-mixin(\"identity\")", mixin.toString());
        assertEquals(
                "get-mixin(\"identity\") isn't a valid CSS value.",
                assertThrows(SassValueException.class, mixin::toCssString).getMessage()
        );
        assertEquals(
                "get-mixin(\"identity\") does not belong to current compilation.",
                assertThrows(
                        SassValueException.class,
                        () -> mixin.assertCompilationContext(new Object())
                ).getMessage()
        );
        assertEquals(mixin, new SassMixin(callable, new Object()));
        assertEquals(mixin.hashCode(), new SassMixin(callable, new Object()).hashCode());
        assertEquals(
                new SassMixin(new PlainCssCallable("var"), token),
                new SassMixin(new PlainCssCallable("var"), new Object())
        );
    }
}