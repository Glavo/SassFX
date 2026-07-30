// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final String FONT_FACE_JAVAFX17_FIXTURE = "AAYAEQAOAAZBVVRIT1IABFBhbmUAAAAPLWZ4LWZvbnQtZmFtaWx5ACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACSJFeGFtcGxlIgALZm9udC13ZWlnaHQAAzYwMAALZm9udC1mYW1pbHkAA1VSTAAraHR0cHM6Ly9leGFtcGxlLmludmFsaWQvZm9udHMvZXhhbXBsZS53b2ZmMgAFd29mZjIABUxPQ0FMAA1FeGFtcGxlIExvY2FsAAlSRUZFUkVOQ0UAEEV4YW1wbGVSZWZlcmVuY2UAAAABAAEBAAEAAAACAAAAAAAMAAEAAwABAAQEAAUAAAEAAgAAAAYAAAAHAAAACAAAAAUAAwAAAAkAAAAKAAAACwAAAAwAAAANAAAADgAAAA8AAAAQAAAADg==";

    /// Contains the JavaFX 17 BSS v6 fixture for [#SUPPORTED_SOURCE].
    private static final String JAVAFX17_FIXTURE = "AAYAF///AAZBVVRIT1IABFBhbmUAAAABKgAGYnV0dG9uAAVob3ZlcgAHcHJpbWFyeQAFTGFiZWwABGl0ZW0ACy1meC1vcGFjaXR5ACJqYXZhZnguY3NzLmNvbnZlcnRlci5TaXplQ29udmVydGVyAAJQWAANLWZ4LXRleHQtZmlsbAANLWZ4LWZvbnQtc2l6ZQA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250U2l6ZUNvbnZlcnRlcgAPLWZ4LWZvbnQtZmFtaWx5ACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACCJTeXN0ZW0iAAstZngtcGFkZGluZwAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAstZngtZGlzYWJsZQAlamF2YWZ4LmNzcy5jb252ZXJ0ZXIuQm9vbGVhbkNvbnZlcnRlcgAEdHJ1ZQAAAAEAAgIAAwEAAQAAAAIAAAEAAwABAAQAAgABAAUBAAMAAAAGAAAAAgEAAQAHAAEACAACAAAAAACrAAYACQABAAoBAAAJP+AAAAAAAAAACwEADAAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAAANAAEADgEAAAlAKAAAAAAAAAALAAAPAAEAEAQAEQAAEgABABMCAQAAAAQBAAAJP/AAAAAAAAAACwEAAAlAAAAAAAAAAAALAQAACUAIAAAAAAAAAAsBAAAJQBAAAAAAAAAACwAAFAABABUEABYAAAA=";

    /// Contains the JavaFX 27 BSS v9 fixture for [#SUPPORTED_SOURCE].
    private static final String JAVAFX27_FIXTURE = "AAkAF///AAZBVVRIT1IABFBhbmUAAAABKgAGYnV0dG9uAAVob3ZlcgAHcHJpbWFyeQAFTGFiZWwABGl0ZW0ACy1meC1vcGFjaXR5ACJqYXZhZnguY3NzLmNvbnZlcnRlci5TaXplQ29udmVydGVyAAJQWAANLWZ4LXRleHQtZmlsbAANLWZ4LWZvbnQtc2l6ZQA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250U2l6ZUNvbnZlcnRlcgAPLWZ4LWZvbnQtZmFtaWx5ACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACCJTeXN0ZW0iAAstZngtcGFkZGluZwAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAstZngtZGlzYWJsZQAlamF2YWZ4LmNzcy5jb252ZXJ0ZXIuQm9vbGVhbkNvbnZlcnRlcgAEdHJ1ZQAAAAAAAAABAAACAgADAQABAAAAAgAAAQADAAEABAACAAEABQEAAwAAAAYAAAACAQABAAcAAQAIAAIAAAAAAKsABgAJAAEACgEAAAk/4AAAAAAAAAALAQAMAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAAAAA0AAQAOAQAACUAoAAAAAAAAAAsAAA8AAQAQBAARAAASAAEAEwIBAAAABAEAAAk/8AAAAAAAAAALAQAACUAAAAAAAAAAAAsBAAAJQAgAAAAAAAAACwEAAAlAEAAAAAAAAAALAAAUAAEAFQQAFgAAAA==";

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

    /// Contains quoted strings parsed by generic and font-family grammars.
    private static final String QUOTED_STRING_SOURCE = """
            Label {
              -fx-ellipsis-string: "More";
              -fx-prompt-text: 'Type here';
              -fx-shape: "M 0 0 L 1 1 Z";
              -fx-custom-string: "";
              -fx-custom-true: "TrUe";
              -fx-custom-false: 'FALSE';
              -fx-custom-duration: "INDEFINITE";
              -fx-custom-infinity: "Infinity";
              -fx-custom-color: "red";
              -fx-custom-hex-color: "#123456";
              -fx-font-family: "Oracle Font";
              -fx-keyword-font-family: indefinite;
              -fx-quoted-keyword-font-family: "true";
              -fx-empty-font-family: "";
            }
            """;

    /// Contains every size unit accepted by JavaFX's generic sequence parser.
    private static final String GENERIC_SIZE_SEQUENCE_SOURCE = """
            Pane {
              -fx-custom-single-size: 1px;
              -fx-custom-sizes:
                  1 2% 3em 4ex 5px 6cm 7mm 8in 9pt 10pc
                  11deg 12grad 13rad 14turn;
              -fx-custom-mixed-sizes: -1.5em 0 2turn;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#TYPED_SCALAR_SOURCE].
    private static final String TYPED_SCALAR_JAVAFX17_FIXTURE = "AAYAIf//AAZBVVRIT1IABFBhbmUAAAAKLWZ4LWN1cnNvcgAEaGFuZAAOLWZ4LWJsZW5kLW1vZGUACG11bHRpcGx5AAktZngtc2hhcGUADU0gMCAwIEwgMSAxIFoADy1meC1mb250LXdlaWdodAA2amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250V2VpZ2h0Q29udmVydGVyAARCT0xEAA4tZngtZm9udC1zdHlsZQA1amF2YWZ4LmNzcy5jb252ZXJ0ZXIuRm9udENvbnZlcnRlciRGb250U3R5bGVDb252ZXJ0ZXIABklUQUxJQwAXLWZ4LWZvbnQtc21vb3RoaW5nLXR5cGUABGdyYXkAEy1meC1zdHJva2UtbGluZS1jYXAAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAIGphdmFmeC5zY2VuZS5zaGFwZS5TdHJva2VMaW5lQ2FwAAVyb3VuZAAULWZ4LXN0cm9rZS1saW5lLWpvaW4AIWphdmFmeC5zY2VuZS5zaGFwZS5TdHJva2VMaW5lSm9pbgAFYmV2ZWwADy1meC1zdHJva2UtdHlwZQAdamF2YWZ4LnNjZW5lLnNoYXBlLlN0cm9rZVR5cGUABmluc2lkZQAVLWZ4LXN0cm9rZS1kYXNoLWFycmF5ADRqYXZhZnguY3NzLmNvbnZlcnRlci5TaXplQ29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyAAJQWAANLWZ4LWFsaWdubWVudAAGY2VudGVyABItZngtdGV4dC1hbGlnbm1lbnQAAAABAAEBAAEAAAACAAAAAACTAAwAAwEABAAEAAAFAQAEAAYAAAcAAAQACAAACQABAAoEAAsAAAwAAQANBAAOAAAPAAAEABAAABEAAQASABMEABQAABUAAQASABYEABcAABgAAQASABkEABoAABsAAQAcAgEAAAACAQAACT/wAAAAAAAAAB0BAAAJQAAAAAAAAAAAHQAAHgEABAAfAAAgAQAEAB8AAAA=";

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
    private static final String FONT_VARIANT_JAVAFX17_FIXTURE = "AAYAEv//AAZBVVRIT1IABFBhbmUAAAAPLWZ4LWZvbnQtd2VpZ2h0ADZqYXZhZnguY3NzLmNvbnZlcnRlci5Gb250Q29udmVydGVyJEZvbnRXZWlnaHRDb252ZXJ0ZXIABFRISU4AC0VYVFJBX0xJR0hUAAVMSUdIVAAGTk9STUFMAAZNRURJVU0ACVNFTUlfQk9MRAAEQk9MRAAKRVhUUkFfQk9MRAAFQkxBQ0sADi1meC1mb250LXN0eWxlADVqYXZhZnguY3NzLmNvbnZlcnRlci5Gb250Q29udmVydGVyJEZvbnRTdHlsZUNvbnZlcnRlcgAHUkVHVUxBUgAGSVRBTElDAAAAAQABAQABAAAAAgAAAAAAjgAOAAMAAQAEBAAFAAADAAEABAQABgAAAwABAAQEAAcAAAMAAQAEBAAIAAADAAEABAQACQAAAwABAAQEAAoAAAMAAQAEBAALAAADAAEABAQADAAAAwABAAQEAA0AAAMAAQAEBAALAAADAAEABAQABwAADgABAA8EABAAAA4AAQAPBAARAAAOAAEADwQAEQAAAA==";

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
    private static final String BACKGROUND_AND_BORDER_GEOMETRY_JAVAFX17_FIXTURE = "AAYAD///AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyABUtZngtYmFja2dyb3VuZC1pbnNldHMANmphdmFmeC5jc3MuY29udmVydGVyLkluc2V0c0NvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAJQWAAVLWZ4LWJhY2tncm91bmQtcmFkaXVzADdjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkNvcm5lclJhZGlpQ29udmVydGVyAAdQRVJDRU5UABEtZngtb3BhcXVlLWluc2V0cwARLWZ4LWJvcmRlci1pbnNldHMAES1meC1ib3JkZXItcmFkaXVzAAAAAQABAQABAAAAAgAAAAAD6AAGAAMAAQAEAgEAAAACAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAAAFAAEABgIBAAAAAgEAAQAHAgEAAAAEAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAk/8AAAAAAAAAAIAQAACUAAAAAAAAAAAAgBAAEABwIBAAAABAEAAAlACAAAAAAAAAAIAQAACUAIAAAAAAAAAAgBAAAJQAgAAAAAAAAACAEAAAlACAAAAAAAAAAIAAAJAAEACgIBAAAAAgEAAAMBAAAAAgEAAAAEAQAACUAQAAAAAAAAAAgBAAAJQBQAAAAAAAAACAEAAAlAEAAAAAAAAAAIAQAACUAUAAAAAAAAAAgBAAAABAEAAAlAGAAAAAAAAAAIAQAACUAcAAAAAAAAAAgBAAAJQBgAAAAAAAAACAEAAAlAHAAAAAAAAAAIAQAAAwEAAAACAQAAAAQBAAAJQCAAAAAAAAAACwEAAAlAIAAAAAAAAAALAQAACUAgAAAAAAAAAAsBAAAJQCAAAAAAAAAACwEAAAAEAQAACUAgAAAAAAAAAAsBAAAJQCAAAAAAAAAACwEAAAlAIAAAAAAAAAALAQAACUAgAAAAAAAAAAsAAAwAAQAHAgEAAAAEAQAACUAiAAAAAAAAAAgBAAAJQCQAAAAAAAAACAEAAAlAJgAAAAAAAAAIAQAACUAoAAAAAAAAAAgAAA0AAQAGAgEAAAACAQABAAcCAQAAAAQBAAAJQCoAAAAAAAAACAEAAAlALAAAAAAAAAAIAQAACUAqAAAAAAAAAAgBAAAJQCwAAAAAAAAACAEAAQAHAgEAAAAEAQAACUAuAAAAAAAAAAgBAAAJQC4AAAAAAAAACAEAAAlALgAAAAAAAAAIAQAACUAuAAAAAAAAAAgAAA4AAQAKAgEAAAACAQAAAwEAAAACAQAAAAQBAAAJQDAAAAAAAAAACAEAAAlAMQAAAAAAAAAIAQAACUAwAAAAAAAAAAgBAAAJQDEAAAAAAAAACAEAAAAEAQAACUAyAAAAAAAAAAgBAAAJQDMAAAAAAAAACAEAAAlAMgAAAAAAAAAIAQAACUAzAAAAAAAAAAgBAAADAQAAAAIBAAAABAEAAAlANAAAAAAAAAALAQAACUA0AAAAAAAAAAsBAAAJQDQAAAAAAAAACwEAAAlANAAAAAAAAAALAQAAAAQBAAAJQDQAAAAAAAAACwEAAAlANAAAAAAAAAALAQAACUA0AAAAAAAAAAsBAAAJQDQAAAAAAAAACwAAAA==";

    /// Contains one semi-transparent solid background paint.
    private static final String SEMI_TRANSPARENT_BACKGROUND_SOURCE = """
            Pane {
              -fx-background-color: rgba(18, 52, 86, 0.3);
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#SEMI_TRANSPARENT_BACKGROUND_SOURCE].
    private static final String SEMI_TRANSPARENT_BACKGROUND_JAVAFX17_FIXTURE = "AAYABf//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyAAAAAQABAQABAAAAAgAAAAAAMwABAAMAAQAEAgEAAAABAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/TMzNAAAAAAAAA";

    /// Contains layered solid border paints and border widths.
    private static final String BORDER_STROKE_SOURCE = """
            Pane {
              -fx-border-color: #123456 red green blue, #abcdef;
              -fx-border-width: 1px 2px 3px 4px, 5%;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BORDER_STROKE_SOURCE].
    private static final String BORDER_STROKE_JAVAFX17_FIXTURE = "AAYAC///AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1jb2xvcgA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5MYXllcmVkQm9yZGVyUGFpbnRDb252ZXJ0ZXIAPWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uU3Ryb2tlQm9yZGVyUGFpbnRDb252ZXJ0ZXIAEC1meC1ib3JkZXItd2lkdGgAPGNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTWFyZ2lucyRTZXF1ZW5jZUNvbnZlcnRlcgA0Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5NYXJnaW5zJENvbnZlcnRlcgACUFgAB1BFUkNFTlQAAAABAAEBAAEAAAACAAAAAAHYAAIAAwABAAQCAQAAAAIBAAEABQIBAAAABAEAAAU/shISIAAAAD/KGhogAAAAP9WVlaAAAAA/8AAAAAAAAAEAAAU/8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAAEAAAUAAAAAAAAAAD/gEBAgAAAAAAAAAAAAAAA/8AAAAAAAAAEAAAUAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAA/8AAAAAAAAAEAAQAFAgEAAAAEAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAQAABT/ldXWAAAAAP+m5ucAAAAA/7f3+AAAAAD/wAAAAAAAAAAAGAAEABwIBAAAAAgEAAQAIAgEAAAAEAQAACT/wAAAAAAAAAAkBAAAJQAAAAAAAAAAACQEAAAlACAAAAAAAAAAJAQAACUAQAAAAAAAAAAkBAAEACAIBAAAABAEAAAlAFAAAAAAAAAAKAQAACUAUAAAAAAAAAAoBAAAJQBQAAAAAAAAACgEAAAlAFAAAAAAAAAAKAAAA";

    /// Contains layered background and border image source URLs.
    private static final String IMAGE_SOURCE = """
            Pane {
              -fx-background-image: url("image.png"), url(second.png);
              -fx-border-image-source: url("border.png");
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#IMAGE_SOURCE] without a stylesheet base URL.
    private static final String IMAGE_JAVAFX17_FIXTURE = "AAYAC///AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtaW1hZ2UAM2phdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAhamF2YWZ4LmNzcy5jb252ZXJ0ZXIuVVJMQ29udmVydGVyACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACWltYWdlLnBuZwAKc2Vjb25kLnBuZwAXLWZ4LWJvcmRlci1pbWFnZS1zb3VyY2UACmJvcmRlci5wbmcAAAABAAEBAAEAAAACAAAAAABYAAIAAwABAAQCAQAAAAIBAAEABQIBAAAAAgEAAQAGBAAHAAEAAQAFAgEAAAACAQABAAYEAAgAAAAJAAEABAIBAAAAAQEAAQAFAgEAAAACAQABAAYEAAoAAAAA";

    /// Contains the canonical source URL used for [#IMAGE_SOURCE]'s JavaFX resolution base.
    private static final URI IMAGE_SOURCE_URL = URI.create("https://example.invalid/assets/theme.scss");

    /// Contains the JavaFX 17 BSS v6 fixture for [#IMAGE_SOURCE] with [#IMAGE_SOURCE_URL].
    private static final String IMAGE_WITH_BASE_JAVAFX17_FIXTURE = "AAYADP//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtaW1hZ2UAM2phdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAhamF2YWZ4LmNzcy5jb252ZXJ0ZXIuVVJMQ29udmVydGVyACRqYXZhZnguY3NzLmNvbnZlcnRlci5TdHJpbmdDb252ZXJ0ZXIACWltYWdlLnBuZwApaHR0cHM6Ly9leGFtcGxlLmludmFsaWQvYXNzZXRzL3RoZW1lLnNjc3MACnNlY29uZC5wbmcAFy1meC1ib3JkZXItaW1hZ2Utc291cmNlAApib3JkZXIucG5nAAAAAQABAQABAAAAAgAAAAAAZwACAAMAAQAEAgEAAAACAQABAAUCAQAAAAIBAAEABgQABwEAAAQACAEAAQAFAgEAAAACAQABAAYEAAkBAAAEAAgAAAoAAQAEAgEAAAABAQABAAUCAQAAAAIBAAEABgQACwEAAAQACAAAAA==";

    /// Contains JavaFX linear and radial paint layers for BSS serialization.
    private static final String GRADIENT_PAINT_SOURCE = """
            Pane {
              -fx-background-color: linear-gradient(to right bottom, reflect, red, #123456 25%, rgba(18, 52, 86, 0.3) 75%, blue), radial-gradient(focus-angle 45deg, focus-distance 20%, center 30% 40%, radius 50%, repeat, red 0%, green 50%, blue 100%);
              -fx-border-color: linear-gradient(from 0% 0% to 100% 100%, repeat, red 0%, blue 100%) green radial-gradient(radius 20px, red, blue) #123456, red;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#GRADIENT_PAINT_SOURCE].
    private static final String GRADIENT_PAINT_JAVAFX17_FIXTURE = "AAYAE///AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyADtqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRMaW5lYXJHcmFkaWVudENvbnZlcnRlcgAHUEVSQ0VOVAAiamF2YWZ4LmNzcy5jb252ZXJ0ZXIuRW51bUNvbnZlcnRlcgAeamF2YWZ4LnNjZW5lLnBhaW50LkN5Y2xlTWV0aG9kAAdSRUZMRUNUACJqYXZhZnguY3NzLmNvbnZlcnRlci5TdG9wQ29udmVydGVyADtqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRSYWRpYWxHcmFkaWVudENvbnZlcnRlcgADREVHAAZSRVBFQVQAEC1meC1ib3JkZXItY29sb3IAPmNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJvcmRlclBhaW50Q29udmVydGVyAD1jb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLlN0cm9rZUJvcmRlclBhaW50Q29udmVydGVyAAJQWAAITk9fQ1lDTEUAAAABAAEBAAEAAAACAAAAAATFAAIAAwABAAQCAQAAAAIBAAEABQIBAAAACQEAAAkAAAAAAAAAAAAGAQAACQAAAAAAAAAAAAYBAAAJQFkAAAAAAAAABgEAAAlAWQAAAAAAAAAGAQABAAcACAQACQEAAQAKAgEAAAACAQAACQAAAAAAAAAAAAYBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAEACgIBAAAAAgEAAAlAOQAAAAAAAAAGAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAQABAAoCAQAAAAIBAAAJQFLAAAAAAAAABgEAAAU/shISIAAAAD/KGhogAAAAP9WVlaAAAAA/0zMzQAAAAAEAAQAKAgEAAAACAQAACUBZAAAAAAAAAAYBAAAFAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAP/AAAAAAAAABAAEACwIBAAAACQEAAAlARoAAAAAAAAAMAQAACUA0AAAAAAAAAAYBAAAJQD4AAAAAAAAABgEAAAlARAAAAAAAAAAGAQAACUBJAAAAAAAAAAYBAAEABwAIBAANAQABAAoCAQAAAAIBAAAJAAAAAAAAAAAABgEAAAU/8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAAEAAQAKAgEAAAACAQAACUBJAAAAAAAAAAYBAAAFAAAAAAAAAAA/4BAQIAAAAAAAAAAAAAAAP/AAAAAAAAABAAEACgIBAAAAAgEAAAlAWQAAAAAAAAAGAQAABQAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAD/wAAAAAAAAAAAOAAEADwIBAAAAAgEAAQAQAgEAAAAEAQABAAUCAQAAAAcBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACUBZAAAAAAAAAAYBAAAJQFkAAAAAAAAABgEAAQAHAAgEAA0BAAEACgIBAAAAAgEAAAkAAAAAAAAAAAAGAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQABAAoCAQAAAAIBAAAJQFkAAAAAAAAABgEAAAUAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAA/8AAAAAAAAAEAAAUAAAAAAAAAAD/gEBAgAAAAAAAAAAAAAAA/8AAAAAAAAAEAAQALAgEAAAAIAAAAAAEAAAlANAAAAAAAAAARAQABAAcACAQAEgEAAQAKAgEAAAACAQAACQAAAAAAAAAAAAYBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAEACgIBAAAAAgEAAAlAWQAAAAAAAAAGAQAABQAAAAAAAAAAAAAAAAAAAAA/8AAAAAAAAD/wAAAAAAAAAQAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAQABABACAQAAAAQBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAABAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAAAAAA=";

    /// Contains layered JavaFX border styles with segments and stroke options.
    private static final String BORDER_STYLE_SOURCE = """
            Pane {
              -fx-border-style: solid dashed dotted hidden, segments(1px, 2px, 3px) phase 4px outside line-join miter 5px line-cap square, dashed centered line-join bevel line-cap butt;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BORDER_STYLE_SOURCE].
    private static final String BORDER_STYLE_JAVAFX17_FIXTURE = "AAYAFP//AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1zdHlsZQA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5MYXllcmVkQm9yZGVyU3R5bGVDb252ZXJ0ZXIARWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uQm9yZGVyU3Ryb2tlU3R5bGVTZXF1ZW5jZUNvbnZlcnRlcgA3Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5Cb3JkZXJTdHlsZUNvbnZlcnRlcgA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuU2l6ZUNvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgACUFgAImphdmFmeC5jc3MuY29udmVydGVyLlNpemVDb252ZXJ0ZXIAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAHWphdmFmeC5zY2VuZS5zaGFwZS5TdHJva2VUeXBlAAdvdXRzaWRlACFqYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlTGluZUpvaW4ABW1pdGVyACBqYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlTGluZUNhcAAGc3F1YXJlAAhjZW50ZXJlZAAFYmV2ZWwABGJ1dHQAAAABAAEBAAEAAAACAAAAAANMAAEAAwABAAQCAQAAAAMBAAEABQIBAAAABAEAAQAGAgEAAAAGAQAAAAAAAAAAAQABAAYCAQAAAAYBAAAAAAAAAAABAAEABgIBAAAABgEAAAAAAAAAAAEAAQAGAgEAAAAGAQAAAAAAAAAAAQABAAUCAQAAAAQBAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABgIBAAAABgEAAQAHAgEAAAADAQAACT/wAAAAAAAAAAgBAAAJQAAAAAAAAAAACAEAAAlACAAAAAAAAAAIAQABAAkBAAAJQBAAAAAAAAAACAEAAQAKAAsEAAwBAAEACgANBAAOAQABAAkBAAAJQBQAAAAAAAAACAEAAQAKAA8EABABAAEABQIBAAAABAEAAQAGAgEAAAAGAQAAAAABAAEACgALBAARAQABAAoADQQAEgABAAEACgAPBAATAQABAAYCAQAAAAYBAAAAAAEAAQAKAAsEABEBAAEACgANBAASAAEAAQAKAA8EABMBAAEABgIBAAAABgEAAAAAAQABAAoACwQAEQEAAQAKAA0EABIAAQABAAoADwQAEwEAAQAGAgEAAAAGAQAAAAABAAEACgALBAARAQABAAoADQQAEgABAAEACgAPBAATAAAA";

    /// Contains a JavaFX border style whose size positions are property lookups.
    private static final String LOOKUP_BORDER_STYLE_SOURCE = """
            Pane {
              -fx-border-style: segments(-fx-dash-a, -fx-dash-b) phase -fx-phase outside line-join miter -fx-miter line-cap round;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_BORDER_STYLE_SOURCE].
    private static final String LOOKUP_BORDER_STYLE_JAVAFX17_FIXTURE = "AAYAFP//AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1zdHlsZQA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5MYXllcmVkQm9yZGVyU3R5bGVDb252ZXJ0ZXIARWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uQm9yZGVyU3Ryb2tlU3R5bGVTZXF1ZW5jZUNvbnZlcnRlcgA3Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5Cb3JkZXJTdHlsZUNvbnZlcnRlcgA0amF2YWZ4LmNzcy5jb252ZXJ0ZXIuU2l6ZUNvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAKLWZ4LWRhc2gtYQAKLWZ4LWRhc2gtYgAiamF2YWZ4LmNzcy5jb252ZXJ0ZXIuU2l6ZUNvbnZlcnRlcgAJLWZ4LXBoYXNlACJqYXZhZnguY3NzLmNvbnZlcnRlci5FbnVtQ29udmVydGVyAB1qYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlVHlwZQAHb3V0c2lkZQAhamF2YWZ4LnNjZW5lLnNoYXBlLlN0cm9rZUxpbmVKb2luAAVtaXRlcgAJLWZ4LW1pdGVyACBqYXZhZnguc2NlbmUuc2hhcGUuU3Ryb2tlTGluZUNhcAAFcm91bmQAAAABAAEBAAEAAAACAAAAAAFyAAEAAwABAAQCAQAAAAEBAAEABQIBAAAABAEAAQAGAgEAAAAGAQABAAcCAQAAAAIBAQAEAAgBAQAEAAkBAAEACgEBAAQACwEAAQAMAA0EAA4BAAEADAAPBAAQAQABAAoBAQAEABEBAAEADAASBAATAQABAAYCAQAAAAYBAAEABwIBAAAAAgEBAAQACAEBAAQACQEAAQAKAQEABAALAQABAAwADQQADgEAAQAMAA8EABABAAEACgEBAAQAEQEAAQAMABIEABMBAAEABgIBAAAABgEAAQAHAgEAAAACAQEABAAIAQEABAAJAQABAAoBAQAEAAsBAAEADAANBAAOAQABAAwADwQAEAEAAQAKAQEABAARAQABAAwAEgQAEwEAAQAGAgEAAAAGAQABAAcCAQAAAAIBAQAEAAgBAQAEAAkBAAEACgEBAAQACwEAAQAMAA0EAA4BAAEADAAPBAAQAQABAAoBAQAEABEBAAEADAASBAATAAAA";

    /// Contains JavaFX's standalone {@code none} border-style declaration.
    private static final String NONE_BORDER_STYLE_SOURCE = """
            Pane {
              -fx-border-style: none;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#NONE_BORDER_STYLE_SOURCE].
    private static final String NONE_BORDER_STYLE_JAVAFX17_FIXTURE = "AAYABf//AAZBVVRIT1IABFBhbmUAAAAQLWZ4LWJvcmRlci1zdHlsZQAEbnVsbAAAAAEAAQEAAQAAAAIAAAAAAAoAAQADAAAEAAQAAAA=";

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
    private static final String BORDER_IMAGE_LAYOUT_JAVAFX17_FIXTURE = "AAYAG///AAZBVVRIT1IABFBhbmUAAAAXLWZ4LWJvcmRlci1pbWFnZS1pbnNldHMANmphdmFmeC5jc3MuY29udmVydGVyLkluc2V0c0NvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuSW5zZXRzQ29udmVydGVyAAJQWAAPLWZ4LWltYWdlLWluc2V0ABctZngtYm9yZGVyLWltYWdlLXJlcGVhdAA4Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5SZXBlYXRTdHJ1Y3RDb252ZXJ0ZXIAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAJGphdmFmeC5zY2VuZS5sYXlvdXQuQmFja2dyb3VuZFJlcGVhdAAGUkVQRUFUAAlOT19SRVBFQVQABVNQQUNFAAVST1VORAAWLWZ4LWJvcmRlci1pbWFnZS1zbGljZQA5Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5TbGljZVNlcXVlbmNlQ29udmVydGVyADxjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkJvcmRlckltYWdlU2xpY2VDb252ZXJ0ZXIAB1BFUkNFTlQADy1meC1pbWFnZS1zbGljZQAWLWZ4LWJvcmRlci1pbWFnZS13aWR0aABFY29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5Cb3JkZXJJbWFnZVdpZHRoc1NlcXVlbmNlQ29udmVydGVyADxjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkJvcmRlckltYWdlV2lkdGhDb252ZXJ0ZXIABGF1dG8ADy1meC1pbWFnZS13aWR0aAAPLWZ4LW90aGVyLXdpZHRoAAAAAQABAQABAAAAAgAAAAACWQAEAAMAAQAEAgEAAAACAQABAAUCAQAAAAQBAAAJP/AAAAAAAAAABgEAAAlAAAAAAAAAAAAGAQAACUAIAAAAAAAAAAYBAAAJQBAAAAAAAAAABgEAAQAFAgEAAAAEAQEABAAHAQEABAAHAQEABAAHAQEABAAHAAAIAAEACQMBAAAAAwEAAAACAQABAAoACwQADAEAAQAKAAsEAA0BAAAAAgEAAQAKAAsEAA4BAAEACgALBAAPAQAAAAIBAAEACgALBAANAQABAAoACwQADQAAEAABABECAQAAAAMBAAEAEgIBAAAAAgEAAQAFAgEAAAAEAQAACUAkAAAAAAAAABMBAAAJQCQAAAAAAAAAEwEAAAlAJAAAAAAAAAATAQAACUAkAAAAAAAAABMBAAAHAQEAAQASAgEAAAACAQABAAUCAQAAAAQBAQAEABQBAQAEABQBAQAEABQBAQAEABQBAAAHAQEAAQASAgEAAAACAQABAAUCAQAAAAQBAAAJQDQAAAAAAAAAEwEAAAlAPgAAAAAAAAATAQAACUBEAAAAAAAAABMBAAAJQEkAAAAAAAAAEwEAAAcAAAAVAAEAFgIBAAAAAwEAAQAXAgEAAAAEAQAACT/wAAAAAAAAAAYBAAAJQAAAAAAAAAAAEwEAAAlACAAAAAAAAAAGAQAACUAQAAAAAAAAAAYBAAEAFwIBAAAABAEBAAQAGAEBAAQAGQEAAAlAGAAAAAAAAAAGAQAACUAcAAAAAAAAABMBAAEAFwIBAAAABAEBAAQAGgEBAAQAGgEBAAQAGgEBAAQAGgAAAA==";
    /// Contains JavaFX lookup, image-pattern, and repeating-image-pattern paint layers.
    private static final String LOOKUP_AND_IMAGE_PATTERN_PAINT_SOURCE = """
            Pane {
              -fx-base: #123456;
              -fx-background-color: -fx-base, image-pattern("image.png"), image-pattern("image.png", 1px, 2px, 3px, 4px, false), image-pattern("image.png", 5%, 6%, 7%, 8%, true), repeating-image-pattern("tile.png");
              -fx-border-color: -fx-base image-pattern("border.png", 1px, 2px, 3px, 4px, true) repeating-image-pattern("border-tile.png"), red;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_AND_IMAGE_PATTERN_PAINT_SOURCE].
    private static final String LOOKUP_AND_IMAGE_PATTERN_PAINT_JAVAFX17_FIXTURE = "AAYAE///AAZBVVRIT1IABFBhbmUAAAAILWZ4LWJhc2UAFC1meC1iYWNrZ3JvdW5kLWNvbG9yADVqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgA5amF2YWZ4LmNzcy5jb252ZXJ0ZXIuUGFpbnRDb252ZXJ0ZXIkSW1hZ2VQYXR0ZXJuQ29udmVydGVyACFqYXZhZnguY3NzLmNvbnZlcnRlci5VUkxDb252ZXJ0ZXIAJGphdmFmeC5jc3MuY29udmVydGVyLlN0cmluZ0NvbnZlcnRlcgALImltYWdlLnBuZyIAAlBYAAdQRVJDRU5UAEJqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRSZXBlYXRpbmdJbWFnZVBhdHRlcm5Db252ZXJ0ZXIACiJ0aWxlLnBuZyIAEC1meC1ib3JkZXItY29sb3IAPmNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJvcmRlclBhaW50Q29udmVydGVyAD1jb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLlN0cm9rZUJvcmRlclBhaW50Q29udmVydGVyAAwiYm9yZGVyLnBuZyIAESJib3JkZXItdGlsZS5wbmciAAAAAQABAQABAAAAAgAAAAACwQADAAMAAAU/shISIAAAAD/KGhogAAAAP9WVlaAAAAA/8AAAAAAAAAAABAABAAUCAQAAAAUBAQAEAAMBAAEABgIBAAAAAQEAAQAHAgEAAAACAQABAAgEAAkAAQABAAYCAQAAAAYBAAEABwIBAAAAAgEAAQAIBAAJAAEAAAk/8AAAAAAAAAAKAQAACUAAAAAAAAAAAAoBAAAJQAgAAAAAAAAACgEAAAlAEAAAAAAAAAAKAQAABwABAAEABgIBAAAABgEAAQAHAgEAAAACAQABAAgEAAkAAQAACUAUAAAAAAAAAAsBAAAJQBgAAAAAAAAACwEAAAlAHAAAAAAAAAALAQAACUAgAAAAAAAAAAsBAAAHAQEAAQAMAgEAAAABAQABAAcCAQAAAAIBAAEACAQADQAAAA4AAQAPAgEAAAACAQABABACAQAAAAQBAQAEAAMBAAEABgIBAAAABgEAAQAHAgEAAAACAQABAAgEABEAAQAACT/wAAAAAAAAAAoBAAAJQAAAAAAAAAAACgEAAAlACAAAAAAAAAAKAQAACUAQAAAAAAAAAAoBAAAHAQEAAQAMAgEAAAABAQABAAcCAQAAAAIBAAEACAQAEgABAAEABgIBAAAABgEAAQAHAgEAAAACAQABAAgEABEAAQAACT/wAAAAAAAAAAoBAAAJQAAAAAAAAAAACgEAAAlACAAAAAAAAAAKAQAACUAQAAAAAAAAAAoBAAAHAQEAAQAQAgEAAAAEAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAQAABT/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/wAAAAAAAAAAAA";

    /// Contains one image-pattern with JavaFX lookup-backed geometry values.
    private static final String LOOKUP_IMAGE_PATTERN_SIZE_SOURCE = """
            Pane {
              -fx-background-color: image-pattern("image.png", -fx-pattern-x, -fx-pattern-y, -fx-pattern-width, -fx-pattern-height);
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_IMAGE_PATTERN_SIZE_SOURCE].
    private static final String LOOKUP_IMAGE_PATTERN_SIZE_JAVAFX17_FIXTURE = "AAYADf//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyADlqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRJbWFnZVBhdHRlcm5Db252ZXJ0ZXIAIWphdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuU3RyaW5nQ29udmVydGVyAAsiaW1hZ2UucG5nIgANLWZ4LXBhdHRlcm4teAANLWZ4LXBhdHRlcm4teQARLWZ4LXBhdHRlcm4td2lkdGgAEi1meC1wYXR0ZXJuLWhlaWdodAAAAAEAAQEAAQAAAAIAAAAAAEYAAQADAAEABAIBAAAAAQEAAQAFAgEAAAAFAQABAAYCAQAAAAIBAAEABwQACAABAQAEAAkBAQAEAAoBAQAEAAsBAQAEAAwAAAA=";

    /// Contains one image-pattern URI represented with JavaFX {@code url(...)} syntax.
    private static final String URL_IMAGE_PATTERN_SOURCE = """
            Pane {
              -fx-background-color: image-pattern(url("image-two.png"));
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#URL_IMAGE_PATTERN_SOURCE].
    private static final String URL_IMAGE_PATTERN_JAVAFX17_FIXTURE = "AAYACf//AAZBVVRIT1IABFBhbmUAAAAULWZ4LWJhY2tncm91bmQtY29sb3IANWphdmFmeC5jc3MuY29udmVydGVyLlBhaW50Q29udmVydGVyJFNlcXVlbmNlQ29udmVydGVyADlqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRJbWFnZVBhdHRlcm5Db252ZXJ0ZXIAIWphdmFmeC5jc3MuY29udmVydGVyLlVSTENvbnZlcnRlcgAkamF2YWZ4LmNzcy5jb252ZXJ0ZXIuU3RyaW5nQ29udmVydGVyAA1pbWFnZS10d28ucG5nAAAAAQABAQABAAAAAgAAAAAALgABAAMAAQAEAgEAAAABAQABAAUCAQAAAAEBAAEABgIBAAAAAgEAAQAHBAAIAAAAAA==";

    /// Contains a JavaFX gradient that resolves one color stop through a property lookup.
    private static final String LOOKUP_GRADIENT_PAINT_SOURCE = """
            Pane {
              -fx-base: #123456;
              -fx-background-color: linear-gradient(-fx-base, red);
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#LOOKUP_GRADIENT_PAINT_SOURCE].
    private static final String LOOKUP_GRADIENT_PAINT_JAVAFX17_FIXTURE = "AAYADP//AAZBVVRIT1IABFBhbmUAAAAILWZ4LWJhc2UAFC1meC1iYWNrZ3JvdW5kLWNvbG9yADVqYXZhZnguY3NzLmNvbnZlcnRlci5QYWludENvbnZlcnRlciRTZXF1ZW5jZUNvbnZlcnRlcgA7amF2YWZ4LmNzcy5jb252ZXJ0ZXIuUGFpbnRDb252ZXJ0ZXIkTGluZWFyR3JhZGllbnRDb252ZXJ0ZXIAB1BFUkNFTlQAImphdmFmeC5jc3MuY29udmVydGVyLkVudW1Db252ZXJ0ZXIAHmphdmFmeC5zY2VuZS5wYWludC5DeWNsZU1ldGhvZAAITk9fQ1lDTEUAImphdmFmeC5jc3MuY29udmVydGVyLlN0b3BDb252ZXJ0ZXIAAAABAAEBAAEAAAACAAAAAADeAAIAAwAABT+yEhIgAAAAP8oaGiAAAAA/1ZWVoAAAAD/wAAAAAAAAAAAEAAEABQIBAAAAAQEAAQAGAgEAAAAHAQAACQAAAAAAAAAAAAcBAAAJAAAAAAAAAAAABwEAAAkAAAAAAAAAAAAHAQAACUBZAAAAAAAAAAcBAAEACAAJBAAKAQABAAsCAQAAAAIBAAAJAAAAAAAAAAAABwEBAAQAAwEAAQALAgEAAAACAQAACUBZAAAAAAAAAAcBAAAFP/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAAAAAA=";
    /// Contains layered background image layout values for JavaFX BSS encoding.
    private static final String BACKGROUND_LAYOUT_SOURCE = """
            Pane {
              -fx-background-position: left 10px top 20%, right 5px bottom 6px, top left 7px, center bottom, 30px 40px;
              -fx-background-repeat: repeat-x, repeat-y, space no-repeat, round, stretch;
              -fx-background-size: 25px auto, auto 30%, cover, contain, stretch;
            }
            """;

    /// Contains the JavaFX 17 BSS v6 fixture for [#BACKGROUND_LAYOUT_SOURCE].
    private static final String BACKGROUND_LAYOUT_JAVAFX17_FIXTURE = "AAYAFv//AAZBVVRIT1IABFBhbmUAAAAXLWZ4LWJhY2tncm91bmQtcG9zaXRpb24ARWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJhY2tncm91bmRQb3NpdGlvbkNvbnZlcnRlcgA+Y29tLnN1bi5qYXZhZnguc2NlbmUubGF5b3V0LnJlZ2lvbi5CYWNrZ3JvdW5kUG9zaXRpb25Db252ZXJ0ZXIAB1BFUkNFTlQAAlBYABUtZngtYmFja2dyb3VuZC1yZXBlYXQAOGNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uUmVwZWF0U3RydWN0Q29udmVydGVyACJqYXZhZnguY3NzLmNvbnZlcnRlci5FbnVtQ29udmVydGVyACRqYXZhZnguc2NlbmUubGF5b3V0LkJhY2tncm91bmRSZXBlYXQABlJFUEVBVAAJTk9fUkVQRUFUAAVTUEFDRQAFUk9VTkQAEy1meC1iYWNrZ3JvdW5kLXNpemUAQWNvbS5zdW4uamF2YWZ4LnNjZW5lLmxheW91dC5yZWdpb24uTGF5ZXJlZEJhY2tncm91bmRTaXplQ29udmVydGVyADpjb20uc3VuLmphdmFmeC5zY2VuZS5sYXlvdXQucmVnaW9uLkJhY2tncm91bmRTaXplQ29udmVydGVyACVqYXZhZnguY3NzLmNvbnZlcnRlci5Cb29sZWFuQ29udmVydGVyAAVmYWxzZQAEdHJ1ZQAAAAEAAQEAAQAAAAIAAAAAAroAAwADAAEABAIBAAAABQEAAQAFAgEAAAAEAQAACUA0AAAAAAAAAAYBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACUAkAAAAAAAAAAcBAAEABQIBAAAABAEAAAkAAAAAAAAAAAAGAQAACUAUAAAAAAAAAAcBAAAJQBgAAAAAAAAABwEAAAkAAAAAAAAAAAAGAQABAAUCAQAAAAQBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACQAAAAAAAAAAAAYBAAAJQBwAAAAAAAAABwEAAQAFAgEAAAAEAQAACUBZAAAAAAAAAAYBAAAJAAAAAAAAAAAABgEAAAkAAAAAAAAAAAAGAQAACUBJAAAAAAAAAAYBAAEABQIBAAAABAEAAAlARAAAAAAAAAAHAQAACQAAAAAAAAAAAAYBAAAJAAAAAAAAAAAABgEAAAlAPgAAAAAAAAAHAAAIAAEACQMBAAAABQEAAAACAQABAAoACwQADAEAAQAKAAsEAA0BAAAAAgEAAQAKAAsEAA0BAAEACgALBAAMAQAAAAIBAAEACgALBAAOAQABAAoACwQADQEAAAACAQABAAoACwQADwEAAQAKAAsEAA8BAAAAAgEAAQAKAAsEAA0BAAEACgALBAANAAAQAAEAEQIBAAAABQEAAQASAgEAAAAEAQAACUA5AAAAAAAAAAcAAQABABMEABQBAAEAEwQAFAEAAQASAgEAAAAEAAEAAAlAPgAAAAAAAAAGAQABABMEABQBAAEAEwQAFAEAAQASAgEAAAAEAAABAAEAEwQAFQEAAQATBAAUAQABABICAQAAAAQAAAEAAQATBAAUAQABABMEABUBAAEAEgIBAAAABAEAAAlAWQAAAAAAAAAGAQAACUBZAAAAAAAAAAYBAAEAEwQAFAEAAQATBAAUAAAA";

    /// Serializes JavaFX font-face descriptors and URL, local, and reference sources.
    @Test
    void compilesFontFaceRules() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(FONT_FACE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(FONT_FACE_JAVAFX17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Compiles plain-CSS font-face descriptors and source functions.
    @Test
    void compilesPlainCssFontFace() throws Exception {
        var compiler = new SassCompiler();
        var expected = compiler.compile(
                SassSource.fromString(FONT_FACE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();
        var actual = compiler.compile(
                SassSource.fromString(FONT_FACE_SOURCE, Syntax.CSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(remainingBytes(expected), remainingBytes(actual));
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
    void compilesJavaFX17Bss() throws Exception {
        var result = compile(BssTarget.DEFAULT);
        var output = result.output();

        assertTrue(output.isReadOnly());
        assertEquals(0, output.position());
        assertArrayEquals(
                Base64.getDecoder().decode(JAVAFX17_FIXTURE),
                remainingBytes(output)
        );
        assertNull(result.sourceMap());
    }

    /// Compiles plain-CSS declaration values through the JavaFX BSS encoders.
    @Test
    void compilesPlainCssDeclarationValues() throws Exception {
        var compiler = new SassCompiler();
        var expected = compiler.compile(
                SassSource.fromString(SUPPORTED_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();
        var actual = compiler.compile(
                SassSource.fromString(SUPPORTED_SOURCE, Syntax.CSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(remainingBytes(expected), remainingBytes(actual));
    }

    /// Matches SCSS BSS for named-color and property-lookup precedence.
    @Test
    void compilesPlainCssLayeredNamedColors() throws Exception {
        var source = """
                Pane {
                  red: #123456;
                  green: #abcdef;
                  -fx-background-color: red, blue;
                  -fx-border-color: red green blue black, linear-gradient(red, blue);
                  -fx-fill: red;
                  -fx-effect: dropshadow(gaussian, green, 8px, 20%, 1px, 2px);
                }
                """;
        var compiler = new SassCompiler();
        var expected = compiler.compile(
                SassSource.fromString(source, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();
        var actual = compiler.compile(
                SassSource.fromString(source, Syntax.CSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(remainingBytes(expected), remainingBytes(actual));
    }

    /// Rejects declaration names that the JavaFX CSS lexer cannot tokenize.
    @Test
    void rejectsUnsupportedJavaFXDeclarationNames() {
        var compiler = new SassCompiler();
        for (var source : List.of(
                "Pane { --custom: red; }",
                "Pane { 属性: red; }"
        )) {
            var input = SassSource.fromString(source, Syntax.CSS);
            for (var target : List.<OutputTarget<?>>of(
                    JavaFXCssTarget.DEFAULT,
                    BssTarget.DEFAULT
            )) {
                var failure = assertThrows(
                        SassCompilationException.class,
                        () -> compiler.compile(input, target)
                );
                assertTrue(
                        failure.getMessage().contains("declaration name"),
                        failure.getMessage()
                );
            }
        }
    }

    /// Stores functional pseudo-classes with JavaFX's token concatenation.
    @Test
    void compilesFunctionalPseudoClasses() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Pane.repeat.repeat:dir(rtl):lang("en"):state(primary selected)\
                                :state(primary selected):state(primary/**/secondary)\
                                :empty-state():not(leaf):dir(ltr) {
                                  -fx-opacity: 0.5;
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("lang(\"en\")"));
        assertTrue(strings.contains("state(primaryselected)"));
        assertTrue(strings.contains("state(primarysecondary)"));
        assertTrue(strings.contains("empty-state()"));
        assertTrue(strings.contains("not(leaf)"));
        assertTrue(strings.contains("dir(ltr)"));
        assertFalse(strings.contains("dir(rtl)"));
        assertFalse(strings.contains("state(primary selected)"));

        var input = document.input();
        input.readUnsignedShort(); // origin
        assertEquals(1, input.readUnsignedShort());
        assertEquals(1, input.readUnsignedShort());
        assertEquals(1, input.readUnsignedByte());
        assertEquals("Pane", document.strings()[input.readUnsignedShort()]);
        assertEquals(1, input.readUnsignedShort());
        assertEquals("repeat", document.strings()[input.readUnsignedShort()]);
        assertEquals("", document.strings()[input.readUnsignedShort()]);
        assertEquals(6, input.readUnsignedShort());
        assertEquals("lang(\"en\")", document.strings()[input.readUnsignedShort()]);
        assertEquals(
                "state(primaryselected)",
                document.strings()[input.readUnsignedShort()]
        );
        assertEquals(
                "state(primarysecondary)",
                document.strings()[input.readUnsignedShort()]
        );
        assertEquals("empty-state()", document.strings()[input.readUnsignedShort()]);
        assertEquals("not(leaf)", document.strings()[input.readUnsignedShort()]);
        assertEquals("dir(ltr)", document.strings()[input.readUnsignedShort()]);
    }

    /// Serializes the JavaFX 27 import and media-rule framing of BSS v9.
    @Test
    void compilesJavaFX27Bss() throws Exception {
        var target = new BssTarget(JavaFXTarget.JAVAFX27);
        var output = compile(target).output();

        assertTrue(output.isReadOnly());
        assertEquals(0, output.position());
        assertArrayEquals(
                Base64.getDecoder().decode(JAVAFX27_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Requires the fixed OpenJFX byte fixtures to exercise every supported
    /// non-transition converter family.
    ///
    /// This turns the converter inventory into an executable coverage
    /// contract. A new serializer branch must add a pinned OpenJFX fixture,
    /// and removing the last fixture for a supported converter fails here.
    @Test
    void coversEverySupportedConverterFamilyWithPinnedFixtures()
            throws Exception {
        var fixtureBytes = new StringBuilder();
        for (var field : BssTargetTest.class.getDeclaredFields()) {
            if (field.getType() == String.class
                    && (field.getName().equals("JAVAFX17_FIXTURE")
                    || field.getName().endsWith("_JAVAFX17_FIXTURE"))) {
                var encoded = (String) field.get(null);
                fixtureBytes.append(new String(
                        Base64.getDecoder().decode(encoded),
                        StandardCharsets.ISO_8859_1
                ));
            }
        }
        fixtureBytes.append(new String(
                remainingBytes(new SassCompiler().compile(
                        SassSource.fromString(
                                """
                                        Pane {
                                          -fx-show-delay: 1s;
                                          -fx-font: italic bold 12px "System";
                                          -fx-effect: dropshadow(gaussian, red, 10px, 0.2, 1px, 2px);
                                          -fx-fill: derive(red, 10%);
                                          -fx-stroke: ladder(red, black 0%, white 100%);
                                        }
                                        Label {
                                          -fx-effect: innershadow(gaussian, blue, 8px, 0.1, 0, 1px);
                                        }
                                        """,
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                ).output()),
                StandardCharsets.ISO_8859_1
        ));

        var converters = List.of(
                "javafx.css.converter.SizeConverter",
                "javafx.css.converter.SizeConverter$SequenceConverter",
                "javafx.css.converter.FontConverter",
                "javafx.css.converter.FontConverter$FontSizeConverter",
                "javafx.css.converter.FontConverter$FontStyleConverter",
                "javafx.css.converter.FontConverter$FontWeightConverter",
                "javafx.css.converter.EnumConverter",
                "javafx.css.converter.DurationConverter",
                "javafx.css.converter.StringConverter",
                "javafx.css.converter.URLConverter",
                "javafx.css.converter.URLConverter$SequenceConverter",
                "javafx.css.converter.BooleanConverter",
                "javafx.css.converter.InsetsConverter",
                "javafx.css.converter.InsetsConverter$SequenceConverter",
                "javafx.css.converter.PaintConverter$SequenceConverter",
                "javafx.css.converter.PaintConverter$LinearGradientConverter",
                "javafx.css.converter.PaintConverter$RadialGradientConverter",
                "javafx.css.converter.PaintConverter$ImagePatternConverter",
                "javafx.css.converter.PaintConverter$RepeatingImagePatternConverter",
                "javafx.css.converter.StopConverter",
                "javafx.css.converter.DeriveColorConverter",
                "javafx.css.converter.LadderConverter",
                "javafx.css.converter.EffectConverter$DropShadowConverter",
                "javafx.css.converter.EffectConverter$InnerShadowConverter",
                "com.sun.javafx.scene.layout.region.LayeredBackgroundPositionConverter",
                "com.sun.javafx.scene.layout.region.BackgroundPositionConverter",
                "com.sun.javafx.scene.layout.region.RepeatStructConverter",
                "com.sun.javafx.scene.layout.region.LayeredBackgroundSizeConverter",
                "com.sun.javafx.scene.layout.region.BackgroundSizeConverter",
                "com.sun.javafx.scene.layout.region.SliceSequenceConverter",
                "com.sun.javafx.scene.layout.region.BorderImageSliceConverter",
                "com.sun.javafx.scene.layout.region.BorderImageWidthsSequenceConverter",
                "com.sun.javafx.scene.layout.region.BorderImageWidthConverter",
                "com.sun.javafx.scene.layout.region.CornerRadiiConverter",
                "com.sun.javafx.scene.layout.region.LayeredBorderPaintConverter",
                "com.sun.javafx.scene.layout.region.StrokeBorderPaintConverter",
                "com.sun.javafx.scene.layout.region.Margins$Converter",
                "com.sun.javafx.scene.layout.region.Margins$SequenceConverter",
                "com.sun.javafx.scene.layout.region.LayeredBorderStyleConverter",
                "com.sun.javafx.scene.layout.region.BorderStrokeStyleSequenceConverter",
                "com.sun.javafx.scene.layout.region.BorderStyleConverter"
        );
        for (var converter : converters) {
            assertTrue(
                    fixtureBytes.indexOf(converter) >= 0,
                    () -> "No pinned fixture covers " + converter
            );
        }
    }

    /// Emits every JavaFX target with its corresponding BSS header version.
    @Test
    void writesEverySupportedBssHeader() throws Exception {
        for (var compatibility : JavaFXTarget.values()) {
            var output = new SassCompiler().compile(
                    SassSource.fromString(
                            "Pane { -fx-opacity: 1; }",
                            Syntax.SCSS
                    ),
                    new BssTarget(compatibility)
            ).output();
            assertEquals(
                    compatibility.bssVersion(),
                    output.duplicate().order(ByteOrder.BIG_ENDIAN).getShort()
            );
        }
    }

    /// Uses the JavaFX 8 converter package in BSS version 5 string tables.
    @Test
    void writesJavaFX8ConverterClassNames() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Pane {
                                  -fx-opacity: 0.5;
                                  -fx-background-color: linear-gradient(red, blue);
                                }
                                """,
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX8)
        ).output();
        var binaryText = new String(
                remainingBytes(output),
                StandardCharsets.ISO_8859_1
        );

        assertTrue(binaryText.contains("com.sun.javafx.css.converters.SizeConverter"));
        assertTrue(binaryText.contains("com.sun.javafx.css.parser.StopConverter"));
        assertFalse(binaryText.contains("javafx.css.converter.SizeConverter"));
        assertFalse(binaryText.contains("com.sun.javafx.css.converters.StopConverter"));
    }

    /// Reproduces JavaFX 8's gradient repeat-cycle encoding.
    @Test
    void writesJavaFX8GradientRepeatCompatibility() throws Exception {
        var compiler = new SassCompiler();
        var source = SassSource.fromString(
                """
                        Pane {
                          -fx-background-color:
                              linear-gradient(repeat, red, blue),
                              radial-gradient(radius 50%, repeat, red, blue);
                        }
                        """,
                Syntax.SCSS
        );
        var javaFX8Output = compiler.compile(
                source,
                new BssTarget(JavaFXTarget.JAVAFX8)
        ).output();
        var javaFX17Output = compiler.compile(
                source,
                new BssTarget(JavaFXTarget.JAVAFX17)
        ).output();
        var javaFX8Text = new String(
                remainingBytes(javaFX8Output),
                StandardCharsets.ISO_8859_1
        );
        var javaFX17Text = new String(
                remainingBytes(javaFX17Output),
                StandardCharsets.ISO_8859_1
        );

        assertTrue(javaFX8Text.contains("REFLECT"));
        assertFalse(javaFX8Text.contains("REPEAT"));
        assertTrue(javaFX17Text.contains("REPEAT"));
        assertFalse(javaFX17Text.contains("REFLECT"));
    }

    /// Serializes JavaFX's deprecated gradient and ladder grammars.
    @Test
    void compilesLegacyGradientPaints() throws Exception {
        var source = SassSource.fromString(
                """
                        Pane {
                          -fx-background-color:
                              linear (0%,0%) to (100%,100%) stops (0.0,red) (1.0,rgba(0, 0, 255, 0.5)) reflect,
                              radial focus-angle 45deg focus-distance 20% center (30%,40%) 50% stops (0.0,red) (0.5,green) (1.0,blue) repeat;
                          -fx-border-color:
                              linear (0%,0%) to (100%,100%) stops (0.0,red) (1.0,blue) no-cycle
                              green blue black;
                        }
                        Shape {
                          -fx-fill:
                              radial 50% stops (0.0,red) (1.0,blue) no-cycle;
                        }
                        LookupPane {
                          -fx-background-color:
                              linear (-fx-start-x,-fx-start-y) to (-fx-end-x,-fx-end-y)
                              stops (-fx-stop-offset,-fx-base) (1.0,blue);
                        }
                        LadderShape {
                          -fx-fill:
                              ladder -fx-base stops
                              (0.0,black) (1.0,derive(-fx-base, 20%));
                          -fx-stroke:
                              ladder #123456 stops (0.5,white);
                        }
                        """,
                Syntax.SCSS
        );
        var javaFX8 = new SassCompiler().compile(
                source,
                new BssTarget(JavaFXTarget.JAVAFX8)
        ).output();
        var javaFX17 = new SassCompiler().compile(
                source,
                new BssTarget(JavaFXTarget.JAVAFX17)
        ).output();
        var javaFX8Text = new String(
                remainingBytes(javaFX8),
                StandardCharsets.ISO_8859_1
        );
        var javaFX17Text = new String(
                remainingBytes(javaFX17),
                StandardCharsets.ISO_8859_1
        );

        assertTrue(javaFX8Text.contains(
                "com.sun.javafx.css.converters.PaintConverter"
                        + "$LinearGradientConverter"
        ));
        assertTrue(javaFX8Text.contains(
                "com.sun.javafx.css.converters.PaintConverter"
                        + "$RadialGradientConverter"
        ));
        assertTrue(javaFX17Text.contains(
                "javafx.css.converter.PaintConverter"
                        + "$LinearGradientConverter"
        ));
        assertTrue(javaFX17Text.contains(
                "javafx.css.converter.PaintConverter"
                        + "$RadialGradientConverter"
        ));
        assertTrue(javaFX8Text.contains("REFLECT"));
        assertTrue(javaFX8Text.contains("REPEAT"));
        assertTrue(javaFX17Text.contains("REFLECT"));
        assertTrue(javaFX17Text.contains("REPEAT"));
        assertTrue(javaFX8Text.contains("-fx-start-x"));
        assertTrue(javaFX8Text.contains("-fx-stop-offset"));
        assertTrue(javaFX17Text.contains("-fx-end-y"));
        assertTrue(javaFX17Text.contains("-fx-base"));
        assertTrue(javaFX8Text.contains(
                "com.sun.javafx.css.parser.LadderConverter"
        ));
        assertTrue(javaFX17Text.contains(
                "javafx.css.converter.LadderConverter"
        ));
    }

    /// Rejects an incomplete legacy gradient.
    @Test
    void rejectsInvalidLegacyGradient() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                """
                                        Pane {
                                          -fx-background-color:
                                              linear (0%,0%) to (100%,100%) stops;
                                        }
                                        """,
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertTrue(failure.getMessage().contains("BSS"));
    }

    /// Serializes JavaFX scalar converters with the JavaFX 17 BSS wire format.
    @Test
    void compilesTypedScalarConverters() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(TYPED_SCALAR_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(TYPED_SCALAR_JAVAFX17_FIXTURE),
                remainingBytes(output)
        );
    }

    /// Strips CSS quotes from generic strings while preserving font-family tokens.
    @Test
    void compilesQuotedStringsWithPropertySpecificSemantics() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(QUOTED_STRING_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("More"));
        assertTrue(strings.contains("Type here"));
        assertTrue(strings.contains("M 0 0 L 1 1 Z"));
        assertTrue(strings.contains(""));
        assertTrue(strings.contains("\"Oracle Font\""));
        assertTrue(strings.contains("indefinite"));
        assertTrue(strings.contains("\"true\""));
        assertTrue(strings.contains("\"\""));
        assertTrue(strings.contains("javafx.css.converter.StringConverter"));
        assertTrue(strings.contains("javafx.css.converter.BooleanConverter"));
        assertTrue(strings.contains("javafx.css.converter.DurationConverter"));
        assertTrue(strings.contains("javafx.css.converter.SizeConverter"));
        assertTrue(strings.contains("true"));
        assertTrue(strings.contains("false"));
        assertFalse(strings.contains("\"More\""));
        assertFalse(strings.contains("'Type here'"));
        assertFalse(strings.contains("\"M 0 0 L 1 1 Z\""));
        assertFalse(strings.contains("\"TrUe\""));
        assertFalse(strings.contains("'FALSE'"));
        assertFalse(strings.contains("\"INDEFINITE\""));
        assertFalse(strings.contains("\"Infinity\""));
        assertFalse(strings.contains("\"red\""));
        assertFalse(strings.contains("\"#123456\""));
    }

    /// Lowercases declaration names and only canonicalizes lookup keys that
    /// JavaFX has already encountered.
    @Test
    void normalizesCaseInsensitivePropertiesAndLookupKeys() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Pane {
                                  -FX-BASE: #123456;
                                  -FX-SELF: -FX-SELF;
                                  -FX-FORWARD: -FX-LATER;
                                  -FX-BACKGROUND-COLOR:
                                      linear-gradient(-FX-BASE, derive(-FX-BASE, 10%));
                                  -FX-PADDING: 1px 2px 3px 4px;
                                  -FX-LATER: red;
                                  -FX-CUSTOM-TOKEN: MixedCase;
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("-fx-base"));
        assertTrue(strings.contains("-fx-self"));
        assertTrue(strings.contains("-fx-forward"));
        assertTrue(strings.contains("-fx-background-color"));
        assertTrue(strings.contains("-fx-padding"));
        assertTrue(strings.contains("-fx-later"));
        assertTrue(strings.contains("-fx-custom-token"));
        assertTrue(strings.contains("-FX-LATER"));
        assertTrue(strings.contains("MixedCase"));
        assertTrue(strings.contains("javafx.css.converter.InsetsConverter"));
        assertTrue(strings.contains(
                "javafx.css.converter.PaintConverter$SequenceConverter"
        ));
        assertFalse(strings.contains("-FX-BASE"));
        assertFalse(strings.contains("-FX-SELF"));
        assertFalse(strings.contains("-FX-PADDING"));
    }

    /// Keeps each imported stylesheet's source-ordered property lookup registry
    /// independent from its importer.
    @Test
    void isolatesLookupPropertiesForImportedStylesheets(
            @TempDir Path directory
    ) throws Exception {
        var imported = directory.resolve("theme.css");
        var root = directory.resolve("root.css");
        Files.writeString(
                imported,
                "ImportedPane { -FX-SHARED: red; }",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                root,
                """
                        @import "theme.css";
                        RootPane {
                          -FX-FILL: -FX-SHARED;
                          -FX-LOCAL: red;
                          -FX-STROKE: -FX-LOCAL;
                        }
                        """,
                StandardCharsets.UTF_8
        );

        for (var target : List.of(
                JavaFXTarget.JAVAFX17,
                JavaFXTarget.JAVAFX27
        )) {
            var document = decodeDocument(new SassCompiler().compile(
                    SassSource.fromFile(root),
                    new BssTarget(target)
            ).output());
            var strings = java.util.Arrays.asList(document.strings());

            assertTrue(strings.contains("-fx-shared"), target.toString());
            assertTrue(strings.contains("-FX-SHARED"), target.toString());
            assertTrue(strings.contains("-fx-local"), target.toString());
            assertFalse(strings.contains("-FX-LOCAL"), target.toString());
            assertFalse(strings.contains("red"), target.toString());
        }
    }

    /// Serializes all generic non-time size units with the sequence converter.
    @Test
    void compilesGenericSizeSequence() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(GENERIC_SIZE_SEQUENCE_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("javafx.css.converter.SizeConverter"));
        assertTrue(strings.contains("javafx.css.converter.SizeConverter$SequenceConverter"));
        for (var unit : new String[]{
                "PX", "PERCENT", "EM", "EX", "CM", "MM", "IN", "PT", "PC",
                "DEG", "GRAD", "RAD", "TURN"
        }) {
            assertTrue(strings.contains(unit), unit);
        }
        assertFalse(strings.contains("S"));
        assertFalse(strings.contains("MS"));
    }

    /// Rejects generic sequences containing non-size or time values.
    @Test
    void rejectsInvalidGenericSizeSequences() {
        for (var value : new String[]{
                "1px 2ms",
                "1ms 2px",
                "1px -fx-other-size",
                "-fx-other-size 1px",
                "1px red",
                "1px, 2px",
                "1px / 2px",
                "[1px 2px]"
        }) {
            var failure = assertThrows(
                    SassCompilationException.class,
                    () -> new SassCompiler().compile(
                            SassSource.fromString(
                                    "Pane { -fx-custom-sizes: " + value + "; }",
                                    Syntax.SCSS
                            ),
                            BssTarget.DEFAULT
                    ),
                    value
            );

            assertEquals(
                    "BSS generic size sequences require two or more unbracketed"
                            + " space-separated non-time sizes.",
                    failure.getMessage()
            );
        }
    }

    /// Serializes JavaFX 18 color-named blend modes as plain strings.
    @Test
    void compilesExtendedBlendModesAsStrings() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Add { -fx-blend-mode: add; }
                                Red { -fx-blend-mode: red; }
                                Green { -fx-blend-mode: green; }
                                Blue { -fx-blend-mode: blue; }
                                """,
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX18)
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("add"));
        assertTrue(strings.contains("red"));
        assertTrue(strings.contains("green"));
        assertTrue(strings.contains("blue"));
    }

    /// Serializes JavaFX numeric, relative, and posture font variants.
    @Test
    void compilesNumericAndRelativeFontVariants() throws Exception {
        var output = new SassCompiler().compile(
                SassSource.fromString(FONT_VARIANT_SOURCE, Syntax.SCSS),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(
                Base64.getDecoder().decode(FONT_VARIANT_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(BACKGROUND_AND_BORDER_GEOMETRY_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(SEMI_TRANSPARENT_BACKGROUND_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(BORDER_STROKE_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(IMAGE_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(IMAGE_WITH_BASE_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(GRADIENT_PAINT_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(BORDER_STYLE_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(LOOKUP_BORDER_STYLE_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(NONE_BORDER_STYLE_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(BORDER_IMAGE_LAYOUT_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(LOOKUP_AND_IMAGE_PATTERN_PAINT_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(LOOKUP_AND_IMAGE_PATTERN_PAINT_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(LOOKUP_IMAGE_PATTERN_SIZE_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(URL_IMAGE_PATTERN_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(LOOKUP_GRADIENT_PAINT_JAVAFX17_FIXTURE),
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
                Base64.getDecoder().decode(BACKGROUND_LAYOUT_JAVAFX17_FIXTURE),
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

    /// Encodes the JavaFX font shorthand with its composite converter.
    @Test
    void compilesFontShorthand() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                .button {
                                  -fx-font: italic small-caps bold 14px/18px "Example Sans";
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());

        assertTrue(
                java.util.Arrays.asList(document.strings()).contains(
                        "javafx.css.converter.FontConverter"
                )
        );
    }

    /// Encodes every OpenJFX font-size keyword with its parser semantics.
    @Test
    void compilesFontSizeKeywords() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Pane {
                                  -fx-a-font-size: inherit;
                                  -fx-b-font-size: xx-small;
                                  -fx-c-font-size: x-small;
                                  -fx-d-font-size: small;
                                  -fx-e-font-size: medium;
                                  -fx-f-font-size: large;
                                  -fx-g-font-size: x-large;
                                  -fx-h-font-size: xx-large;
                                  -fx-i-font-size: smaller;
                                  -fx-j-font-size: larger;
                                  -fx-font: italic large/medium "Example Sans";
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains(
                "javafx.css.converter.FontConverter$FontSizeConverter"
        ));
        assertTrue(strings.contains("PERCENT"));
        assertTrue(strings.contains("inherit"));
        for (var keyword : new String[]{
                "xx-small", "x-small", "small", "medium", "large",
                "x-large", "xx-large", "smaller", "larger"
        }) {
            assertFalse(strings.contains(keyword), keyword);
        }
    }

    /// Rejects values outside OpenJFX's font-size grammar.
    @Test
    void rejectsInvalidFontSizes() {
        for (var value : new String[]{
                "\"large\"",
                "extra-large",
                "1s",
                "45deg"
        }) {
            var failure = assertThrows(
                    SassCompilationException.class,
                    () -> new SassCompiler().compile(
                            SassSource.fromString(
                                    "Pane { -fx-font-size: " + value + "; }",
                                    Syntax.SCSS
                            ),
                            BssTarget.DEFAULT
                    ),
                    value
            );

            assertEquals(
                    "BSS font sizes require a JavaFX size or font-size keyword.",
                    failure.getMessage()
            );
        }
    }

    /// Encodes scalar and nested derived and ladder colors with JavaFX converters.
    @Test
    void compilesDerivedAndLadderColors() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                .button {
                                  -fx-text-fill: derive(#336699, -15%);
                                  -fx-fill: ladder(-fx-base, black 0%, white 100%);
                                  -fx-background-color: linear-gradient(derive(-fx-base, 20%), ladder(-fx-background, red 0%, white 100%));
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("javafx.css.converter.DeriveColorConverter"));
        assertTrue(strings.contains("javafx.css.converter.LadderConverter"));
        assertTrue(strings.contains("javafx.css.converter.StopConverter"));
    }

    /// Encodes scalar and layered JavaFX region references with StringConverter.
    @Test
    void compilesRegionReferences() throws Exception {
        for (var target : java.util.List.of(
                JavaFXTarget.JAVAFX8,
                JavaFXTarget.JAVAFX17
        )) {
            var document = decodeDocument(new SassCompiler().compile(
                    SassSource.fromString(
                            """
                                    Shape {
                                      -fx-fill: region("#glyph");
                                    }
                                    Pane {
                                      -fx-background-color:
                                          region(".source"),
                                          region("#badge"),
                                          REGION(""),
                                          regionXYZ(".escaped\\6f");
                                    }
                                    """,
                            Syntax.SCSS
                    ),
                    new BssTarget(target)
            ).output());
            var strings = java.util.Arrays.asList(document.strings());
            var converter = target == JavaFXTarget.JAVAFX8
                    ? "com.sun.javafx.css.converters.StringConverter"
                    : "javafx.css.converter.StringConverter";

            assertTrue(strings.contains(converter));
            assertTrue(strings.contains("SPECIAL-REGION-URL:#glyph"));
            assertTrue(strings.contains("SPECIAL-REGION-URL:.source"));
            assertTrue(strings.contains("SPECIAL-REGION-URL:#badge"));
            assertTrue(strings.contains("SPECIAL-REGION-URL:"));
            assertTrue(strings.contains("SPECIAL-REGION-URL:.escapedo"));
            assertFalse(strings.contains("region(\"#glyph\")"));
        }
    }

    /// Ignores tokens after the first quoted JavaFX region argument.
    @Test
    void regionReferenceUsesOnlyFirstArgument() throws Exception {
        var compiler = new SassCompiler();
        var plain = compiler.compile(
                SassSource.fromString(
                        "Shape { -fx-fill: region(\".source\"); }",
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output();
        var commaExtra = compiler.compile(
                SassSource.fromString(
                        "Shape { -fx-fill: region(\".source\", \".ignored\"); }",
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output();
        var seriesExtra = compiler.compile(
                SassSource.fromString(
                        "Shape { -fx-fill: region(\".source\" \".ignored\"); }",
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output();

        assertArrayEquals(remainingBytes(plain), remainingBytes(commaExtra));
        assertArrayEquals(remainingBytes(plain), remainingBytes(seriesExtra));
    }

    /// Rejects malformed JavaFX region references.
    @Test
    void rejectsInvalidRegionReferences() {
        for (var value : java.util.List.of(
                "region()",
                "region(unquoted)"
        )) {
            var failure = assertThrows(
                    SassCompilationException.class,
                    () -> new SassCompiler().compile(
                            SassSource.fromString(
                                    "Shape { -fx-fill: " + value + "; }",
                                    Syntax.SCSS
                            ),
                            BssTarget.DEFAULT
                    )
            );

            assertEquals(
                    "BSS paint values require solid, derived, or ladder colors,"
                            + " property lookups, JavaFX gradients, image patterns,"
                            + " or region references.",
                    failure.getMessage()
            );
        }
    }

    /// Encodes one scalar URL with JavaFX's non-sequence URL converter.
    @Test
    void compilesScalarUrl() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        ".button { -fx-graphic: url(\"icon.png\"); }",
                        Syntax.SCSS,
                        URI.create("https://example.invalid/assets/theme.scss")
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("javafx.css.converter.URLConverter"));
        assertTrue(strings.contains("icon.png"));
        assertTrue(strings.contains("https://example.invalid/assets/theme.scss"));
        assertFalse(strings.contains("javafx.css.converter.URLConverter$SequenceConverter"));
    }

    /// Encodes ordinary millisecond, second, and indefinite duration values.
    @Test
    void compilesDurationScalars() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Tooltip {
                                  -fx-show-delay: 125ms;
                                  -fx-show-duration: 1.5s;
                                  -fx-hide-delay: indefinite;
                                }
                                Spinner {
                                  -fx-initial-delay: "INDEFINITE";
                                  -fx-repeat-delay: 50ms;
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("javafx.css.converter.DurationConverter"));
        assertTrue(strings.contains("MS"));
        assertTrue(strings.contains("S"));
        assertTrue(strings.contains("PX"));
        assertFalse(strings.contains("indefinite"));
        assertFalse(strings.contains("\"INDEFINITE\""));
    }

    /// Uses the internal JavaFX 8 converter name for ordinary durations.
    @Test
    void compilesJavaFX8DurationConverterName() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        "Tooltip { -fx-show-delay: 125ms; }",
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX8)
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains(
                "com.sun.javafx.css.converters.DurationConverter"
        ));
        assertFalse(strings.contains("javafx.css.converter.DurationConverter"));
    }

    /// Preserves a duration property lookup without assigning a duration converter.
    @Test
    void preservesDurationLookup() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        "Tooltip { -fx-show-delay: -fx-delay; }",
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("-fx-delay"));
        assertFalse(strings.contains("javafx.css.converter.DurationConverter"));
    }

    /// Keeps the indefinite token in property-specific font-family grammar.
    @Test
    void keepsIndefiniteFontFamilyAsString() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        "Label { -fx-font-family: indefinite; }",
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("javafx.css.converter.StringConverter"));
        assertTrue(strings.contains("indefinite"));
        assertFalse(strings.contains("javafx.css.converter.DurationConverter"));
    }

    /// Wraps a scalar fill URL as an image pattern without a stylesheet base URL.
    @Test
    void compilesFillUrlWithoutStylesheetBase() throws Exception {
        var sourceUrl = URI.create("https://example.invalid/assets/theme.scss");
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        "Shape { -fx-fill: url(\"fill.png\"); }",
                        Syntax.SCSS,
                        sourceUrl
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains(
                "javafx.css.converter.PaintConverter$ImagePatternConverter"
        ));
        assertTrue(strings.contains("javafx.css.converter.URLConverter"));
        assertTrue(strings.contains("fill.png"));
        assertFalse(strings.contains(sourceUrl.toString()));
    }

    /// Produces the same BSS value for scalar fill URL and image-pattern syntax.
    @Test
    void scalarFillUrlMatchesImagePatternFunction() throws Exception {
        var sourceUrl = URI.create("https://example.invalid/assets/theme.scss");
        for (var javaFXTarget : java.util.List.of(
                JavaFXTarget.JAVAFX8,
                JavaFXTarget.JAVAFX17,
                JavaFXTarget.JAVAFX27
        )) {
            var scalar = new SassCompiler().compile(
                    SassSource.fromString(
                            "Shape { -fx-fill: url(\"fill.png\"); }",
                            Syntax.SCSS,
                            sourceUrl
                    ),
                    new BssTarget(javaFXTarget)
            ).output();
            var function = new SassCompiler().compile(
                    SassSource.fromString(
                            "Shape { -fx-fill: image-pattern(url(\"fill.png\")); }",
                            Syntax.SCSS,
                            sourceUrl
                    ),
                    new BssTarget(javaFXTarget)
            ).output();

            assertArrayEquals(remainingBytes(function), remainingBytes(scalar));
        }
    }

    /// Encodes both JavaFX shadow effects, all blur types, and lookup-backed slots.
    @Test
    void compilesShadowEffects() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Pane {
                                  -fx-effect: dropshadow(gaussian, rgba(18, 52, 86, 0.3), 12px, 25%, -2px, 3em);
                                }
                                Text {
                                  -fx-effect: innershadow(two-pass-box, -fx-shadow-color, -fx-radius, 0.4, 2px, -3px);
                                }
                                Region {
                                  -fx-effect: dropshadow(one-pass-box, derive(#123456, 10%), 1em, 0, 0, 1px);
                                }
                                Label {
                                  -fx-effect: innershadow(three-pass-box, hsba(240, 100%, 50%, 0.5), 8px, 0.2, 1px, 2px);
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains(
                "javafx.css.converter.EffectConverter$DropShadowConverter"
        ));
        assertTrue(strings.contains(
                "javafx.css.converter.EffectConverter$InnerShadowConverter"
        ));
        assertTrue(strings.contains("javafx.scene.effect.BlurType"));
        assertTrue(strings.contains("GAUSSIAN"));
        assertTrue(strings.contains("ONE_PASS_BOX"));
        assertTrue(strings.contains("TWO_PASS_BOX"));
        assertTrue(strings.contains("THREE_PASS_BOX"));
        assertTrue(strings.contains("-fx-shadow-color"));
        assertTrue(strings.contains("-fx-radius"));
    }

    /// Dispatches every supported JavaFX value function by its OpenJFX
    /// case-insensitive name prefix.
    @Test
    void compilesOpenJFXFunctionNamePrefixes() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                PrefixColors {
                                  -fx-a: RgBSuffix(18, 52, 86, 0.3);
                                  -fx-b: hsbSuffix(210, 79%, 34%, 0.3);
                                  -fx-c: deriveSuffix(#123456, 10%);
                                  -fx-d: ladderSuffix(#123456, red 0%, blue 100%);
                                }
                                PrefixEffects {
                                  -fx-effect: dropshadowSuffix(
                                      gaussian, #123456, 8px, 20%, 1px, 2px);
                                }
                                PrefixInnerEffect {
                                  -fx-effect: innershadowSuffix(
                                      three-pass-box, #123456, 8px, 20%, 1px, 2px);
                                }
                                PrefixPaints {
                                  -fx-background-color:
                                      linear-gradientSuffix(red, blue),
                                      radial-gradientSuffix(radius 50%, red, blue);
                                  -fx-fill: image-patternSuffix(
                                      "image.png", 0%, 0%, 100%, 100%, false);
                                  -fx-stroke:
                                      repeating-image-patternSuffix("tile.png");
                                  -fx-text-fill: regionSuffix("#glyph");
                                }
                                PrefixBorder {
                                  -fx-border-style: SeGmEnTsSuffix(1px, 2px);
                                }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        for (var converter : List.of(
                "javafx.css.converter.DeriveColorConverter",
                "javafx.css.converter.LadderConverter",
                "javafx.css.converter.EffectConverter$DropShadowConverter",
                "javafx.css.converter.EffectConverter$InnerShadowConverter",
                "javafx.css.converter.PaintConverter$LinearGradientConverter",
                "javafx.css.converter.PaintConverter$RadialGradientConverter",
                "javafx.css.converter.PaintConverter$ImagePatternConverter",
                "javafx.css.converter.PaintConverter$RepeatingImagePatternConverter",
                "javafx.css.converter.SizeConverter$SequenceConverter",
                "javafx.css.converter.StringConverter"
        )) {
            assertTrue(strings.contains(converter), converter);
        }
    }

    /// Uses JavaFX 8's internal converter package for shadow effects.
    @Test
    void compilesJavaFX8ShadowConverterNames() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Pane {
                                  -fx-effect: dropshadow(gaussian, black, 10px, 0, 1px, 2px);
                                }
                                Text {
                                  -fx-effect: innershadow(three-pass-box, black, 8px, 0.2, 1px, 2px);
                                }
                                """,
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX8)
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains(
                "com.sun.javafx.css.converters.EffectConverter$DropShadowConverter"
        ));
        assertTrue(strings.contains(
                "com.sun.javafx.css.converters.EffectConverter$InnerShadowConverter"
        ));
        assertTrue(strings.contains("com.sun.javafx.css.converters.EnumConverter"));
        assertFalse(strings.contains(
                "javafx.css.converter.EffectConverter$DropShadowConverter"
        ));
    }

    /// Rejects malformed or unsupported JavaFX effect functions.
    @Test
    void rejectsInvalidShadowEffects() {
        var invalidValues = java.util.List.of(
                "dropshadow(unknown, black, 10px, 0, 1px, 2px)",
                "dropshadow(gaussian, black, 10px, 0, 1px)",
                "dropshadow(gaussian, black, 10px, 0, 1px, 2px, 3px)",
                "dropshadow(gaussian, black, 10px, 100ms, 1px, 2px)",
                "unknown-effect(gaussian, black, 10px, 0, 1px, 2px)",
                "\"dropshadow(gaussian, black, 10px, 0, 1px, 2px)\""
        );
        for (var value : invalidValues) {
            var failure = assertThrows(
                    SassCompilationException.class,
                    () -> new SassCompiler().compile(
                            SassSource.fromString(
                                    ".button { -fx-effect: " + value + "; }",
                                    Syntax.SCSS
                            ),
                            BssTarget.DEFAULT
                    )
            );

            assertEquals(
                    "BSS effects require dropshadow() or innershadow() with a blur"
                            + " type, color, radius, spread or choke, and two offsets.",
                    failure.getMessage()
            );
        }
    }

    /// Rejects function tokens outside OpenJFX's value-function dispatch table.
    @Test
    void rejectsUnsupportedValueFunctions() {
        for (var value : List.of(
                "unsupported(1)",
                "hslSuffix(0, 100%, 50%)"
        )) {
            var failure = assertThrows(
                    SassCompilationException.class,
                    () -> new SassCompiler().compile(
                            SassSource.fromString(
                                    "Pane { -fx-custom: " + value + "; }",
                                    Syntax.SCSS
                            ),
                            BssTarget.DEFAULT
                    )
            );

            assertEquals(
                    "BSS output doesn't support this JavaFX value function.",
                    failure.getMessage()
            );
        }
    }

    /// Preserves global and property-lookup effect values without a shadow converter.
    @Test
    void compilesEffectKeywordsAndLookups() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                Pane { -fx-effect: none; }
                                Text { -fx-effect: -fx-other-effect; }
                                """,
                        Syntax.SCSS
                ),
                BssTarget.DEFAULT
        ).output());
        var strings = java.util.Arrays.asList(document.strings());

        assertTrue(strings.contains("null"));
        assertTrue(strings.contains("-fx-other-effect"));
        assertFalse(strings.contains(
                "javafx.css.converter.EffectConverter$DropShadowConverter"
        ));
        assertFalse(strings.contains(
                "javafx.css.converter.EffectConverter$InnerShadowConverter"
        ));
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

    /// Rejects nested style rules retained by plain CSS nesting.
    @Test
    void rejectsNestedStyleRules() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                """
                                        .parent {
                                          .child { -fx-opacity: 0.5; }
                                        }
                                        """,
                                Syntax.CSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "BSS output doesn't support nested style rules.",
                failure.getMessage()
        );
    }

    /// Rejects opaque at-rules that have no BSS encoding.
    @Test
    void rejectsUnknownAtRules() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                "@keyframes pulse { from { -fx-opacity: 0; } }",
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "BSS output doesn't support @keyframes rules.",
                failure.getMessage()
        );
    }

    /// Rejects every transition declaration that affected JavaFX releases
    /// cannot deserialize from BSS.
    @Test
    void rejectsTransitionsThatJavaFXCannotLoadFromBss() {
        String @Unmodifiable [] declarations = new String[]{
                "transition: opacity 250ms ease-in 10ms",
                "transition-delay: 10ms",
                "transition-duration: 250ms",
                "TrAnSiTiOn-DuRaTiOn: 250ms",
                "transition-property: opacity",
                "transition-timing-function: ease-in"
        };
        JavaFXTarget @Unmodifiable [] targets = new JavaFXTarget[]{
                JavaFXTarget.JAVAFX23,
                JavaFXTarget.JAVAFX27
        };

        for (var target : targets) {
            for (var declaration : declarations) {
                var failure = assertThrows(
                        SassCompilationException.class,
                        () -> new SassCompiler().compile(
                                SassSource.fromString(
                                        "Pane { " + declaration + "; }",
                                        Syntax.SCSS
                                ),
                                new BssTarget(target)
                        )
                );

                assertEquals(
                        "JavaFX 23 through 27 cannot load transition declarations from BSS.",
                        failure.getMessage()
                );
            }
        }
    }

    /// Rejects media types, which JavaFX media-condition grammar does not support.
    @Test
    void rejectsMediaTypes() {
        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromString(
                                """
                                        .button {
                                          @media screen {
                                            -fx-opacity: 0.5;
                                          }
                                        }
                                        """,
                                Syntax.SCSS
                        ),
                        BssTarget.DEFAULT
                )
        );

        assertEquals(
                "Expected '('",
                failure.getMessage()
        );
    }

    /// Serializes JavaFX 25 discrete media expressions into BSS version 7.
    @Test
    void compilesJavaFX25MediaRules() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @media (prefers-color-scheme: dark)
                                        and ((prefers-reduced-motion)
                                        or (not (prefers-reduced-transparency))) {
                                  Pane { -fx-opacity: 1; }
                                }
                                """,
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX25)
        ).output());
        var input = document.input();

        assertEquals(7, document.version());
        input.readShort(); // origin
        assertEquals(1, input.readShort());
        assertTrue(input.readBoolean());
        assertEquals(1, input.readInt());
        assertEquals(3, input.readUnsignedByte());
        assertFeature(
                input,
                document.strings(),
                "prefers-color-scheme",
                "dark"
        );
        assertEquals(4, input.readUnsignedByte());
        assertFeature(
                input,
                document.strings(),
                "prefers-reduced-motion",
                null
        );
        assertEquals(5, input.readUnsignedByte());
        assertFeature(
                input,
                document.strings(),
                "prefers-reduced-transparency",
                null
        );
        assertFalse(input.readBoolean());
    }

    /// Serializes JavaFX 26 interval media expressions into BSS version 8.
    @Test
    void compilesJavaFX26RangeMediaRules() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @media (400px <= width < 800px) {
                                  Pane { -fx-opacity: 1; }
                                }
                                """,
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX26)
        ).output());
        var input = document.input();

        assertEquals(8, document.version());
        input.readShort(); // origin
        assertEquals(1, input.readShort());
        assertTrue(input.readBoolean());
        assertEquals(1, input.readInt());
        assertEquals(3, input.readUnsignedByte());
        assertRange(input, document.strings(), 8, "width", 400, 8);
        assertRange(input, document.strings(), 9, "width", 800, 8);
        assertFalse(input.readBoolean());
    }

    /// Serializes JavaFX 27 platform media features into BSS version 9.
    @Test
    void compilesJavaFX27PlatformMediaRules() throws Exception {
        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromString(
                        """
                                @media (-fx-platform: windows) {
                                  Pane { -fx-opacity: 1; }
                                }
                                """,
                        Syntax.SCSS
                ),
                new BssTarget(JavaFXTarget.JAVAFX27)
        ).output());
        var input = document.input();

        assertEquals(9, document.version());
        assertEquals(0, input.readInt());
        input.readShort(); // origin
        assertEquals(1, input.readShort());
        assertTrue(input.readBoolean());
        assertEquals(1, input.readInt());
        assertFeature(input, document.strings(), "-fx-platform", "windows");
        assertFalse(input.readBoolean());
    }

    /// Resolves and embeds a conditional imported stylesheet in BSS version 9.
    @Test
    void compilesJavaFX27ConditionalImport(@TempDir Path directory) throws Exception {
        var imported = directory.resolve("theme.css");
        var root = directory.resolve("root.css");
        Files.writeString(
                imported,
                "ImportedPane { -fx-opacity: 0.75; }",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                root,
                """
                        @import "theme.css" (prefers-color-scheme: dark);
                        RootPane { -fx-opacity: 0.5; }
                        """,
                StandardCharsets.UTF_8
        );

        var result = new SassCompiler().compile(
                SassSource.fromFile(root),
                new BssTarget(JavaFXTarget.JAVAFX27)
        );
        var document = decodeDocument(result.output());
        var input = document.input();

        assertEquals(9, document.version());
        assertEquals(1, input.readInt());
        assertEquals(1, input.readInt());
        assertFeature(
                input,
                document.strings(),
                "prefers-color-scheme",
                "dark"
        );
        var importedLength = input.readInt();
        assertTrue(importedLength > 0);
        var importedBody = new DataInputStream(
                new ByteArrayInputStream(input.readNBytes(importedLength))
        );
        assertEquals(0, importedBody.readInt());
        importedBody.readShort(); // origin
        assertEquals(1, importedBody.readShort());
        assertEquals(
                java.util.Set.of(root.toRealPath().toUri(), imported.toRealPath().toUri()),
                result.loadedUrls()
        );
    }

    /// Flattens unconditional imports for BSS versions predating import framing.
    @Test
    void flattensImportForJavaFX17(@TempDir Path directory) throws Exception {
        var imported = directory.resolve("theme.css");
        var root = directory.resolve("root.css");
        Files.writeString(
                imported,
                "ImportedPane { -fx-opacity: 0.75; }",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                root,
                """
                        @import "theme.css";
                        RootPane { -fx-opacity: 0.5; }
                        """,
                StandardCharsets.UTF_8
        );

        var document = decodeDocument(new SassCompiler().compile(
                SassSource.fromFile(root),
                new BssTarget(JavaFXTarget.JAVAFX17)
        ).output());
        var input = document.input();

        assertEquals(6, document.version());
        input.readShort(); // origin
        assertEquals(2, input.readShort());
    }

    /// Rejects a recursive retained CSS import with the importing source range.
    @Test
    void rejectsRecursiveImport(@TempDir Path directory) throws Exception {
        var root = directory.resolve("root.css");
        Files.writeString(
                root,
                """
                        @import "root.css";
                        RootPane { -fx-opacity: 0.5; }
                        """,
                StandardCharsets.UTF_8
        );

        var failure = assertThrows(
                SassCompilationException.class,
                () -> new SassCompiler().compile(
                        SassSource.fromFile(root),
                        new BssTarget(JavaFXTarget.JAVAFX27)
                )
        );

        assertTrue(failure.getMessage().contains("Recursive JavaFX CSS import"));
        assertEquals(root.toRealPath().toUri(), failure.primaryDiagnostic().span().url());
    }

    /// Reads and verifies one discrete media feature expression.
    ///
    /// @param input   the body input
    /// @param strings the decoded global string table
    /// @param name    the expected feature name
    /// @param value   the expected feature value, or `null`
    private static void assertFeature(
            DataInputStream input,
            @Nullable String[] strings,
            String name,
            @Nullable String value
    ) throws Exception {
        assertEquals(2, input.readUnsignedByte());
        assertEquals(name, strings[input.readInt()]);
        var valueIndex = input.readInt();
        assertEquals(value, valueIndex < 0 ? null : strings[valueIndex]);
    }

    /// Reads and verifies one range media expression.
    ///
    /// @param input       the body input
    /// @param strings     the decoded global string table
    /// @param tag         the expected comparison tag
    /// @param name        the expected feature name
    /// @param value       the expected numeric value
    /// @param unitOrdinal the expected JavaFX size-unit ordinal
    private static void assertRange(
            DataInputStream input,
            @Nullable String[] strings,
            int tag,
            String name,
            double value,
            int unitOrdinal
    ) throws Exception {
        assertEquals(tag, input.readUnsignedByte());
        assertEquals(name, strings[input.readInt()]);
        assertEquals(value, input.readDouble());
        assertEquals(unitOrdinal, input.readByte());
    }

    /// Decodes a BSS header and global string table.
    ///
    /// @param buffer the complete BSS document
    /// @return the BSS version, strings, and input positioned at the body
    private static DecodedBss decodeDocument(
            @Unmodifiable ByteBuffer buffer
    ) throws Exception {
        var input = new DataInputStream(
                new ByteArrayInputStream(remainingBytes(buffer))
        );
        var version = input.readUnsignedShort();
        var count = input.readUnsignedShort();
        var nullIndex = input.readShort();
        var strings = new String[count];
        for (var index = 0; index < count; index++) {
            if (index != nullIndex) {
                strings[index] = input.readUTF();
            }
        }
        return new DecodedBss(version, strings, input);
    }

    /// Stores a decoded BSS header and body input.
    ///
    /// @param version the BSS format version
    /// @param strings the global string table
    /// @param input   the input positioned at the stylesheet body
    @NotNullByDefault
    private record DecodedBss(
            int version,
            @Nullable String[] strings,
            DataInputStream input
    ) {
        /// Validates decoded components.
        private DecodedBss {
            Objects.requireNonNull(strings, "strings");
            Objects.requireNonNull(input, "input");
        }
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
