// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.evaluate;

import org.glavo.scssfx.SourceSpan;
import org.glavo.scssfx.internal.source.SourceFile;
import org.glavo.scssfx.internal.value.SassNull;
import org.glavo.scssfx.internal.value.SassString;
import org.glavo.scssfx.internal.value.SassValue;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies lexical frames, flow-control assignments, closures, and immutable snapshots.
@NotNullByDefault
@SuppressWarnings("try")
final class EnvironmentTest {
    /// Verifies that a missing Java binding remains distinct from the Sass null value.
    @Test
    void distinguishesUndefinedVariablesFromSassNull() {
        var environment = new Environment();

        assertNull(environment.findVariable("x", null));
        assertNull(environment.getVariable("x", null));
        assertFalse(environment.variableExists("x", null));
        assertFalse(environment.globalVariableExists("x", null));

        assign(environment, "x", SassNull.NULL);

        assertSame(SassNull.NULL, environment.getVariable("x", null));
        assertTrue(environment.variableExists("x", null));
        assertTrue(environment.globalVariableExists("x", null));
    }

    /// Verifies that global snapshots retain insertion order and earlier binding state.
    @Test
    void snapshotsGlobalsInInsertionOrder() {
        var environment = new Environment();
        var one = value("one");
        var two = value("two");
        var three = value("three");
        assign(environment, "b", one);
        assign(environment, "a", two);

        var valuesBefore = environment.globalVariablesSnapshot();
        var bindingsBefore = environment.globalBindingsSnapshot();
        assign(environment, "b", three);
        assign(environment, "c", one);
        var valuesAfter = environment.globalVariablesSnapshot();

        assertEquals(List.of("b", "a"), List.copyOf(valuesBefore.keySet()));
        assertEquals(one, valuesBefore.get("b"));
        assertEquals(two, valuesBefore.get("a"));
        assertEquals(one, bindingsBefore.get("b").value());
        assertEquals(List.of("b", "a", "c"), List.copyOf(valuesAfter.keySet()));
        assertEquals(three, valuesAfter.get("b"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> valuesBefore.put("d", value("four"))
        );
        assertThrows(UnsupportedOperationException.class, bindingsBefore::clear);
    }

    /// Verifies that a lexical assignment shadows rather than updates a global binding.
    @Test
    void shadowsGlobalsInLexicalScopes() {
        var environment = new Environment();
        var global = value("global");
        var local = value("local");
        assign(environment, "x", global);

        try (var ignored = environment.scope(ScopeSemantics.LEXICAL, true)) {
            assign(environment, "x", local);

            assertFalse(environment.atRoot());
            assertEquals(local, environment.getVariable("x", null));
            assertEquals(global, environment.globalVariablesSnapshot().get("x"));
        }

        assertTrue(environment.atRoot());
        assertEquals(global, environment.getVariable("x", null));
    }

    /// Verifies that a nested assignment updates an existing non-global outer local.
    @Test
    void updatesExistingOuterLocalBindings() {
        var environment = new Environment();
        var global = value("global");
        var outer = value("outer");
        var inner = value("inner");
        assign(environment, "x", global);

        try (var outerScope = environment.scope(ScopeSemantics.LEXICAL, true)) {
            assign(environment, "x", outer);
            try (var innerScope = environment.scope(ScopeSemantics.LEXICAL, true)) {
                assign(environment, "x", inner);
                assertEquals(inner, environment.getVariable("x", null));
            }
            assertEquals(inner, environment.getVariable("x", null));
            assertEquals(global, environment.globalVariablesSnapshot().get("x"));
        }

        assertEquals(global, environment.getVariable("x", null));
    }

    /// Verifies that explicit global assignment leaves a visible local shadow selected.
    @Test
    void updatesGlobalsWithoutReplacingVisibleLocals() {
        var environment = new Environment();
        var global = value("global");
        var local = value("local");
        var changed = value("changed");
        assign(environment, "x", global);

        try (var ignored = environment.scope(ScopeSemantics.LEXICAL, true)) {
            assign(environment, "x", local);
            assignGlobal(environment, "x", changed);

            assertEquals(local, environment.getVariable("x", null));
            assertEquals(changed, environment.globalVariablesSnapshot().get("x"));
        }

        assertEquals(changed, environment.getVariable("x", null));
    }

    /// Verifies that flow control updates existing globals but keeps new names local.
    @Test
    void appliesFlowControlAssignmentSemantics() {
        var environment = new Environment();
        var global = value("global");
        var changed = value("changed");
        var local = value("local");
        assign(environment, "x", global);

        try (var ignored = environment.scope(ScopeSemantics.FLOW_CONTROL, true)) {
            assign(environment, "x", changed);
            assign(environment, "new-name", local);

            assertEquals(changed, environment.globalVariablesSnapshot().get("x"));
            assertFalse(environment.globalVariableExists("new-name", null));
            assertEquals(local, environment.getVariable("new-name", null));
        }

        assertEquals(changed, environment.getVariable("x", null));
        assertNull(environment.getVariable("new-name", null));
    }

    /// Verifies that an enclosing lexical scope prevents nested flow control from reaching globals.
    @Test
    void preventsFlowControlFromCrossingLexicalAncestors() {
        var environment = new Environment();
        var global = value("global");
        var inner = value("inner");
        assign(environment, "x", global);

        try (var lexical = environment.scope(ScopeSemantics.LEXICAL, true)) {
            try (var flow = environment.scope(ScopeSemantics.FLOW_CONTROL, true)) {
                assign(environment, "x", inner);
                assertEquals(inner, environment.getVariable("x", null));
                assertEquals(global, environment.globalVariablesSnapshot().get("x"));
            }
            assertEquals(global, environment.getVariable("x", null));
        }

        assertEquals(global, environment.getVariable("x", null));
    }

    /// Verifies that a scope without a new frame still changes and restores assignment semantics.
    @Test
    void changesSemanticsWithoutCreatingAFrame() {
        var environment = new Environment();
        var originalX = value("original-x");
        var originalZ = value("original-z");
        var localX = value("local-x");
        var changedZ = value("changed-z");
        assign(environment, "x", originalX);
        assign(environment, "z", originalZ);

        try (var flow = environment.scope(ScopeSemantics.FLOW_CONTROL, true)) {
            try (var lexical = environment.scope(ScopeSemantics.LEXICAL, false)) {
                assign(environment, "x", localX);
                assertEquals(localX, environment.getVariable("x", null));
                assertEquals(originalX, environment.globalVariablesSnapshot().get("x"));
            }

            assign(environment, "z", changedZ);
            assertEquals(localX, environment.getVariable("x", null));
            assertEquals(changedZ, environment.globalVariablesSnapshot().get("z"));
        }

        assertEquals(originalX, environment.getVariable("x", null));
        assertEquals(changedZ, environment.getVariable("z", null));
    }

    /// Verifies exception restoration, last-opened-first closing, and idempotent close.
    @Test
    void restoresScopesAfterExceptionsAndRequiresLifoClosing() {
        var environment = new Environment();
        var global = value("global");
        var changed = value("changed");
        assign(environment, "x", global);

        var outer = environment.scope(ScopeSemantics.LEXICAL, true);
        var inner = environment.scope(ScopeSemantics.FLOW_CONTROL, true);
        assertEquals(
                "scopes must be closed in last-opened-first order",
                assertThrows(IllegalStateException.class, outer::close).getMessage()
        );
        assertFalse(environment.atRoot());
        inner.close();
        outer.close();
        outer.close();
        assertTrue(environment.atRoot());

        var failure = assertThrows(IllegalStateException.class, () -> {
            try (var ignored = environment.scope(ScopeSemantics.LEXICAL, true)) {
                assign(environment, "temporary", value("temporary"));
                throw new IllegalStateException("boom");
            }
        });
        assertEquals("boom", failure.getMessage());
        assertTrue(environment.atRoot());
        assertNull(environment.getVariable("temporary", null));

        try (var ignored = environment.scope(ScopeSemantics.FLOW_CONTROL, true)) {
            assign(environment, "x", changed);
        }
        assertEquals(changed, environment.getVariable("x", null));
    }

    /// Verifies that closures share captured frames but not scopes opened after capture.
    @Test
    void sharesExistingFramesWithClosures() {
        var environment = new Environment();
        var initial = value("initial");
        var fromClosure = value("from-closure");
        Environment closure;

        try (var ignored = environment.scope(ScopeSemantics.LEXICAL, true)) {
            assign(environment, "captured", initial);
            closure = environment.closure();
            assign(closure, "captured", fromClosure);
            assertEquals(fromClosure, environment.getVariable("captured", null));

            try (var ownerScope = environment.scope(ScopeSemantics.LEXICAL, true)) {
                assign(environment, "owner-only", value("owner"));
                assertNull(closure.getVariable("owner-only", null));
            }
            try (var closureScope = closure.scope(ScopeSemantics.LEXICAL, true)) {
                assign(closure, "closure-only", value("closure"));
                assertNull(environment.getVariable("closure-only", null));
            }
        }

        assertNull(environment.getVariable("captured", null));
        assertEquals(fromClosure, closure.getVariable("captured", null));
        assign(closure, "captured", value("after-owner-exit"));
        assertEquals(value("after-owner-exit"), closure.getVariable("captured", null));
    }

    /// Verifies that a closure begins with semi-global assignment semantics.
    @Test
    void resetsDynamicAssignmentModeForClosures() {
        var environment = new Environment();
        var original = value("original");
        var changed = value("changed");
        assign(environment, "global", original);

        try (var ignored = environment.scope(ScopeSemantics.LEXICAL, true)) {
            var closure = environment.closure();
            assign(closure, "global", changed);

            assertEquals(changed, environment.globalVariablesSnapshot().get("global"));
            assertEquals(changed, environment.getVariable("global", null));
            assertEquals(changed, closure.getVariable("global", null));
        }

        assertEquals(changed, environment.getVariable("global", null));
    }

    /// Creates an unquoted test string value.
    ///
    /// @param text the represented text
    /// @return the Sass string
    private static SassString value(String text) {
        return new SassString(text, false);
    }

    /// Assigns an ordinary unqualified variable with a synthetic source span.
    ///
    /// @param environment the environment to mutate
    /// @param name        the normalized variable name
    /// @param value       the assigned value
    private static void assign(Environment environment, String name, SassValue value) {
        environment.setVariable(name, value, origin(name), null, false);
    }

    /// Assigns an explicit global variable with a synthetic source span.
    ///
    /// @param environment the environment to mutate
    /// @param name        the normalized variable name
    /// @param value       the assigned value
    private static void assignGlobal(Environment environment, String name, SassValue value) {
        environment.setVariable(name, value, origin(name), null, true);
    }

    /// Creates a complete synthetic source span for a variable binding.
    ///
    /// @param name the normalized variable name
    /// @return a source span containing the variable name
    private static SourceSpan origin(String name) {
        var source = new SourceFile(name, null);
        return source.span(0, name.length());
    }
}
