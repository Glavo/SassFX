// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies JavaFX BSS output without loading a JavaFX runtime.
@NotNullByDefault
final class BssTargetTest {
    /// Contains one SCSS stylesheet representable by the supported BSS subset.
    private static final String SUPPORTED_SOURCE = """
            Pane .button:hover > #primary, Label.item {
              -fx-opacity: 0.5 !important;
              -fx-text-fill: #f00;
              -fx-font-size: 12px;
              -fx-font-family: "System";
              -fx-padding: 1px 2px 3px 4px;
              -fx-disable: true;
            }
            """;

    /// Contains JavaFX font-face descriptors and all persisted source forms.
    private static final String FONT_FACE_SOURCE = """
            @font-face {
              font-family: "Example";
              font-weight: 600;
              src: url("https://example.invalid/fonts/example.woff2") format("woff2"), local("Example Local"), ExampleReference;
            }
            Pane {
              -fx-font-family: "Example";
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#FONT_FACE_SOURCE].
    private static final String FONT_FACE_JAVA_FX_17_FIXTURE = "AAYAEQAOAAZBVVRIT1IABFBhbmUAAAAPLWZ4LWZvbnQtZmFtaWx5ACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACSJFeGFtcGxlIgALZm9udC13ZWlnaHQAAzYwMAALZm9udC1mYW1pbHkAA1VSTAAraHR0cHM6Ly9leGFtcGxlLmludmFsaWQvZm9udHMvZXhhbXBsZS53b2ZmMgAFd29mZjIABUxPQ0FMAA1FeGFtcGxlIExvY2FsAAlSRUZFUkVOQ0UAEEV4YW1wbGVSZWZlcmVuY2UAAAABAAEBAAEAAAACAAAAAAAMAAEAAwABAAQEAAUAAAEAAgAAAAYAAAAHAAAACAAAAAUAAwAAAAkAAAAKAAAACwAAAAwAAAANAAAADgAAAA8AAAAQAAAADg==";

    /// Contains the JavaFX 17 BSS v6 fixture for [#SUPPORTED_SOURCE].
    private static final String JAVA_FX_17_FIXTURE = "AAYAF///AAZBVVRIT1IABFBhbmUAAAABKgAGYnV0dG9uAAVob3ZlcgAHcHJpbWFyeQAFTGFiZWwABGl0ZW0ACy1meC1vcGFjaXR5ACJqYXZhZnguY3NzLmNvbnZlcnRlci5TaXplQ29udmVydGVyAAJQWAANLWZ4LXRleHQtZmlsbAANLWZ4LWZvbnQtc2l6ZQA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250U2l6ZUNvbnZlcnRlcgAPLWZ4LWZvbnQtZmFtaWx5ACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACCJTeXN0ZW0iAAstZngtcGFkZGluZwAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAstZngtZGlzYWJsZQAlamF2YWZ4LmNzcy5jb252ZXJ0ZXIuQm9vbGVhbkNvbnZlcnRlcgAEdHJ1ZQAAAAEAAgIAAwEAAQAAAAIAAAEAAwABAAQAAgABAAUBAAMAAAAGAAAAAgEAAQAHAAEACAACAAAAAACrAAYACQABAAoBAAAJP+AAAAAAAAAACwEADAAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAAANAAEADgEAAAlAKAAAAAAAAAALAAAPAAEAEAQAEQAAEgABABMCAQAAAAQBAAAJP/AAAAAAAAAACwEAAAlAAAAAAAAAAAALAQAACUAIAAAAAAAAAAsBAAAJQBAAAAAAAAAACwAAFAABABUEABYAAAA=";

    /// Contains the JavaFX 27 BSS v9 fixture for [#SUPPORTED_SOURCE].
    private static final String JAVA_FX_27_FIXTURE = "AAkAF///AAZBVVRIT1IABFBhbmUAAAABKgAGYnV0dG9uAAVob3ZlcgAHcHJpbWFyeQAFTGFiZWwABGl0ZW0ACy1meC1vcGFjaXR5ACJqYXZhZnguY3NzLmNvbnZlcnRlci5TaXplQ29udmVydGVyAAJQWAANLWZ4LXRleHQtZmlsbAANLWZ4LWZvbnQtc2l6ZQA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250U2l6ZUNvbnZlcnRlcgAPLWZ4LWZvbnQtZmFtaWx5ACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACCJTeXN0ZW0iAAstZngtcGFkZGluZwAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAstZngtZGlzYWJsZQAlamF2YWZ4LmNzcy5jb252ZXJ0ZXIuQm9vbGVhbkNvbnZlcnRlcgAEdHJ1ZQAAAAAAAAABAAACAgADAQABAAAAAgAAAQADAAEABAACAAEABQEAAwAAAAYAAAACAQABAAcAAQAIAAIAAAAAAKsABgAJAAEACgEAAAk/4AAAAAAAAAALAQAMAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAAAAA0AAQAOAQAACUAoAAAAAAAAAAsAAA8AAQAQBAARAAASAAEAEwIBAAAABAEAAAk/8AAAAAAAAAALAQAACUAAAAAAAAAAAAsBAAAJQAgAAAAAAAAACwEAAAlAEAAAAAAAAAALAAAUAAEAFQQAFgAAAA==";

    /// Contains scalar JavaFX properties with specialized BSS encodings.
    private static final String TYPED_SCALAR_SOURCE = """
            Pane {
              -fx-cursor: hand;
              -fx-blend-mode: multiply;
              -fx-shape: "M 0 0 L 1 1 Z";
              -fx-font-weight: bold;
              -fx-font-style: italic;
              -fx-font-smoothing-type: gray;
              -fx-stroke-line-cap: round;
              -fx-stroke-line-join: bevel;
              -fx-stroke-type: inside;
              -fx-stroke-dash-array: 1px 2px;
              -fx-alignment: center;
              -fx-text-alignment: center;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#TYPED_SCALAR_SOURCE].
    private static final String TYPED_SCALAR_JAVA_FX_17_FIXTURE = "AAYAIf//AAZBVVRIT1IABFBhbmUAAAAKLWZ4LWN1cnNvcgAEaGFuZAAOLWZ4LWJsZW5kLW1vZGUACG11bHRpcGx5AAktZngtc2hhcGUADU0gMCAwIEwgMSAxIFoADy1meC1mb250LXdlaWdodAA2amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250V2VpZ2h0Q29udmVydGVyAARCT0xEAA4tZngtZm9udC1zdHlsZQA1amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250U3R5bGVDb252ZXJ0ZXIABklUQUxJQwAXLWZ4LWZvbnQtc21vb3RoaW5nLXR5cGUABGdyYXkAEy1meC1zdHJva2UtbGluZS1jYXAAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAIGphdmFmeC5zY2VuZS5zaGFwZS5TdHJva2VMaW5lQ2FwAAVyb3VuZAAULWZ4LXN0cm9rZS1saW5lLWpvaW4AIWphdmFmeC5zY2VuZS5zaGFwZS5TdHJva2VMaW5lSm9pbgAFYmV2ZWwADy1meC1zdHJva2UtdHlwZQAdamF2YWZ4LnNjZW5lLnNoYXBlLlN0cm9rZVR5cGUABmluc2lkZQAVLWZ4LXN0cm9rZS1kYXNoLWFycmF5ADRqYXZhZnguY3NzLmNvbnZlcnRlci5TaXplQ29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyAAJQWAANLWZ4LWFsaWdubWVudAAGY2VudGVyABItZngtdGV4dC1hbGlnbm1lbnQAAAABAAEBAAEAAAACAAAAAACTAAwAAwEABAAEAAAFAQAEAAYAAAcAAAQACAAACQABAAoEAAsAAAwAAQANBAAOAAAPAAAEABAAABEAAQASABMEABQAABUAAQASABYEABcAABgAAQASABkEABoAABsAAQAcAgEAAAACAQAACT/wAAAAAAAAAB0BAAAJQAAAAAAAAAAAHQAAHgEABAAfAAAgAQAEAB8AAAA=";

    /// Contains JavaFX font weight and posture values requiring BSS normalization.
    private static final String FONT_VARIANT_SOURCE = """
            Pane {
              -fx-font-weight: 100;
              -fx-font-weight: 200;
              -fx-font-weight: 300;
              -fx-font-weight: 400;
              -fx-font-weight: 500;
              -fx-font-weight: 600;
              -fx-font-weight: 700;
              -fx-font-weight: 800;
              -fx-font-weight: 900;
              -fx-font-weight: bolder;
              -fx-font-weight: lighter;
              -fx-font-style: normal;
              -fx-font-style: italic;
              -fx-font-style: oblique;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#FONT_VARIANT_SOURCE].
    private static final String FONT_VARIANT_JAVA_FX_17_FIXTURE = "AAYAEv//AAZBVVRIT1IABFBhbmUAAAAPLWZ4LWZvbnQtd2VpZ2h0ADZqYXZhZnguY3NzLmNvbnZlcnRlci5Gb250Q29udmVydGVyJEZvbnRXZWlnaHRDb252ZXJ0ZXIABFRISU4AC0VYVFJBX0xJR0hUAAVMSUdIVAAGTk9STUFMAAZNRURJVU0ACVNFTUlfQk9MRAAEQk9MRAAKRVhUUkFfQk9MRAAFQkxBQ0sADi1meC1mb250LXN0eWxlADVqYXZhZnguY3NzLmNvbnZlcnRlci5Gb250Q29udmVydGVyJEZvbnRTdHlsZUNvbnZlcnRlcgAHUkVHVUxBUgAGSVRBTElDAAAAAQABAQABAAAAAgAAAAAAjgAOAAMAAQAEBAAFAAADAAEABAQABgAAAwABAAQEAAcAAAMAAQAEBAAIAAADAAEABAQACQAAAwABAAQEAAoAAAMAAQAEBAALAAADAAEABAQADAAAAwABAAQEAA0AAAMAAQAEBAALAAADAAEABAQABwAADgABAA8EABAAAA4AAQAPBAARAAAOAAEADwQAEQAAAA==";

    /// Contains layered solid backgrounds and matching border geometry.
    private static final String BACKGROUND_AND_BORDER_GEOMETRY_SOURCE = """
            Pane {
              -fx-background-color: #123456, red;
              -fx-background-insets: 1px 2px, 3px;
              -fx-background-radius: 4px 5px / 6px 7px, 8%;
              -fx-opaque-insets: 9px 10px 11px 12px;
              -fx-border-insets: 13px 14px, 15px;
              -fx-border-radius: 16px 17px / 18px 19px, 20%;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BACKGROUND_AND_BORDER_GEOMETRY_SOURCE].
    private static final String BACKGROUND_AND_BORDER_GEOMETRY_JAVA_FX_17_FIXTURE = "AAYAD///AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyABUtZngtYmFja2dyb3VuZC1pbnNldHMANmphdmFmeC5jc3MuY29udmVydGVyLkluc2V0c0NvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAJQWAAVLWZ4LWJhY2tncm91bmQtcmFkaXVzADdjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkNvcm5lclJhZGlpQ29udmVydGVyAAdQRVJDRU5UABEtZngtb3BhcXVlLWluc2V0cwARLWZ4LWJvcmRlci1pbnNldHMAES1meC1ib3JkZXItcmFkaXVzAAAAAQABAQABAAAAAgAAAAAD6AAGAAMAAQAEAgEAAAACAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAAAFAAEABgIBAAAAAgEAAQAHAgEAAAAEAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAk/8AAAAAAAAAAIAQAACUAAAAAAAAAAAAgBAAEABwIBAAAABAEAAAlACAAAAAAAAAAIAQAACUAIAAAAAAAAAAgBAAAJQAgAAAAAAAAACAEAAAlACAAAAAAAAAAIAAAJAAEACgIBAAAAAgEAAAMBAAAAAgEAAAAEAQAACUAQAAAAAAAAAAgBAAAJQBQAAAAAAAAACAEAAAlAEAAAAAAAAAAIAQAACUAUAAAAAAAAAAgBAAAABAEAAAlAGAAAAAAAAAAIAQAACUAcAAAAAAAAAAgBAAAJQBgAAAAAAAAACAEAAAlAHAAAAAAAAAAIAQAAAwEAAAACAQAAAAQBAAAJQCAAAAAAAAAACwEAAAlAIAAAAAAAAAALAQAACUAgAAAAAAAAAAsBAAAJQCAAAAAAAAAACwEAAAAEAQAACUAgAAAAAAAAAAsBAAAJQCAAAAAAAAAACwEAAAlAIAAAAAAAAAALAQAACUAgAAAAAAAAAAsAAAwAAQAHAgEAAAAEAQAACUAiAAAAAAAAAAgBAAAJQCQAAAAAAAAACAEAAAlAJgAAAAAAAAAIAQAACUAoAAAAAAAAAAgAAA0AAQAGAgEAAAACAQABAAcCAQAAAAQBAAAJQCoAAAAAAAAACAEAAAlALAAAAAAAAAAIAQAACUAqAAAAAAAAAAgBAAAJQCwAAAAAAAAACAEAAQAHAgEAAAAEAQAACUAuAAAAAAAAAAgBAAAJQC4AAAAAAAAACAEAAAlALgAAAAAAAAAIAQAACUAuAAAAAAAAAAgAAA4AAQAKAgEAAAACAQAAAwEAAAACAQAAAAQBAAAJQDAAAAAAAAAACAEAAAlAMQAAAAAAAAAIAQAACUAwAAAAAAAAAAgBAAAJQDEAAAAAAAAACAEAAAAEAQAACUAyAAAAAAAAAAgBAAAJQDMAAAAAAAAACAEAAAlAMgAAAAAAAAAIAQAACUAzAAAAAAAAAAgBAAADAQAAAAIBAAAABAEAAAlANAAAAAAAAAALAQAACUA0AAAAAAAAAAsBAAAJQDQAAAAAAAAACwEAAAlANAAAAAAAAAALAQAAAAQBAAAJQDQAAAAAAAAACwEAAAlANAAAAAAAAAALAQAACUA0AAAAAAAAAAsBAAAJQDQAAAAAAAAACwAAAA==";

    /// Contains one semi-transparent solid background paint.
    private static final String SEMI_TRANSPARENT_BACKGROUND_SOURCE = """
            Pane {
              -fx-background-color: rgba(18, 52, 86, 0.3);
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#SEMI_TRANSPARENT_BACKGROUND_SOURCE].
    private static final String SEMI_TRANSPARENT_BACKGROUND_JAVA_FX_17_FIXTURE = "AAYABf//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyAAAAAQABAQABAAAAAgAAAAAAMwABAAMAAQAEAgEAAAABAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/TMzNAAAAAAAAA";

    /// Contains layered solid border paints and border widths.
    private static final String BORDER_STROKE_SOURCE = """
            Pane {
              -fx-border-color: #123456 red green blue, #abcdef;
              -fx-border-width: 1px 2px 3px 4px, 5%;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BORDER_STROKE_SOURCE].
    private static final String BORDER_STROKE_JAVA_FX_17_FIXTURE = "AAYAC///AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1jb2xvcgA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5MYXllcmVkQm9yZGVyUGFpbnRDb252ZXJ0ZXIAPWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uU3Ryb2tlQm9yZGVyUGFpbnRDb252ZXJ0ZXIAEC1meC1ib3JkZXItd2lkdGgAPGNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTWFyZ2lucyRTZXF1ZW5jZUNvbnZlcnRlcgA0Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5NYXJnaW5zJENvbnZlcnRlcgACUFgAB1BFUkNFTlQAAAABAAEBAAEAAAACAAAAAAHYAAIAAwABAAQCAQAAAAIBAAEABQIBAAAABAEAAAU/shISIAAAAD/KGhogAAAAP9WVlaAAAAA/8AAAAAAAAAEAAAU/8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAAEAAAUAAAAAAAAAAD/gEBAgAAAAAAAAAAAAAAA/8AAAAAAAAAEAAAUAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAA/8AAAAAAAAAEAAQAFAgEAAAAEAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAAAGAAEABwIBAAAAAgEAAQAIAgEAAAAEAQAACT/wAAAAAAAAAAkBAAAJQAAAAAAAAAAACQEAAAlACAAAAAAAAAAJAQAACUAQAAAAAAAAAAkBAAEACAIBAAAABAEAAAlAFAAAAAAAAAAKAQAACUAUAAAAAAAAAAoBAAAJQBQAAAAAAAAACgEAAAlAFAAAAAAAAAAKAAAA";

    /// Contains layered background and border image source URLs.
    private static final String IMAGE_SOURCE = """
            Pane {
              -fx-background-image: url("image.png"), url(second.png);
              -fx-border-image-source: url("border.png");
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#IMAGE_SOURCE] without a stylesheet base URL.
    private static final String IMAGE_JAVA_FX_17_FIXTURE = "AAYAC///AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtaW1hZ2UAM2phdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAhamF2YWZ4LmNzcy5jb252ZXJ0ZXIuVVJMQ29udmVydGVyACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACWltYWdlLnBuZwAKc2Vjb25kLnBuZwAXLWZ4LWJvcmRlci1pbWFnZS1zb3VyY2UACmJvcmRlci5wbmcAAAABAAEBAAEAAAACAAAAAABYAAIAAwABAAQCAQAAAAIBAAEABQIBAAAAAgEAAQAGBAAHAAEAAQAFAgEAAAACAQABAAYEAAgAAAAJAAEABAIBAAAAAQEAAQAFAgEAAAACAQABAAYEAAoAAAAA";

    /// Contains the canonical source URL used for [#IMAGE_SOURCE]'s JavaFX resolution base.
    private static final URI IMAGE_SOURCE_URL = URI.create("https://example.invalid/assets/theme.scss");

    /// Contains the JavaFX 17 BSS v6 fixture for [#IMAGE_SOURCE] with [#IMAGE_SOURCE_URL].
    private static final String IMAGE_WITH_BASE_JAVA_FX_17_FIXTURE = "AAYADP//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtaW1hZ2UAM2phdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAhamF2YWZ4LmNzcy5jb252ZXJ0ZXIuVVJMQ29udmVydGVyACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACWltYWdlLnBuZwApaHR0cHM6Ly9leGFtcGxlLmludmFsaWQvYXNzZXRzL3RoZW1lLnNjc3MACnNlY29uZC5wbmcAFy1meC1ib3JkZXItaW1hZ2Utc291cmNlAApib3JkZXIucG5nAAAAAQABAQABAAAAAgAAAAAAZwACAAMAAQAEAgEAAAACAQABAAUCAQAAAAIBAAEABgQABwEAAAQACAEAAQAFAgEAAAACAQABAAYEAAkBAAAEAAgAAAoAAQAEAgEAAAABAQABAAUCAQAAAAIBAAEABgQACwEAAAQACAAAAA==";

    /// Contains JavaFX linear and radial paint layers for BSS serialization.
    private static final String GRADIENT_PAINT_SOURCE = """
            Pane {
              -fx-background-color: linear-gradient(to right bottom, reflect, red, #123456 25%, rgba(18, 52, 86, 0.3) 75%, blue), radial-gradient(focus-angle 45deg, focus-distance 20%, center 30% 40%, radius 50%, repeat, red 0%, green 50%, blue 100%);
              -fx-border-color: linear-gradient(from 0% 0% to 100% 100%, repeat, red 0%, blue 100%) green radial-gradient(radius 20px, red, blue) #123456, red;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#GRADIENT_PAINT_SOURCE].
    private static final String GRADIENT_PAINT_JAVA_FX_17_FIXTURE = "AAYAE///AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyADtqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRMaW5lYXJHcmFkaWVudENvbnZlcnRlcgAHUEVSQ0VOVAAiamF2YWZ4LmNzcy5jb252ZXJ0ZXIuRW51bUNvbnZlcnRlcgAeamF2YWZ4LnNjZW5lLnBhaW50LkN5Y2xlTWV0aG9kAAdSRUZMRUNUACJqYXZhZnguY3NzLmNvbnZlcnRlci5TdG9wQ29udmVydGVyADtqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRSYWRpYWxHcmFkaWVudENvbnZlcnRlcgADREVHAAZSRVBFQVQAEC1meC1ib3JkZXItY29sb3IAPmNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJvcmRlclBhaW50Q29udmVydGVyAD1jb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLlN0cm9rZUJvcmRlclBhaW50Q29udmVydGVyAAJQWAAITk9fQ1lDTEUAAAABAAEBAAEAAAACAAAAAATFAAIAAwABAAQCAQAAAAIBAAEABQIBAAAACQEAAAkAAAAAAAAAAAAGAQAACQAAAAAAAAAAAAYBAAAJQFkAAAAAAAAABgEAAAlAWQAAAAAAAAAGAQABAAcACAQACQEAAQAKAgEAAAACAQAACQAAAAAAAAAAAAYBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAEACgIBAAAAAgEAAAlAOQAAAAAAAAAGAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAQABAAoCAQAAAAIBAAAJQFLAAAAAAAAABgEAAAU/shISIAAAAD/KGhogAAAAP9WVlaAAAAA/0zMzQAAAAAEAAQAKAgEAAAACAQAACUBZAAAAAAAAAAYBAAAFAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAP/AAAAAAAAABAAEACwIBAAAACQEAAAlARoAAAAAAAAAMAQAACUA0AAAAAAAAAAYBAAAJQD4AAAAAAAAABgEAAAlARAAAAAAAAAAGAQAACUBJAAAAAAAAAAYBAAEABwAIBAANAQABAAoCAQAAAAIBAAAJAAAAAAAAAAAABgEAAAU/8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAAEAAQAKAgEAAAACAQAACUBJAAAAAAAAAAYBAAAFAAAAAAAAAAA/4BAQIAAAAAAAAAAAAAAAP/AAAAAAAAABAAEACgIBAAAAAgEAAAlAWQAAAAAAAAAGAQAABQAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAD/wAAAAAAAAAAAOAAEADwIBAAAAAgEAAQAQAgEAAAAEAQABAAUCAQAAAAcBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACUBZAAAAAAAAAAYBAAAJQFkAAAAAAAAABgEAAQAHAAgEAA0BAAEACgIBAAAAAgEAAAkAAAAAAAAAAAAGAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQABAAoCAQAAAAIBAAAJQFkAAAAAAAAABgEAAAUAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAA/8AAAAAAAAAEAAAUAAAAAAAAAAD/gEBAgAAAAAAAAAAAAAAA/8AAAAAAAAAEAAQALAgEAAAAIAAAAAAEAAAlANAAAAAAAAAARAQABAAcACAQAEgEAAQAKAgEAAAACAQAACQAAAAAAAAAAAAYBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAEACgIBAAAAAgEAAAlAWQAAAAAAAAAGAQAABQAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAD/wAAAAAAAAAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAQABABACAQAAAAQBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAAAAAA=";

    /// Contains layered JavaFX border styles with segments and stroke options.
    private static final String BORDER_STYLE_SOURCE = """
            Pane {
              -fx-border-style: solid dashed dotted hidden, segments(1px, 2px, 3px) phase 4px outside line-join miter 5px line-cap square, dashed centered line-join bevel line-cap butt;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BORDER_STYLE_SOURCE].
    private static final String BORDER_STYLE_JAVA_FX_17_FIXTURE = "AAYAFP//AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1zdHlsZQA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5MYXllcmVkQm9yZGVyU3R5bGVDb252ZXJ0ZXIARWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uQm9yZGVyU3Ryb2tlU3R5bGVTZXF1ZW5jZUNvbnZlcnRlcgA3Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5Cb3JkZXJTdHlsZUNvbnZlcnRlcgA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuU2l6ZUNvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgACUFgAImphdmFmeC5jc3MuY29udmVydGVyLlNpemVDb252ZXJ0ZXIAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAHWphdmFmeC5zY2VuZS5zaGFwZS5TdHJva2VUeXBlAAdvdXRzaWRlACFqYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlTGluZUpvaW4ABW1pdGVyACBqYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlTGluZUNhcAAGc3F1YXJlAAhjZW50ZXJlZAAFYmV2ZWwABGJ1dHQAAAABAAEBAAEAAAACAAAAAANMAAEAAwABAAQCAQAAAAMBAAEABQIBAAAABAEAAQAGAgEAAAAGAQAAAAAAAAAAAQABAAYCAQAAAAYBAAAAAAAAAAABAAEABgIBAAAABgEAAAAAAAAAAAEAAQAGAgEAAAAGAQAAAAAAAAAAAQABAAUCAQAAAAQBAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABQIBAAAABAEAAQAGAgEAAAAGAQAAAAABAAEACgALBAARAQABAAoADQQAEgABAAEACgAPBAATAQABAAYCAQAAAAYBAAAAAAEAAQAKAAsEABEBAAEACgANBAASAAEAAQAKAA8EABMBAAEABgIBAAAABgEAAAAAAQABAAoACwQAEQEAAQAKAA0EABIAAQABAAoADwQAEwEAAQAGAgEAAAAGAQAAAAABAAEACgALBAARAQABAAoADQQAEgABAAEACgAPBAATAAAA";

    /// Contains a JavaFX border style whose size positions are property lookups.
    private static final String LOOKUP_BORDER_STYLE_SOURCE = """
            Pane {
              -fx-border-style: segments(-fx-dash-a, -fx-dash-b) phase -fx-phase outside line-join miter -fx-miter line-cap round;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_BORDER_STYLE_SOURCE].
    private static final String LOOKUP_BORDER_STYLE_JAVA_FX_17_FIXTURE = "AAYAFP//AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1zdHlsZQA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5MYXllcmVkQm9yZGVyU3R5bGVDb252ZXJ0ZXIARWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uQm9yZGVyU3Ryb2tlU3R5bGVTZXF1ZW5jZUNvbnZlcnRlcgA3Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5Cb3JkZXJTdHlsZUNvbnZlcnRlcgA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuU2l6ZUNvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAKLWZ4LWRhc2gtYQAKLWZ4LWRhc2gtYgAiamF2YWZ4LmNzcy5jb252ZXJ0ZXIuU2l6ZUNvbnZlcnRlcgAJLWZ4LXBoYXNlACJqYXZhZnguY3NzLmNvbnZlcnRlci5FbnVtQ29udmVydGVyAB1qYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlVHlwZQAHb3V0c2lkZQAhamF2YWZ4LnNjZW5lLnNoYXBlLlN0cm9rZUxpbmVKb2luAAVtaXRlcgAJLWZ4LW1pdGVyACBqYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlTGluZUNhcAAFcm91bmQAAAABAAEBAAEAAAACAAAAAAFyAAEAAwABAAQCAQAAAAEBAAEABQIBAAAABAEAAQAGAgEAAAAGAQABAAcCAQAAAAIBAQAEAAgBAQAEAAkBAAEACgEBAAQACwEAAQAMAA0EAA4BAAEADAAPBAAQAQABAAoBAQAEABEBAAEADAASBAATAQABAAYCAQAAAAYBAAEABwIBAAAAAgEBAAQACAEBAAQACQEAAQAKAQEABAALAQABAAwADQQADgEAAQAMAA8EABABAAEACgEBAAQAEQEAAQAMABIEABMBAAEABgIBAAAABgEAAQAHAgEAAAACAQEABAAIAQEABAAJAQABAAoBAQAEAAsBAAEADAANBAAOAQABAAwADwQAEAEAAQAKAQEABAARAQABAAwAEgQAEwEAAQAGAgEAAAAGAQABAAcCAQAAAAIBAQAEAAgBAQAEAAkBAAEACgEBAAQACwEAAQAMAA0EAA4BAAEADAAPBAAQAQABAAoBAQAEABEBAAEADAASBAATAAAA";

    /// Contains JavaFX's standalone {@code none} border-style declaration.
    private static final String NONE_BORDER_STYLE_SOURCE = """
            Pane {
              -fx-border-style: none;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#NONE_BORDER_STYLE_SOURCE].
    private static final String NONE_BORDER_STYLE_JAVA_FX_17_FIXTURE = "AAYABf//AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1zdHlsZQAEbnVsbAAAAAEAAQEAAQAAAAIAAAAAAAoAAQADAAAEAAQAAAA=";

    /// Contains layered JavaFX border-image layout values including lookup and auto widths.
    private static final String BORDER_IMAGE_LAYOUT_SOURCE = """
            Pane {
              -fx-border-image-insets: 1px 2px 3px 4px, -fx-image-inset;
              -fx-border-image-repeat: repeat-x, space round, stretch;
              -fx-border-image-slice: 10% fill, -fx-image-slice fill, 20% 30% 40% 50%;
              -fx-border-image-width: 1px 2% 3 4px, auto -fx-image-width 6px 7%, -fx-other-width;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BORDER_IMAGE_LAYOUT_SOURCE].
    private static final String BORDER_IMAGE_LAYOUT_JAVA_FX_17_FIXTURE = "AAYAG///AAZBVVRIT1IABFBhbmUAAAAXLWZ4LWJvcmRlci1pbWFnZS1pbnNldHMANmphdmFmeC5jc3MuY29udmVydGVyLkluc2V0c0NvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAJQWAAPLWZ4LWltYWdlLWluc2V0ABctZngtYm9yZGVyLWltYWdlLXJlcGVhdAA4Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5SZXBlYXRTdHJ1Y3RDb252ZXJ0ZXIAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAJGphdmFmeC5zY2VuZS5sYXlvdXQuQmFja2dyb3VuZFJlcGVhdAAGUkVQRUFUAAlOT19SRVBFQVQABVNQQUNFAAVST1VORAAWLWZ4LWJvcmRlci1pbWFnZS1zbGljZQA5Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5TbGljZVNlcXVlbmNlQ29udmVydGVyADxjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkJvcmRlckltYWdlU2xpY2VDb252ZXJ0ZXIAB1BFUkNFTlQADy1meC1pbWFnZS1zbGljZQAWLWZ4LWJvcmRlci1pbWFnZS13aWR0aABFY29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5Cb3JkZXJJbWFnZVdpZHRoc1NlcXVlbmNlQ29udmVydGVyADxjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkJvcmRlckltYWdlV2lkdGhDb252ZXJ0ZXIABGF1dG8ADy1meC1pbWFnZS13aWR0aAAPLWZ4LW90aGVyLXdpZHRoAAAAAQABAQABAAAAAgAAAAACWQAEAAMAAQAEAgEAAAACAQABAAUCAQAAAAQBAAAJP/AAAAAAAAAABgEAAAlAAAAAAAAAAAAGAQAACUAIAAAAAAAAAAYBAAAJQBAAAAAAAAAABgEAAQAFAgEAAAAEAQEABAAHAQEABAAHAQEABAAHAQEABAAHAAAIAAEACQMBAAAAAwEAAAACAQABAAoACwQADAEAAQAKAAsEAA0BAAAAAgEAAQAKAAsEAA4BAAEACgALBAAPAQAAAAIBAAEACgALBAANAQABAAoACwQADQAAEAABABECAQAAAAMBAAEAEgIBAAAAAgEAAQAFAgEAAAAEAQAACUAkAAAAAAAAABMBAAAJQCQAAAAAAAAAEwEAAAlAJAAAAAAAAAATAQAACUAkAAAAAAAAABMBAAAHAQEAAQASAgEAAAACAQABAAUCAQAAAAQBAQAEABQBAQAEABQBAQAEABQBAQAEABQBAAAHAQEAAQASAgEAAAACAQABAAUCAQAAAAQBAAAJQDQAAAAAAAAAEwEAAAlAPgAAAAAAAAATAQAACUBEAAAAAAAAABMBAAAJQEkAAAAAAAAAEwEAAAcAAAAVAAEAFgIBAAAAAwEAAQAXAgEAAAAEAQAACT/wAAAAAAAAAAYBAAAJQAAAAAAAAAAAEwEAAAlACAAAAAAAAAAGAQAACUAQAAAAAAAAAAYBAAEAFwIBAAAABAEBAAQAGAEBAAQAGQEAAAlAGAAAAAAAAAAGAQAACUAcAAAAAAAAABMBAAEAFwIBAAAABAEBAAQAGgEBAAQAGgEBAAQAGgEBAAQAGgAAAA==";
    /// Contains JavaFX lookup, image-pattern, and repeating-image-pattern paint layers.
    private static final String LOOKUP_AND_IMAGE_PATTERN_PAINT_SOURCE = """
            Pane {
              -fx-base: #123456;
              -fx-background-color: -fx-base, image-pattern("image.png"), image-pattern("image.png", 1px, 2px, 3px, 4px, false), image-pattern("image.png", 5%, 6%, 7%, 8%, true), repeating-image-pattern("tile.png");
              -fx-border-color: -fx-base image-pattern("border.png", 1px, 2px, 3px, 4px, true) repeating-image-pattern("border-tile.png"), red;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_AND_IMAGE_PATTERN_PAINT_SOURCE].
    private static final String LOOKUP_AND_IMAGE_PATTERN_PAINT_JAVA_FX_17_FIXTURE = "AAYAE///AAZBVVRIT1IABFBhbmUAAAAILWZ4LWJhc2UAFC1meC1iYWNrZ3JvdW5kLWNvbG9yADVqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgA5amF2YWZ4LmNzcy5jb252ZXJ0ZXIuUGFpbnRDb252ZXJ0ZXIkSW1hZ2VQYXR0ZXJuQ29udmVydGVyACFqYXZhZnguY3NzLmNvbnZlcnRlci5VUkxDb252ZXJ0ZXIAJGphdmFmeC5jc3MuY29udmVydGVyLlN0cmluZ0NvbnZlcnRlcgALImltYWdlLnBuZyIAAlBYAAdQRVJDRU5UAEJqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRSZXBlYXRpbmdJbWFnZVBhdHRlcm5Db252ZXJ0ZXIACiJ0aWxlLnBuZyIAEC1meC1ib3JkZXItY29sb3IAPmNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJvcmRlclBhaW50Q29udmVydGVyAD1jb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLlN0cm9rZUJvcmRlclBhaW50Q29udmVydGVyAAwiYm9yZGVyLnBuZyIAESJib3JkZXItdGlsZS5wbmciAAAAAQABAQABAAAAAgAAAAACwQADAAMAAAU/shISIAAAAD/KGhogAAAAP9WVlaAAAAA/8AAAAAAAAAAABAABAAUCAQAAAAUBAQAEAAMBAAEABgIBAAAAAQEAAQAHAgEAAAACAQABAAgEAAkAAQABAAYCAQAAAAYBAAEABwIBAAAAAgEAAQAIBAAJAAEAAAk/8AAAAAAAAAAKAQAACUAAAAAAAAAAAAoBAAAJQAgAAAAAAAAACgEAAAlAEAAAAAAAAAAKAQAABwABAAEABgIBAAAABgEAAQAHAgEAAAACAQABAAgEAAkAAQAACUAUAAAAAAAAAAsBAAAJQBgAAAAAAAAACwEAAAlAHAAAAAAAAAALAQAACUAgAAAAAAAAAAsBAAAHAQEAAQAMAgEAAAABAQABAAcCAQAAAAIBAAEACAQADQAAAA4AAQAPAgEAAAACAQABABACAQAAAAQBAQAEAAMBAAEABgIBAAAABgEAAQAHAgEAAAACAQABAAgEABEAAQAACT/wAAAAAAAAAAoBAAAJQAAAAAAAAAAACgEAAAlACAAAAAAAAAAKAQAACUAQAAAAAAAAAAoBAAAHAQEAAQAMAgEAAAABAQABAAcCAQAAAAIBAAEACAQAEgABAAEABgIBAAAABgEAAQAHAgEAAAACAQABAAgEABEAAQAACT/wAAAAAAAAAAoBAAAJQAAAAAAAAAAACgEAAAlACAAAAAAAAAAKAQAACUAQAAAAAAAAAAoBAAAHAQEAAQAQAgEAAAAEAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAAAA";

    /// Contains one image-pattern with JavaFX lookup-backed geometry values.
    private static final String LOOKUP_IMAGE_PATTERN_SIZE_SOURCE = """
            Pane {
              -fx-background-color: image-pattern("image.png", -fx-pattern-x, -fx-pattern-y, -fx-pattern-width, -fx-pattern-height);
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_IMAGE_PATTERN_SIZE_SOURCE].
    private static final String LOOKUP_IMAGE_PATTERN_SIZE_JAVA_FX_17_FIXTURE = "AAYADf//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyADlqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRJbWFnZVBhdHRlcm5Db252ZXJ0ZXIAIWphdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuU3RyaW5nQ29udmVydGVyAAsiaW1hZ2UucG5nIgANLWZ4LXBhdHRlcm4teAANLWZ4LXBhdHRlcm4teQARLWZ4LXBhdHRlcm4td2lkdGgAEi1meC1wYXR0ZXJuLWhlaWdodAAAAAEAAQEAAQAAAAIAAAAAAEYAAQADAAEABAIBAAAAAQEAAQAFAgEAAAAFAQABAAYCAQAAAAIBAAEABwQACAABAQAEAAkBAQAEAAoBAQAEAAsBAQAEAAwAAAA=";

    /// Contains one image-pattern URI represented with JavaFX {@code url(...)} syntax.
    private static final String URL_IMAGE_PATTERN_SOURCE = """
            Pane {
              -fx-background-color: image-pattern(url("image-two.png"));
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#URL_IMAGE_PATTERN_SOURCE].
    private static final String URL_IMAGE_PATTERN_JAVA_FX_17_FIXTURE = "AAYACf//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyADlqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRJbWFnZVBhdHRlcm5Db252ZXJ0ZXIAIWphdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuU3RyaW5nQ29udmVydGVyAA1pbWFnZS10d28ucG5nAAAAAQABAQABAAAAAgAAAAAALgABAAMAAQAEAgEAAAABAQABAAUCAQAAAAEBAAEABgIBAAAAAgEAAQAHBAAIAAAAAA==";

    /// Contains a JavaFX gradient that resolves one color stop through a property lookup.
    private static final String LOOKUP_GRADIENT_PAINT_SOURCE = """
            Pane {
              -fx-base: #123456;
              -fx-background-color: linear-gradient(-fx-base, red);
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_GRADIENT_PAINT_SOURCE].
    private static final String LOOKUP_GRADIENT_PAINT_JAVA_FX_17_FIXTURE = "AAYADP//AAZBVVRIT1IABFBhbmUAAAAILWZ4LWJhc2UAFC1meC1iYWNrZ3JvdW5kLWNvbG9yADVqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgA7amF2YWZ4LmNzcy5jb252ZXJ0ZXIuUGFpbnRDb252ZXJ0ZXIkTGluZWFyR3JhZGllbnRDb252ZXJ0ZXIAB1BFUkNFTlQAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAHmphdmFmeC5zY2VuZS5wYWludC5DeWNsZU1ldGhvZAAITk9fQ1lDTEUAImphdmFmeC5jc3MuY29udmVydGVyLlN0b3BDb252ZXJ0ZXIAAAABAAEBAAEAAAACAAAAAADeAAIAAwAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAAAEAAEABQIBAAAAAQEAAQAGAgEAAAAHAQAACQAAAAAAAAAAAAcBAAAJAAAAAAAAAAAABwEAAAkAAAAAAAAAAAAHAQAACUBZAAAAAAAAAAcBAAEACAAJBAAKAQABAAsCAQAAAAIBAAAJAAAAAAAAAAAABwEBAAQAAwEAAQALAgEAAAACAQAACUBZAAAAAAAAAAcBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAAAAAA=";
    /// Contains layered background image layout values for JavaFX BSS encoding.
    private static final String BACKGROUND_LAYOUT_SOURCE = """
            Pane {
              -fx-background-position: left 10px top 20%, right 5px bottom 6px, top left 7px, center bottom, 30px 40px;
              -fx-background-repeat: repeat-x, repeat-y, space no-repeat, round, stretch;
              -fx-background-size: 25px auto, auto 30%, cover, contain, stretch;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BACKGROUND_LAYOUT_SOURCE].
    private static final String BACKGROUND_LAYOUT_JAVA_FX_17_FIXTURE = "AAYAFv//AAZBVVRIT1IABFBhbmUAAAAXLWZ4LWJhY2tncm91bmQtcG9zaXRpb24ARWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJhY2tncm91bmRQb3NpdGlvbkNvbnZlcnRlcgA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5CYWNrZ3JvdW5kUG9zaXRpb25Db252ZXJ0ZXIAB1BFUkNFTlQAAlBYABUtZngtYmFja2dyb3VuZC1yZXBlYXQAOGNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uUmVwZWF0U3RydWN0Q29udmVydGVyACJqYXZhZnguY3NzLmNvbnZlcnRlci5FbnVtQ29udmVydGVyACRqYXZhZnguc2NlbmUubGF5b3V0LkJhY2tncm91bmRSZXBlYXQABlJFUEVBVAAJTk9fUkVQRUFUAAVTUEFDRQAFUk9VTkQAEy1meC1iYWNrZ3JvdW5kLXNpemUAQWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJhY2tncm91bmRTaXplQ29udmVydGVyADpjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkJhY2tncm91bmRTaXplQ29udmVydGVyACVqYXZhZnguY3NzLmNvbnZlcnRlci5Cb29sZWFuQ29udmVydGVyAAVmYWxzZQAEdHJ1ZQAAAAEAAQEAAQAAAAIAAAAAAroAAwADAAEABAIBAAAABQEAAQAFAgEAAAAEAQAACUA0AAAAAAAAAAYBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACUAkAAAAAAAAAAcBAAEABQIBAAAABAEAAAkAAAAAAAAAAAAGAQAACUAUAAAAAAAAAAcBAAAJQBgAAAAAAAAABwEAAAkAAAAAAAAAAAAGAQABAAUCAQAAAAQBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACQAAAAAAAAAAAAYBAAAJQBwAAAAAAAAABwEAAQAFAgEAAAAEAQAACUBZAAAAAAAAAAYBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACUBJAAAAAAAAAAYBAAEABQIBAAAABAEAAAlARAAAAAAAAAAHAQAACQAAAAAAAAAAAAYBAAAJAAAAAAAAAAAABgEAAAlAPgAAAAAAAAAHAAAIAAEACQMBAAAABQEAAAACAQABAAoACwQADAEAAQAKAAsEAA0BAAAAAgEAAQAKAAsEAA0BAAEACgALBAAMAQAAAAIBAAEACgALBAAOAQABAAoACwQADQEAAAACAQABAAoACwQADwEAAQAKAAsEAA8BAAAAAgEAAQAKAAsEAA0BAAEACgALBAANAAAQAAEAEQIBAAAABQEAAQASAgEAAAAEAQAACUA5AAAAAAAAAAcAAQABABMEABQBAAEAEwQAFAEAAQASAgEAAAAEAAEAAAlAPgAAAAAAAAAGAQABABMEABQBAAEAEwQAFAEAAQASAgEAAAAEAAABAAEAEwQAFQEAAQATBAAUAQABABICAQAAAAQAAAEAAQATBAAUAQABABMEABUBAAEAEgIBAAAABAEAAAlAWQAAAAAAAAAGAQAACUBZAAAAAAAAAAYBAAEAEwQAFAEAAQATBAAUAAAA";

    /// Serializes JavaFX font-face descriptors and URL, local, and reference sources.
    @Test
    void compilesFontFaceRules() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(FONT_FACE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(FONT_FACE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes an unquoted JavaFX local-font source.
    @Test
    void compilesUnquotedLocalFontFaceSource() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(
                        "@font-face { src: local(Example); } Pane { -fx-opacity: 1; }",
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(
                        "AAYACQAIAAZBVVRIT1IABFBhbmUAAAALLWZ4LW9wYWNpdHkAImphdmFmeC5jc3MuY29udmVydGVyLlNpemVDb252ZXJ0ZXIAAlBYAAVMT0NBTAAHRXhhbXBsZQAAAAEAAQEAAQAAAAIAAAAAABcAAQADAAEABAEAAAk/8AAAAAAAAAAFAAABAAAAAQAAAAYAAAAHAAAACA=="
                ),
                remainingBytes(output)
        );
    }

    /// Serializes selectors and supported values into JavaFX 17 BSS bytes.
    @Test
    void compilesJavaFx17Bss() throws Exception {
        var result = compile(BssTarget.DEFAULT);
        var output = result.output();

        assertTrue(output.isReadOnly());
        assertEquals(0, output.position());
        assertArrayEquals(
                Base64.getDecoder().decode(JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
        assertNull(result.sourceMap());
    }

    /// Serializes the JavaFX 27 import and media-rule framing of BSS v9.
    @Test
    void compilesJavaFx27Bss() throws Exception {
        var target = new BssTarget(JavaFXCompatibility.JAVA_FX_27);
        var output = compile(target).output();

        assertTrue(output.isReadOnly());
        assertEquals(0, output.position());
        assertArrayEquals(
                Base64.getDecoder().decode(JAVA_FX_27_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX scalar converters with the JavaFX 17 BSS wire format.
    @Test
    void compilesTypedScalarConverters() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(TYPED_SCALAR_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(TYPED_SCALAR_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX numeric, relative, and posture font variants.
    @Test
    void compilesNumericAndRelativeFontVariants() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(FONT_VARIANT_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(FONT_VARIANT_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes layered solid backgrounds and border geometry with JavaFX 17 BSS.
    @Test
    void compilesBackgroundAndBorderGeometry() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(BACKGROUND_AND_BORDER_GEOMETRY_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(BACKGROUND_AND_BORDER_GEOMETRY_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes single-precision JavaFX color components for a transparent paint.
    @Test
    void compilesSemiTransparentBackgroundColor() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(SEMI_TRANSPARENT_BACKGROUND_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(SEMI_TRANSPARENT_BACKGROUND_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes layered solid border paints and widths with JavaFX 17 BSS.
    @Test
    void compilesSolidBorderPaintAndWidthLayers() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(BORDER_STROKE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(BORDER_STROKE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes background and border image URL layers with JavaFX's nested URL converters.
    @Test
    void compilesImageSourceUrlLayers() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(IMAGE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(IMAGE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes the canonical Sass source URL as JavaFX's relative-resource resolution base.
    @Test
    void compilesImageSourceUrlLayersWithCanonicalBase() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(IMAGE_SOURCE, Syntax.SCSS, IMAGE_SOURCE_URL),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(IMAGE_WITH_BASE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes an unquoted font family without a generic property lookup.
    @Test
    void compilesUnquotedFontFamily() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(".button { -fx-font-family: System; }", Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(
                        "AAYAB///AAZBVVRIT1IAASoABmJ1dHRvbgAAAA8tZngtZm9udC1mYW1pbHkAJGphdmFmeC5jc3MuY29udmVydGVyLlN0cmluZ0NvbnZlcnRlcgAGU3lzdGVtAAAAAQABAQABAAEAAgADAAAAAAAMAAEABAABAAUEAAYAAAA="
                ),
                remainingBytes(output)
        );
    }

    /// Serializes global keywords before property-specific converters.
    @Test
    void serializesGlobalKeywordsBeforeFontConverters() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(".button { -fx-font-family: none; }", Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(
                        "AAYABv//AAZBVVRIT1IAASoABmJ1dHRvbgAAAA8tZngtZm9udC1mYW1pbHkABG51bGwAAAABAAEBAAEAAQACAAMAAAAAAAoAAQAEAAAEAAUAAAA="
                ),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX linear and radial background and border paint layers.
    @Test
    void compilesGradientPaintLayers() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(GRADIENT_PAINT_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(GRADIENT_PAINT_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes layered JavaFX border styles with segments and stroke options.
    @Test
    void compilesBorderStyleLayers() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(BORDER_STYLE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(BORDER_STYLE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX border-style size values that defer to property lookup.
    @Test
    void compilesLookupBorderStyleValues() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(LOOKUP_BORDER_STYLE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(LOOKUP_BORDER_STYLE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Preserves JavaFX's standalone global {@code none} border-style encoding.
    @Test
    void compilesNoneBorderStyle() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(NONE_BORDER_STYLE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(NONE_BORDER_STYLE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX border-image insets, repeat, slice, and width layers.
    @Test
    void compilesBorderImageLayoutLayers() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(BORDER_IMAGE_LAYOUT_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(BORDER_IMAGE_LAYOUT_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX lookup, image-pattern, and repeating-image-pattern paint layers.
    @Test
    void compilesLookupAndImagePatternPaintLayers() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(LOOKUP_AND_IMAGE_PATTERN_PAINT_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(LOOKUP_AND_IMAGE_PATTERN_PAINT_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Keeps JavaFX image-pattern URI bases empty for a canonical stylesheet source URL.
    @Test
    void imagePatternUrisIgnoreStylesheetBase() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(
                        LOOKUP_AND_IMAGE_PATTERN_PAINT_SOURCE,
                        Syntax.SCSS,
                        IMAGE_SOURCE_URL
                ),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(LOOKUP_AND_IMAGE_PATTERN_PAINT_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }
    /// Serializes JavaFX lookup-backed image-pattern geometry values.
    @Test
    void compilesLookupImagePatternSizeValues() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(LOOKUP_IMAGE_PATTERN_SIZE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(LOOKUP_IMAGE_PATTERN_SIZE_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX image-pattern {@code url(...)} arguments through URL conversion.
    @Test
    void compilesUrlImagePatternValues() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(URL_IMAGE_PATTERN_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(URL_IMAGE_PATTERN_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Serializes JavaFX property lookup color stops inside gradients.
    @Test
    void compilesLookupGradientPaintValues() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(LOOKUP_GRADIENT_PAINT_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(LOOKUP_GRADIENT_PAINT_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }
    /// Serializes layered JavaFX background image layout values with BSS converters.
    @Test
    void compilesBackgroundImageLayoutLayers() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(BACKGROUND_LAYOUT_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(BACKGROUND_LAYOUT_JAVA_FX_17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Rejects a font-face rule placed after a JavaFX BSS style rule.
    @Test
    void rejectsFontFacesAfterStyleRules() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "Pane { -fx-opacity: 1; } @font-face { src: local(Example); }",
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "BSS @font-face rules must precede style rules.",
                failure.getMessage()
        );
    }

    /// Rejects image source values that JavaFX would not parse as URL layers.
    @Test
    void rejectsNonUrlImageSourceLayers() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                ".button { -fx-background-image: not-a-url; }",
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "BSS image sources require one or more comma-separated url(...) layers.",
                failure.getMessage()
        );
    }

    /// Rejects a JavaFX property whose BSS converter remains unimplemented.
    @Test
    void rejectsPropertiesWithUnimplementedConverters() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                ".button { -fx-font: 12px System; }",
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "BSS output doesn't support JavaFX property -fx-font.",
                failure.getMessage()
        );
    }

    /// Rejects a selector relation that the supported BSS subset cannot encode.
    @Test
    void rejectsUnsupportedSelectorRelations() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString("a + b { -fx-opacity: 0.5; }", Syntax.SCSS),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "BSS output supports only descendant and child selector combinators.",
                failure.getMessage()
        );
    }

    /// Copies all remaining output bytes without changing the supplied buffer's position.
    ///
    /// @param buffer the binary output buffer
    /// @return a byte array containing the buffer's remaining bytes
    private static byte[] remainingBytes(@Unmodifiable ByteBuffer buffer) {
        var copy = buffer.duplicate();
        var bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    /// Compiles the shared stylesheet for one BSS target.
    ///
    /// @param target the selected BSS format target
    /// @return the compilation result
    /// @throws Exception if compilation fails unexpectedly
    private static CompileResult<@Unmodifiable ByteBuffer> compile(BssTarget target) throws Exception {
        return new SassCompiler().compile(
                SassSource.fromString(SUPPORTED_SOURCE, Syntax.SCSS),
                target
        );
    }
}
