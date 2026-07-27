package org.glavo.sassfx.parser;

import org.glavo.sassfx.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class IndentedLoudCommentTest {
    @Test
    void openInline() throws Exception {
        var r = new SassCompiler().compile(
            SassSource.fromString("/* a\n", Syntax.SASS),
            CssTarget.DEFAULT, new CompileOptions(false, List.of()));
        System.out.println("openInline=[" + r.output() + "]");
        assertEquals("/* a */", r.output().trim());
    }
    @Test
    void indentedClosed() throws Exception {
        var r = new SassCompiler().compile(
            SassSource.fromString("/* \n  a */\n", Syntax.SASS),
            CssTarget.DEFAULT, new CompileOptions(false, List.of()));
        System.out.println("indentedClosed=[" + r.output() + "]");
    }
    @Test
    void indentedOpen() throws Exception {
        var r = new SassCompiler().compile(
            SassSource.fromString("/* \n  a\n", Syntax.SASS),
            CssTarget.DEFAULT, new CompileOptions(false, List.of()));
        System.out.println("indentedOpen=[" + r.output() + "]");
    }
}
