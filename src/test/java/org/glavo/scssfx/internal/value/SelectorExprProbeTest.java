package org.glavo.scssfx.internal.value;

import org.glavo.scssfx.CssTarget;
import org.glavo.scssfx.SassCompilationException;
import org.glavo.scssfx.SassCompiler;
import org.glavo.scssfx.SassSource;
import org.glavo.scssfx.Syntax;
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
    void parentOutsideStyleRuleFails() {
        assertThrows(SassCompilationException.class, () ->
                css("$x: &;")
        );
    }
}
