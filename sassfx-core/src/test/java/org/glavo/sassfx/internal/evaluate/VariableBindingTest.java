// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.evaluate;

import org.glavo.sassfx.internal.source.SourceFile;
import org.glavo.sassfx.internal.value.SassNumber;
import org.glavo.sassfx.internal.value.SassValueException;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies live variable-binding assignment and built-in mutability policy.
@NotNullByDefault
final class VariableBindingTest {
    /// Updates a writable binding's value and source origin in place.
    @Test
    void assignsWritableBindingInPlace() {
        var source = new SourceFile("$value: 1; $value: 2;", null);
        var firstSpan = source.span(8, 9);
        var secondSpan = source.span(20, 21);
        var binding = new VariableBinding(SassNumber.of(1, null), firstSpan);

        binding.assign(SassNumber.of(2, null), secondSpan);

        assertEquals(SassNumber.of(2, null), binding.value());
        assertSame(secondSpan, binding.originSpan());
    }

    /// Rejects assignment to a read-only binding without changing its state.
    @Test
    void rejectsReadOnlyBindingAssignment() {
        var source = new SourceFile("3.14159", null);
        var span = source.span(0, source.content().length());
        var value = SassNumber.of(3.14159, null);
        var binding = VariableBinding.readOnly(value, span);

        var failure = assertThrows(
                SassValueException.class,
                () -> binding.assign(SassNumber.of(0, null), source.span(0, 1))
        );

        assertEquals("Cannot modify built-in variable.", failure.getMessage());
        assertSame(value, binding.value());
        assertSame(span, binding.originSpan());
    }
}
