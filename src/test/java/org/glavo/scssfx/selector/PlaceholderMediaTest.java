package org.glavo.scssfx.selector;

import org.glavo.scssfx.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
@org.jetbrains.annotations.NotNullByDefault
final class PlaceholderMediaTest {
  @Test
  void placeholderWithNestedMediaExtendsFromOutside() throws Exception {
    var css = new SassCompiler().compile(
      SassSource.fromString("""
        %foo {
          @media screen and (min-width: 300px) {
            max-width: 80%;
          }
        }
        bar {
          @extend %foo;
        }
        """, Syntax.SCSS),
      CssTarget.DEFAULT
    ).output().replace("\r\n","\n");
    assertTrue(css.contains("@media"), css);
    assertTrue(css.contains("bar"), css);
    assertTrue(css.contains("max-width: 80%"), css);
  }
}
