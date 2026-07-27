package org.glavo.sassfx.internal.value;

import org.glavo.sassfx.CssTarget;
import org.glavo.sassfx.SassCompilationException;
import org.glavo.sassfx.SassCompiler;
import org.glavo.sassfx.SassSource;
import org.glavo.sassfx.Syntax;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SelectorExprProbeTest {
    private final SassCompiler c = new SassCompiler();

    private String css(String scss) throws Exception {
        return c.compile(SassSource.fromString(scss, Syntax.SCSS), CssTarget.DEFAULT)
                .output().replace("\r\n", "\n").trim();
    }

    @Test
    void parentSelectorValueInStyleRule() throws Exception {
        var out = css(".foo { content: #{&}; }");
        assertTrue(out.contains(".foo"), out);
    }

    @Test
    void parentSelectorInMixin() throws Exception {
        var out = css("""
                @mixin m { sees: &; }
                .bar { @include m; }
                """);
        assertTrue(out.contains("sees: .bar"), out);
    }

    @Test
    void parentSelectorComparison() throws Exception {
        var out = css("""
                @mixin where($sel: null) {
                  @if (& == $sel) {
                    h1 { color: white; }
                  } @else {
                    h1 { color: blue; }
                  }
                }
                .bee { @include where(&); }
                .hive { @include where(); }
                """);
        assertTrue(out.contains("color: white"), out);
        assertTrue(out.contains("color: blue"), out);
    }

    @Test
    void parentOutsideStyleRuleIsNull() throws Exception {
        var out = css("""
                @if & {
                  .x { a: b; }
                }
                .y { c: d; }
                """);
        assertTrue(out.contains(".y"), out);
        assertTrue(!out.contains(".x"), out);
    }

    @Test
    void parentInterpolationAtRootFailsSelector() {
        var ex = assertThrows(SassCompilationException.class, () ->
                css("#{&} { a: b; }")
        );
        assertTrue(
                ex.primaryDiagnostic().message().toLowerCase().contains("selector")
                        || ex.primaryDiagnostic().message().contains("expected"),
                ex.primaryDiagnostic().message()
        );
    }
}
