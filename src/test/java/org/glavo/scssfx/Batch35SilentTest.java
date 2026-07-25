package org.glavo.scssfx;
import org.glavo.scssfx.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Batch35SilentTest {
  @Test void silentCommentsMatchDartSass() throws Exception {
    assertTrue(compile("@supports a(//\n  b) {c {d: e}}").contains("a(\n  b)"));
    assertTrue(compile("@supports (a //\n  b) {c {d: e}}").contains("(a \n  b)"));
    assertEquals("@a b;", compile("@a b //").strip());
    assertEquals("@a b {}", compile("@a b //\n  {}").strip());
    assertTrue(compile("@-moz-document url-prefix(a) //\n  {}").contains("@-moz-document url-prefix(a) {}"));
    assertTrue(
            compile("@supports (--a: //\n  b) {c {d: e}}").contains("(--a:  b)"),
            "custom-property supports silent comment folds to spaces"
    );
    assertTrue(
            compile("@supports (--a: b //\n  ) {c {d: e}}").contains("(--a: b  )"),
            "custom-property supports trailing silent comment folds to spaces"
    );
    assertTrue(
            compile("a { b: element(//\n  c); }").contains("element( c)"),
            "special-function silent comments fold in property values"
    );
    assertTrue(
            compile("a { b: -a-calc(//\n  c); }").contains("-a-calc( c)"),
            "vendored calc silent comments fold in property values"
    );
  }
  static String compile(String s) throws Exception {
    return new SassCompiler().compile(SassSource.fromString(s, Syntax.SCSS), CssTarget.DEFAULT).output().replace("\r\n","\n");
  }
}
