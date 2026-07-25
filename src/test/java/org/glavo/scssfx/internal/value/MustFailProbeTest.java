package org.glavo.scssfx.internal.value;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MustFailProbeTest {
    private final SassCompiler c = new SassCompiler();

    private String err(String scss) {
        return assertThrows(SassCompilationException.class, () ->
                c.compile(SassSource.fromString(scss, Syntax.SCSS), CssTarget.DEFAULT)
        ).primaryDiagnostic().message();
    }

    private String css(String scss) throws Exception {
        return c.compile(SassSource.fromString(scss, Syntax.SCSS), CssTarget.DEFAULT)
                .output()
                .replace("\r\n", "\n");
    }

    @Test
    void lchHueRejectsPx() {
        var msg = err("a { b: lch(1% 2 3px); }");
        assertTrue(msg.contains("$hue"), msg);
        assertTrue(msg.contains("angle"), msg);
    }

    @Test
    void lchHueRejectsPercent() {
        var msg = err("a { b: lch(1% 2 3%); }");
        assertTrue(msg.contains("$hue"), msg);
    }

    @Test
    void functionNameRejectsExpression() {
        var msg = err("@function expression() { @return 1; }");
        assertTrue(msg.contains("Invalid function name"), msg);
    }

    @Test
    void functionNameRejectsType() {
        var msg = err("@function type() { @return 1; }");
        assertTrue(msg.toLowerCase().contains("reserved") || msg.contains("plain-CSS"), msg);
    }

    @Test
    void mediaRangeRejectsTripleComparison() {
        var msg = err("@media (1 < width < 2 < 3) {a {b: c}}");
        assertTrue(msg.toLowerCase().contains("expected \")\""), msg);
    }

    @Test
    void mediaRangeRejectsMismatchedOperators() {
        var msg = err("@media (1px > width < 2px) {a {b: c}}");
        assertTrue(msg.toLowerCase().contains("expected \")\""), msg);
    }

    @Test
    void mediaRangeRejectsSpacedLte() {
        var msg = err("@media (width < = 100px) {a {b: c}}");
        assertTrue(msg.contains("Expected expression"), msg);
    }

    @Test
    void mediaRangeEvaluatesExpressions() throws Exception {
        var out = css("""
                $width: width;
                @media ($width < 500px + 100px) {a {dynamic: both}}
                """);
        assertTrue(out.contains("@media (width < 600px)"), out);
        assertTrue(out.contains("dynamic: both"), out);
    }

    @Test
    void mediaRangeStaticPasses() throws Exception {
        var out = css("@media (10px < width < 15px) {a {b: c}}");
        assertTrue(out.contains("@media (10px < width < 15px)"), out);
        assertTrue(out.contains("b: c"), out);
    }
}
