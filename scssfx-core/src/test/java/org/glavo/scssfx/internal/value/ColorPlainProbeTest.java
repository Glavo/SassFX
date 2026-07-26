package org.glavo.scssfx.internal.value;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ColorPlainProbeTest {
    private final SassCompiler c = new SassCompiler();

    private String css(String scss) throws Exception {
        return c.compile(SassSource.fromString(scss, Syntax.SCSS), CssTarget.DEFAULT)
                .output().replace("\r\n", "\n").trim();
    }

    private String err(String scss) {
        return assertThrows(SassCompilationException.class, () ->
                c.compile(SassSource.fromString(scss, Syntax.SCSS), CssTarget.DEFAULT)
        ).primaryDiagnostic().message();
    }

    private String plainErr(String plainCss) {
        return assertThrows(SassCompilationException.class, () ->
                c.compile(SassSource.fromString(plainCss, Syntax.CSS), CssTarget.DEFAULT)
        ).primaryDiagnostic().message();
    }

    private String plainCss(String plainCss) throws Exception {
        return c.compile(SassSource.fromString(plainCss, Syntax.CSS), CssTarget.DEFAULT)
                .output().replace("\r\n", "\n").trim();
    }

    @Test
    void lab_above() throws Exception {
        var out = css("@use 'sass:meta'; a { b: lab(101 2 3); c: meta.inspect(lab(101 2 3)); }");
        assertTrue(out.contains("lab(100%"), out);
    }

    @Test
    void lab_percent_above() throws Exception {
        var out = css("a { b: lab(110% 2 3); }");
        assertTrue(out.contains("lab(100%"), out);
    }

    @Test
    void lab_below() throws Exception {
        var out = css("a { b: lab(-1 2 3); }");
        assertTrue(out.contains("lab(0%"), out);
    }

    @Test
    void alpha_slash_type() {
        var msg = err("a { b: lab(1 2 3 / c); }");
        assertTrue(msg.contains("$channels"), msg);
        assertTrue(msg.contains("is not a number"), msg);
    }

    @Test
    void plain_parent_selector_message() {
        var msg = plainErr("a { x: &; }");
        assertTrue(msg.contains("parent selector"), msg.toLowerCase());
    }

    @Test
    void plain_boolean_ops() throws Exception {
        var out = plainCss("a { and: true and false; or: true or false; not: not true; }");
        assertTrue(out.contains("true and false"), out);
        assertTrue(out.contains("true or false"), out);
        assertTrue(out.contains("not true"), out);
    }

    @Test
    void plain_null() throws Exception {
        var out = plainCss("a { x: null; }");
        assertTrue(out.contains("null"), out);
    }

    @Test
    void plain_calc_simplified() throws Exception {
        var out = plainCss("a { b: calc(1px); }");
        assertTrue(out.contains("1px"), out);
        assertTrue(!out.contains("calc("), out);
    }

    @Test
    void plain_nested_decl_value() {
        var msg = plainErr("a { b: c { d: e; } }");
        assertTrue(msg.contains("Nested declarations"), msg);
    }

    @Test
    void plain_leading_combinator() {
        var msg = plainErr("> a { b: c; }");
        assertTrue(msg.contains("leading combinator"), msg.toLowerCase());
    }

    @Test
    void plain_parent_suffix() {
        var msg = plainErr("a {&b {c: d}}");
        assertTrue(msg.contains("suffix"), msg.toLowerCase());
    }

    @Test
    void math_number_prefix() {
        var msg = err("@use 'sass:math'; a { b: math.ceil(c); }");
        assertTrue(msg.contains("$number"), msg);
    }
}
