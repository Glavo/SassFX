// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.embedded;

import com.sass_lang.embedded_protocol.CalculationOperator;
import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.ListSeparator;
import com.sass_lang.embedded_protocol.OutboundMessage;
import com.sass_lang.embedded_protocol.ProtocolErrorType;
import com.sass_lang.embedded_protocol.SingletonValue;
import com.sass_lang.embedded_protocol.Syntax;
import com.sass_lang.embedded_protocol.Value;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies Sass value callbacks through the framed Embedded Sass wire protocol.
///
/// These tests exercise global functions only. Importer, file-importer, and
/// first-class host-function callback behavior is covered separately.
@NotNullByDefault
final class EmbeddedValueCodecWireTest {
    /// Verifies all ordinary protocol values survive a global-function round trip.
    @Test
    void roundTripsOrdinaryGlobalFunctionValues() throws Exception {
        var source = """
                @use "sass:meta";

                a {
                  null-value: meta.inspect(round-trip(null));
                  boolean-value: meta.inspect(round-trip(true));
                  string-value: meta.inspect(round-trip("hello"));
                  number-value: meta.inspect(round-trip(12px));
                  list-value: meta.inspect(round-trip([1, 2]));
                  map-value: meta.inspect(round-trip((a: 1, b: 2)));
                  color-value: meta.inspect(round-trip(#0a141e));
                  calculation-value: meta.inspect(round-trip(calc(1% + 2px)));
                }
                """;
        var expectedCases = List.of(
                Value.ValueCase.SINGLETON,
                Value.ValueCase.SINGLETON,
                Value.ValueCase.STRING,
                Value.ValueCase.NUMBER,
                Value.ValueCase.LIST,
                Value.ValueCase.MAP,
                Value.ValueCase.COLOR,
                Value.ValueCase.CALCULATION
        );

        try (var harness = new CompilerHarness()) {
            harness.send(
                    21,
                    compileString(source, List.of("round-trip($value)"))
            );

            for (var index = 0; index < expectedCases.size(); index++) {
                var request = functionRequest(harness.receive(), 21);
                assertEquals("round-trip", request.getName());
                assertEquals(1, request.getArgumentsCount());
                var value = request.getArguments(0);
                assertEquals(expectedCases.get(index), value.getValueCase());
                verifyOrdinaryValue(index, value);
                harness.send(
                        21,
                        functionSuccess(request.getId(), value, List.of())
                );
            }

            var response = compileResponse(harness.receive(), 21);
            assertTrue(response.hasSuccess());
            var css = response.getSuccess().getCss();
            assertTrue(css.contains("null-value: null"));
            assertTrue(css.contains("boolean-value: true"));
            assertTrue(css.contains("string-value: \"hello\""));
            assertTrue(css.contains("number-value: 12px"));
            assertTrue(css.contains("list-value: [1, 2]"));
            assertTrue(css.contains("map-value: (a: 1, b: 2)"));
            assertTrue(css.contains("color-value:"));
            assertTrue(css.contains("calculation-value: calc(1% + 2px)"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Round-trips every public Protocol 3.2.0 color space through host and
    /// compiler value conversion.
    @Test
    void roundTripsAllProtocolColorSpaces() throws Exception {
        var spaces = List.of(
                "rgb",
                "hsl",
                "hwb",
                "srgb",
                "srgb-linear",
                "display-p3",
                "display-p3-linear",
                "a98-rgb",
                "prophoto-rgb",
                "rec2020",
                "xyz-d50",
                "xyz-d65",
                "lab",
                "lch",
                "oklab",
                "oklch"
        );

        try (var harness = new CompilerHarness()) {
            for (var index = 0; index < spaces.size(); index++) {
                var compilationId = 100L + index;
                harness.send(
                        compilationId,
                        compileString(
                                """
                                        @use "sass:meta";
                                        a {
                                          value: meta.inspect(
                                            round-trip(make-color())
                                          );
                                        }
                                        """,
                                List.of(
                                        "make-color()",
                                        "round-trip($value)"
                                )
                        )
                );

                var makeRequest = functionRequest(
                        harness.receive(),
                        compilationId
                );
                assertEquals("make-color", makeRequest.getName());
                var color = Value.newBuilder()
                        .setColor(
                                Value.Color.newBuilder()
                                        .setSpace(spaces.get(index))
                                        .setChannel1(index + 0.1)
                                        .setChannel2(index + 0.2)
                                        .setChannel3(index + 0.3)
                                        .setAlpha(0.5)
                        )
                        .build();
                harness.send(
                        compilationId,
                        functionSuccess(
                                makeRequest.getId(),
                                color,
                                List.of()
                        )
                );

                var roundTripRequest = functionRequest(
                        harness.receive(),
                        compilationId
                );
                assertEquals("round-trip", roundTripRequest.getName());
                var encoded = roundTripRequest.getArguments(0);
                assertEquals(Value.ValueCase.COLOR, encoded.getValueCase());
                var canonicalSpace = "xyz-d65".equals(spaces.get(index))
                        ? "xyz"
                        : spaces.get(index);
                assertEquals(
                        canonicalSpace,
                        encoded.getColor().getSpace()
                );
                assertTrue(encoded.getColor().hasChannel1());
                assertTrue(encoded.getColor().hasChannel2());
                assertTrue(encoded.getColor().hasChannel3());
                assertTrue(encoded.getColor().hasAlpha());
                assertEquals(0.5, encoded.getColor().getAlpha());
                harness.send(
                        compilationId,
                        functionSuccess(
                                roundTripRequest.getId(),
                                encoded,
                                List.of()
                        )
                );

                var response = compileResponse(
                        harness.receive(),
                        compilationId
                );
                assertTrue(response.hasSuccess());
            }

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies reporting an accessed argument-list ID consumes rest keywords.
    @Test
    void marksAccessedArgumentListIds() throws Exception {
        var source = """
                a {
                  value: consume(1, $named: 2);
                }
                """;

        try (var harness = new CompilerHarness()) {
            harness.send(
                    22,
                    compileString(source, List.of("consume($arguments...)"))
            );

            var request = functionRequest(harness.receive(), 22);
            assertEquals("consume", request.getName());
            assertEquals(1, request.getArgumentsCount());
            var value = request.getArguments(0);
            assertEquals(Value.ValueCase.ARGUMENT_LIST, value.getValueCase());
            var arguments = value.getArgumentList();
            assertTrue(arguments.getId() > 0);
            assertEquals(ListSeparator.COMMA, arguments.getSeparator());
            assertEquals(1, arguments.getContentsCount());
            assertEquals(1.0, arguments.getContents(0).getNumber().getValue());
            assertEquals(1, arguments.getKeywordsCount());
            assertEquals(
                    2.0,
                    arguments.getKeywordsOrThrow("named").getNumber().getValue()
            );

            var result = Value.newBuilder()
                    .setString(Value.String.newBuilder()
                            .setText("consumed")
                            .setQuoted(false))
                    .build();
            harness.send(
                    22,
                    functionSuccess(
                            request.getId(),
                            result,
                            List.of(arguments.getId())
                    )
            );

            var response = compileResponse(harness.receive(), 22);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss()
                    .contains("value: consumed"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Resolves a nonzero host result ID to the compiler-created argument
    /// list from the active function call.
    @Test
    void returnsCompilerArgumentListById() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    25,
                    compileString(
                            "a { value: echo-arguments(1, 2); }",
                            List.of("echo-arguments($arguments...)")
                    )
            );

            var request = functionRequest(harness.receive(), 25);
            var arguments = request.getArguments(0).getArgumentList();
            assertTrue(arguments.getId() > 0);
            assertEquals(2, arguments.getContentsCount());

            // Fields other than the ID are ignored for a compiler-owned
            // argument-list reference.
            var reference = Value.newBuilder()
                    .setArgumentList(
                            Value.ArgumentList.newBuilder()
                                    .setId(arguments.getId())
                                    .setSeparator(ListSeparator.UNDECIDED)
                                    .addContents(Value.newBuilder()
                                            .setSingleton(
                                                    SingletonValue.FALSE
                                            ))
                    )
                    .build();
            harness.send(
                    25,
                    functionSuccess(request.getId(), reference, List.of())
            );

            var response = compileResponse(harness.receive(), 25);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss()
                    .contains("value: 1, 2"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Allocates distinct IDs for repeated occurrences of the same compiler
    /// argument-list object.
    @Test
    void allocatesArgumentListIdsPerEncodedOccurrence() throws Exception {
        var source = """
                @function forward($arguments...) {
                  @return inspect-two(($arguments, $arguments));
                }

                a { value: forward($named: 1); }
                """;

        try (var harness = new CompilerHarness()) {
            harness.send(
                    26,
                    compileString(source, List.of("inspect-two($value)"))
            );

            var request = functionRequest(harness.receive(), 26);
            var pair = request.getArguments(0).getList();
            assertEquals(2, pair.getContentsCount());
            var first = pair.getContents(0).getArgumentList();
            var second = pair.getContents(1).getArgumentList();
            assertTrue(first.getId() > 0);
            assertTrue(second.getId() > 0);
            assertFalse(first.getId() == second.getId());

            harness.send(
                    26,
                    functionSuccess(
                            request.getId(),
                            Value.newBuilder()
                                    .setSingleton(SingletonValue.TRUE)
                                    .build(),
                            List.of(first.getId(), second.getId())
                    )
            );

            var response = compileResponse(harness.receive(), 26);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss()
                    .contains("value: true"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Accepts duplicate access reports for one compiler argument list.
    @Test
    void acceptsRepeatedAccessedArgumentListIds() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    27,
                    compileString(
                            "a { value: consume($named: 2); }",
                            List.of("consume($arguments...)")
                    )
            );

            var request = functionRequest(harness.receive(), 27);
            var id = request.getArguments(0).getArgumentList().getId();
            harness.send(
                    27,
                    functionSuccess(
                            request.getId(),
                            Value.newBuilder()
                                    .setSingleton(SingletonValue.TRUE)
                                    .build(),
                            List.of(id, id)
                    )
            );

            var response = compileResponse(harness.receive(), 27);
            assertTrue(response.hasSuccess());
            assertTrue(response.getSuccess().getCss()
                    .contains("value: true"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies compiler-owned function and mixin IDs resolve after host echo.
    @Test
    void roundTripsCompilerOwnedOpaqueValues() throws Exception {
        var source = """
                @use "sass:math";
                @use "sass:meta";

                @mixin framed($width) {
                  border-width: $width;
                }

                $function: opaque-round-trip(
                  meta.get-function("abs", $module: "math")
                );
                $mixin: opaque-round-trip(meta.get-mixin("framed"));

                a {
                  value: meta.call($function, -2);
                  @include meta.apply($mixin, 3px);
                }
                """;

        try (var harness = new CompilerHarness()) {
            harness.send(
                    24,
                    compileString(
                            source,
                            List.of("opaque-round-trip($value)")
                    )
            );

            var functionRequest = functionRequest(harness.receive(), 24);
            assertEquals("opaque-round-trip", functionRequest.getName());
            assertEquals(1, functionRequest.getArgumentsCount());
            var function = functionRequest.getArguments(0);
            assertEquals(
                    Value.ValueCase.COMPILER_FUNCTION,
                    function.getValueCase()
            );
            assertEquals(0, function.getCompilerFunction().getId());
            harness.send(
                    24,
                    functionSuccess(
                            functionRequest.getId(),
                            function,
                            List.of()
                    )
            );

            var mixinRequest = functionRequest(harness.receive(), 24);
            assertEquals("opaque-round-trip", mixinRequest.getName());
            assertEquals(1, mixinRequest.getArgumentsCount());
            var mixin = mixinRequest.getArguments(0);
            assertEquals(
                    Value.ValueCase.COMPILER_MIXIN,
                    mixin.getValueCase()
            );
            assertEquals(0, mixin.getCompilerMixin().getId());
            harness.send(
                    24,
                    functionSuccess(
                            mixinRequest.getId(),
                            mixin,
                            List.of()
                    )
            );

            var response = compileResponse(harness.receive(), 24);
            assertTrue(response.hasSuccess());
            var css = response.getSuccess().getCss();
            assertTrue(css.contains("value: 2"));
            assertTrue(css.contains("border-width: 3px"));

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Verifies a function response without a Value oneof terminates the endpoint.
    @Test
    void rejectsUnsetFunctionResultValue() throws Exception {
        assertFatalFunctionResult(
                Value.getDefaultInstance(),
                "Missing mandatory field Value.value"
        );
    }

    /// Verifies a function response without its result oneof terminates the
    /// endpoint.
    @Test
    void rejectsUnsetFunctionCallResponseResult() throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    31,
                    compileString(
                            "a { value: missing-result(); }",
                            List.of("missing-result()")
                    )
            );

            var request = functionRequest(harness.receive(), 31);
            harness.send(
                    31,
                    InboundMessage.newBuilder()
                            .setFunctionCallResponse(
                                    InboundMessage.FunctionCallResponse
                                            .newBuilder()
                                            .setId(request.getId())
                            )
                            .build()
            );

            var received = harness.receive();
            assertEquals(31, received.compilationId());
            assertTrue(received.message().hasError());
            var error = received.message().getError();
            assertEquals(ProtocolErrorType.PARAMS, error.getType());
            assertEquals(
                    "Missing mandatory field FunctionCallResponse.result",
                    error.getMessage()
            );
            assertEquals(
                    EmbeddedCompiler.PROTOCOL_EXIT_STATUS,
                    harness.awaitStatus()
            );
        }
    }

    /// Verifies an unknown compiler-function ID terminates the endpoint.
    @Test
    void rejectsUnknownCompilerFunctionId() throws Exception {
        assertFatalFunctionResult(
                Value.newBuilder()
                        .setCompilerFunction(
                                Value.CompilerFunction.newBuilder().setId(91)
                        )
                        .build(),
                "CompilerFunction.id 91 doesn't match any known functions"
        );
    }

    /// Verifies an unknown compiler-mixin ID terminates the endpoint.
    @Test
    void rejectsUnknownCompilerMixinId() throws Exception {
        assertFatalFunctionResult(
                Value.newBuilder()
                        .setCompilerMixin(
                                Value.CompilerMixin.newBuilder().setId(91)
                        )
                        .build(),
                "CompilerMixin.id 91 doesn't match any known mixins"
        );
    }

    /// Verifies an unknown compiler argument-list reference terminates the
    /// endpoint.
    @Test
    void rejectsUnknownCompilerArgumentListId() throws Exception {
        assertFatalFunctionResult(
                Value.newBuilder()
                        .setArgumentList(
                                Value.ArgumentList.newBuilder().setId(91)
                        )
                        .build(),
                "Value.ArgumentList.id 91 doesn't match any known "
                        + "argument lists"
        );
    }

    /// Verifies zero cannot be reported as an accessed compiler argument-list
    /// ID.
    @Test
    void rejectsZeroAccessedArgumentListId() throws Exception {
        assertFatalAccessedArgumentListId(
                0,
                "Value.ArgumentList.id 0 can't be marked as accessed"
        );
    }

    /// Verifies an unknown accessed compiler argument-list ID terminates the
    /// endpoint.
    @Test
    void rejectsUnknownAccessedArgumentListId() throws Exception {
        assertFatalAccessedArgumentListId(
                91,
                "Value.ArgumentList.id 91 doesn't match any known "
                        + "argument lists"
        );
    }

    /// Verifies host-created multi-element argument lists require an explicit
    /// separator.
    @Test
    void rejectsUndecidedMultiElementArgumentList() throws Exception {
        var result = Value.newBuilder()
                .setArgumentList(
                        Value.ArgumentList.newBuilder()
                                .setSeparator(ListSeparator.UNDECIDED)
                                .addContents(Value.newBuilder()
                                        .setSingleton(SingletonValue.TRUE))
                                .addContents(Value.newBuilder()
                                        .setSingleton(SingletonValue.FALSE))
                )
                .build();
        assertFatalFunctionResultEndingWith(
                result,
                "can't have an undecided separator because it has 2 elements"
        );
    }

    /// Verifies calculation names, nested values, and argument counts use
    /// Embedded Sass parameter errors.
    @Test
    void validatesHostCalculationStructure() throws Exception {
        var one = Value.Calculation.CalculationValue.newBuilder()
                .setNumber(Value.Number.newBuilder().setValue(1))
                .build();
        var two = Value.Calculation.CalculationValue.newBuilder()
                .setNumber(Value.Number.newBuilder().setValue(2))
                .build();
        var three = Value.Calculation.CalculationValue.newBuilder()
                .setNumber(Value.Number.newBuilder().setValue(3))
                .build();
        var four = Value.Calculation.CalculationValue.newBuilder()
                .setNumber(Value.Number.newBuilder().setValue(4))
                .build();

        assertFatalFunctionResult(
                calculation("calc", List.of()),
                "Value.Calculation.arguments must have exactly one "
                        + "argument for calc()."
        );
        assertFatalFunctionResult(
                calculation("calc", List.of(one, two)),
                "Value.Calculation.arguments must have exactly one "
                        + "argument for calc()."
        );
        assertFatalFunctionResult(
                calculation("min", List.of()),
                "Value.Calculation.arguments must have at least 1 "
                        + "argument for min()."
        );
        assertFatalFunctionResult(
                calculation("max", List.of()),
                "Value.Calculation.arguments must have at least 1 "
                        + "argument for max()."
        );
        assertFatalFunctionResult(
                calculation("clamp", List.of()),
                "Value.Calculation.arguments must have 1 to 3 arguments "
                        + "for clamp()."
        );
        assertFatalFunctionResult(
                calculation("clamp", List.of(one, two, three, four)),
                "Value.Calculation.arguments must have 1 to 3 arguments "
                        + "for clamp()."
        );
        assertFatalFunctionResult(
                calculation("round", List.of(one)),
                "Value.Calculation.name \"round\" is not a recognized "
                        + "calculation type."
        );
        assertFatalFunctionResult(
                calculation(
                        "calc",
                        List.of(Value.Calculation.CalculationValue
                                .getDefaultInstance())
                ),
                "Missing mandatory field Value.Calculation.value"
        );
        assertFatalFunctionResult(
                calculation(
                        "calc",
                        List.of(
                                Value.Calculation.CalculationValue.newBuilder()
                                        .setOperation(
                                                Value.Calculation
                                                        .CalculationOperation
                                                        .newBuilder()
                                                        .setOperator(
                                                                CalculationOperator
                                                                        .PLUS
                                                        )
                                                        .setLeft(one)
                                        )
                                        .build()
                        )
                ),
                "Missing mandatory field Value.Calculation.value"
        );
    }

    /// Reports calculation simplification errors as compilation failures
    /// without terminating the protocol connection.
    @Test
    void reportsHostCalculationSimplificationFailure() throws Exception {
        var pixels = Value.Calculation.CalculationValue.newBuilder()
                .setNumber(Value.Number.newBuilder()
                        .setValue(1)
                        .addNumerators("px"))
                .build();
        var seconds = Value.Calculation.CalculationValue.newBuilder()
                .setNumber(Value.Number.newBuilder()
                        .setValue(2)
                        .addNumerators("s"))
                .build();

        try (var harness = new CompilerHarness()) {
            harness.send(
                    29,
                    compileString(
                            "a { value: invalid-calculation(); }",
                            List.of("invalid-calculation()")
                    )
            );

            var request = functionRequest(harness.receive(), 29);
            harness.send(
                    29,
                    functionSuccess(
                            request.getId(),
                            calculation("min", List.of(pixels, seconds)),
                            List.of()
                    )
            );

            var response = compileResponse(harness.receive(), 29);
            assertTrue(response.hasFailure());
            assertEquals(
                    "1px and 2s are incompatible.",
                    response.getFailure().getMessage()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Reports unknown color spaces as compilation failures while preserving
    /// the connection.
    @Test
    void reportsUnknownHostColorSpaceAsCompilationFailure() throws Exception {
        assertCompilationFailureResult(
                Value.newBuilder()
                        .setColor(Value.Color.newBuilder().setSpace("unknown"))
                        .build(),
                "Unknown color space \"unknown\"."
        );
    }

    /// Rejects out-of-range color alpha as malformed host protocol data.
    @Test
    void rejectsOutOfRangeHostColorAlpha() throws Exception {
        assertFatalFunctionResult(
                Value.newBuilder()
                        .setColor(
                                Value.Color.newBuilder()
                                        .setSpace("rgb")
                                        .setChannel1(0)
                                        .setChannel2(0)
                                        .setChannel3(0)
                                        .setAlpha(-0.1)
                        )
                        .build(),
                "Color.alpha must be between 0 and 1, was -0.1"
        );
    }

    /// Reports invalid host-function signatures as ordinary compilation
    /// failures and keeps serving the connection.
    @Test
    void reportsInvalidHostFunctionSignaturesAsCompilationFailures()
            throws Exception {
        var signatures = List.of(
                "",
                "foo",
                "foo($bar",
                "foo() ",
                "foo($)"
        );
        var messages = List.of(
                "Invalid signature \"\": Expected identifier.",
                "Invalid signature \"foo\": expected \"(\".",
                "Invalid signature \"foo($bar\": expected \")\".",
                "Invalid signature \"foo() \": expected no more input.",
                "Invalid signature \"foo($)\": Expected identifier."
        );

        try (var harness = new CompilerHarness()) {
            for (var index = 0; index < signatures.size(); index++) {
                var compilationId = 40L + index;
                harness.send(
                        compilationId,
                        compileString(
                                """
                                        @use "sass:meta";
                                        a { value: meta.inspect(make-host()); }
                                        """,
                                List.of("make-host()")
                        )
                );

                var request = functionRequest(
                        harness.receive(),
                        compilationId
                );
                var function = Value.newBuilder()
                        .setHostFunction(
                                Value.HostFunction.newBuilder()
                                        .setId(1234)
                                        .setSignature(signatures.get(index))
                        )
                        .build();
                harness.send(
                        compilationId,
                        functionSuccess(
                                request.getId(),
                                function,
                                List.of()
                        )
                );

                var response = compileResponse(
                        harness.receive(),
                        compilationId
                );
                assertTrue(response.hasFailure());
                assertEquals(
                        messages.get(index),
                        response.getFailure().getMessage()
                );
            }

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Treats unrecognized protocol enums as Sass compilation failures rather
    /// than connection-level parameter errors.
    @Test
    void reportsUnknownValueEnumsAsCompilationFailures() throws Exception {
        assertCompilationFailureResult(
                Value.newBuilder().setSingletonValue(91).build(),
                "Unknown Value.singleton UNRECOGNIZED"
        );
        assertCompilationFailureResult(
                Value.newBuilder()
                        .setList(
                                Value.List.newBuilder()
                                        .setSeparatorValue(91)
                        )
                        .build(),
                "Unknown ListSeparator UNRECOGNIZED"
        );
        var number = Value.Calculation.CalculationValue.newBuilder()
                .setNumber(Value.Number.newBuilder().setValue(1))
                .build();
        var operation = Value.Calculation.CalculationValue.newBuilder()
                .setOperation(
                        Value.Calculation.CalculationOperation.newBuilder()
                                .setOperatorValue(91)
                                .setLeft(number)
                                .setRight(number)
                )
                .build();
        assertCompilationFailureResult(
                calculation("calc", List.of(operation)),
                "Unknown CalculationOperator UNRECOGNIZED"
        );
    }

    /// Verifies the detailed fields of one ordinary value.
    ///
    /// @param index the value's position in the source sequence
    /// @param value the encoded callback argument
    private static void verifyOrdinaryValue(int index, Value value) {
        switch (index) {
            case 0 -> assertEquals(SingletonValue.NULL, value.getSingleton());
            case 1 -> assertEquals(SingletonValue.TRUE, value.getSingleton());
            case 2 -> {
                assertEquals("hello", value.getString().getText());
                assertTrue(value.getString().getQuoted());
            }
            case 3 -> {
                assertEquals(12.0, value.getNumber().getValue());
                assertEquals(List.of("px"), value.getNumber().getNumeratorsList());
                assertTrue(value.getNumber().getDenominatorsList().isEmpty());
            }
            case 4 -> {
                assertEquals(ListSeparator.COMMA, value.getList().getSeparator());
                assertTrue(value.getList().getHasBrackets());
                assertEquals(2, value.getList().getContentsCount());
            }
            case 5 -> {
                assertEquals(2, value.getMap().getEntriesCount());
                assertEquals(
                        "a",
                        value.getMap().getEntries(0).getKey().getString().getText()
                );
                assertEquals(
                        1.0,
                        value.getMap().getEntries(0).getValue()
                                .getNumber().getValue()
                );
            }
            case 6 -> {
                var color = value.getColor();
                assertEquals("rgb", color.getSpace());
                assertTrue(color.hasChannel1());
                assertTrue(color.hasChannel2());
                assertTrue(color.hasChannel3());
                assertTrue(color.hasAlpha());
                assertEquals(10.0, color.getChannel1());
                assertEquals(20.0, color.getChannel2());
                assertEquals(30.0, color.getChannel3());
                assertEquals(1.0, color.getAlpha());
            }
            case 7 -> {
                var calculation = value.getCalculation();
                assertEquals("calc", calculation.getName());
                assertEquals(1, calculation.getArgumentsCount());
                var operation = calculation.getArguments(0).getOperation();
                assertEquals(CalculationOperator.PLUS, operation.getOperator());
                assertTrue(operation.hasLeft());
                assertTrue(operation.hasRight());
            }
            default -> throw new AssertionError(
                    "Unexpected ordinary value index " + index
            );
        }
    }

    /// Creates one protocol calculation value.
    ///
    /// @param name the calculation function name
    /// @param arguments the encoded calculation arguments
    /// @return the complete protocol value
    private static Value calculation(
            String name,
            @Unmodifiable List<
                    Value.Calculation.CalculationValue
                    > arguments
    ) {
        return Value.newBuilder()
                .setCalculation(
                        Value.Calculation.newBuilder()
                                .setName(name)
                                .addAllArguments(arguments)
                )
                .build();
    }

    /// Runs one malformed function result and verifies a fatal parameter error.
    ///
    /// @param result the malformed or unknown result value
    /// @param expectedMessage the protocol-error detail
    private static void assertFatalFunctionResult(
            Value result,
            String expectedMessage
    ) throws Exception {
        assertFatalFunctionResult(result, expectedMessage, false);
    }

    /// Runs one malformed function result and verifies the suffix of its fatal
    /// parameter error.
    ///
    /// @param result the malformed result value
    /// @param expectedSuffix the protocol-error message suffix
    private static void assertFatalFunctionResultEndingWith(
            Value result,
            String expectedSuffix
    ) throws Exception {
        assertFatalFunctionResult(result, expectedSuffix, true);
    }

    /// Runs one malformed function result and verifies its fatal parameter
    /// error.
    ///
    /// @param result the malformed or unknown result value
    /// @param expectedMessage the complete message or required suffix
    /// @param suffixOnly whether only the message suffix is compared
    private static void assertFatalFunctionResult(
            Value result,
            String expectedMessage,
            boolean suffixOnly
    ) throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    23,
                    compileString(
                            "a {value: invalid-result()}",
                            List.of("invalid-result()")
                    )
            );

            var request = functionRequest(harness.receive(), 23);
            assertEquals("invalid-result", request.getName());
            assertEquals(0, request.getArgumentsCount());
            harness.send(
                    23,
                    functionSuccess(request.getId(), result, List.of())
            );

            var received = harness.receive();
            assertEquals(23, received.compilationId());
            assertTrue(received.message().hasError());
            var error = received.message().getError();
            assertEquals(ProtocolErrorType.PARAMS, error.getType());
            if (suffixOnly) {
                assertTrue(
                        error.getMessage().endsWith(expectedMessage),
                        error.getMessage()
                );
            } else {
                assertEquals(expectedMessage, error.getMessage());
            }
            assertEquals(
                    0xffff_ffffL,
                    Integer.toUnsignedLong(error.getId())
            );
            assertEquals(
                    EmbeddedCompiler.PROTOCOL_EXIT_STATUS,
                    harness.awaitStatus()
            );
        }
    }

    /// Runs one malformed accessed-argument-list report and verifies its fatal
    /// parameter error.
    ///
    /// @param id the invalid accessed ID
    /// @param expectedMessage the protocol-error detail
    private static void assertFatalAccessedArgumentListId(
            int id,
            String expectedMessage
    ) throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    28,
                    compileString(
                            "a { value: invalid-access($named: 1); }",
                            List.of("invalid-access($arguments...)")
                    )
            );

            var request = functionRequest(harness.receive(), 28);
            harness.send(
                    28,
                    functionSuccess(
                            request.getId(),
                            Value.newBuilder()
                                    .setSingleton(SingletonValue.TRUE)
                                    .build(),
                            List.of(id)
                    )
            );

            var received = harness.receive();
            assertEquals(28, received.compilationId());
            assertTrue(received.message().hasError());
            var error = received.message().getError();
            assertEquals(ProtocolErrorType.PARAMS, error.getType());
            assertEquals(expectedMessage, error.getMessage());
            assertEquals(
                    0xffff_ffffL,
                    Integer.toUnsignedLong(error.getId())
            );
            assertEquals(
                    EmbeddedCompiler.PROTOCOL_EXIT_STATUS,
                    harness.awaitStatus()
            );
        }
    }

    /// Runs one semantically invalid host value and verifies an ordinary
    /// compilation failure with a clean endpoint exit.
    ///
    /// @param result the host function result
    /// @param expectedMessage the compilation failure message
    private static void assertCompilationFailureResult(
            Value result,
            String expectedMessage
    ) throws Exception {
        try (var harness = new CompilerHarness()) {
            harness.send(
                    30,
                    compileString(
                            "a { value: semantic-failure(); }",
                            List.of("semantic-failure()")
                    )
            );

            var request = functionRequest(harness.receive(), 30);
            harness.send(
                    30,
                    functionSuccess(request.getId(), result, List.of())
            );

            var response = compileResponse(harness.receive(), 30);
            assertTrue(response.hasFailure());
            assertEquals(
                    expectedMessage,
                    response.getFailure().getMessage()
            );

            harness.closeInput();
            assertEquals(0, harness.awaitStatus());
        }
    }

    /// Creates one SCSS string compilation with global host functions.
    ///
    /// @param source the SCSS source
    /// @param globalFunctions complete global-function signatures
    /// @return the inbound compile request wrapper
    private static InboundMessage compileString(
            String source,
            @Unmodifiable List<String> globalFunctions
    ) {
        var input = InboundMessage.CompileRequest.StringInput.newBuilder()
                .setSource(source)
                .setSyntax(Syntax.SCSS);
        return InboundMessage.newBuilder()
                .setCompileRequest(
                        InboundMessage.CompileRequest.newBuilder()
                                .setString(input)
                                .addAllGlobalFunctions(globalFunctions)
                )
                .build();
    }

    /// Creates one successful host function response.
    ///
    /// @param requestId the callback request ID
    /// @param value the returned Sass value
    /// @param accessedArgumentLists accessed compiler argument-list IDs
    /// @return the inbound response wrapper
    private static InboundMessage functionSuccess(
            int requestId,
            Value value,
            @Unmodifiable List<Integer> accessedArgumentLists
    ) {
        return InboundMessage.newBuilder()
                .setFunctionCallResponse(
                        InboundMessage.FunctionCallResponse.newBuilder()
                                .setId(requestId)
                                .setSuccess(value)
                                .addAllAccessedArgumentLists(
                                        accessedArgumentLists
                                )
                )
                .build();
    }

    /// Extracts one outbound global-function callback.
    ///
    /// @param received the framed outbound message
    /// @param expectedCompilationId the compilation wire ID
    /// @return the function request
    private static OutboundMessage.FunctionCallRequest functionRequest(
            Received received,
            long expectedCompilationId
    ) {
        assertEquals(expectedCompilationId, received.compilationId());
        assertTrue(received.message().hasFunctionCallRequest());
        return received.message().getFunctionCallRequest();
    }

    /// Extracts one terminal compile response.
    ///
    /// @param received the framed outbound message
    /// @param expectedCompilationId the compilation wire ID
    /// @return the compile response
    private static OutboundMessage.CompileResponse compileResponse(
            Received received,
            long expectedCompilationId
    ) {
        assertEquals(expectedCompilationId, received.compilationId());
        assertTrue(received.message().hasCompileResponse());
        return received.message().getCompileResponse();
    }

    /// Runs the blocking endpoint against connected host-side streams.
    @NotNullByDefault
    private static final class CompilerHarness implements Closeable {
        /// Receives bytes written by the host.
        private final PipedInputStream compilerInput;

        /// Sends bytes from the host to the compiler.
        private final PipedOutputStream hostOutput;

        /// Receives bytes written by the compiler.
        private final PipedInputStream hostInput;

        /// Sends bytes from the compiler to the host.
        private final PipedOutputStream compilerOutput;

        /// Runs the blocking compiler endpoint.
        private final ExecutorService compilerExecutor;

        /// Bounds blocking host reads.
        private final ExecutorService readerExecutor;

        /// Completes with the endpoint status.
        private final Future<Integer> status;

        /// Records whether the host input side is closed.
        private boolean inputClosed;

        /// Creates and starts one connected endpoint.
        private CompilerHarness() throws IOException {
            compilerInput = new PipedInputStream(1 << 20);
            hostOutput = new PipedOutputStream(compilerInput);
            hostInput = new PipedInputStream(1 << 20);
            compilerOutput = new PipedOutputStream(hostInput);
            compilerExecutor = Executors.newSingleThreadExecutor();
            readerExecutor = Executors.newSingleThreadExecutor();
            status = compilerExecutor.submit(
                    () -> new EmbeddedCompiler().run(
                            compilerInput,
                            compilerOutput
                    )
            );
        }

        /// Sends one framed inbound message.
        ///
        /// @param compilationId the packet compilation ID
        /// @param message the inbound wrapper
        private void send(long compilationId, InboundMessage message)
                throws IOException {
            EmbeddedPacketIO.write(
                    hostOutput,
                    compilationId,
                    message.toByteArray()
            );
        }

        /// Reads one framed outbound message with a finite timeout.
        ///
        /// @return the decoded packet and wrapper
        private Received receive() throws Exception {
            var read = readerExecutor.submit(
                    () -> EmbeddedPacketIO.read(hostInput)
            );
            final @Nullable EmbeddedPacketIO.Packet packet;
            try {
                packet = read.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                var cause = failure.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw failure;
            } catch (TimeoutException failure) {
                read.cancel(true);
                throw new AssertionError(
                        "Timed out waiting for an embedded compiler packet.",
                        failure
                );
            }
            var present = assertInstanceOf(
                    EmbeddedPacketIO.Packet.class,
                    packet
            );
            return new Received(
                    present.compilationId(),
                    OutboundMessage.parseFrom(present.message())
            );
        }

        /// Closes host input to signal clean endpoint EOF.
        private void closeInput() throws IOException {
            if (inputClosed) {
                return;
            }
            inputClosed = true;
            hostOutput.close();
        }

        /// Waits for the endpoint process-compatible status.
        ///
        /// @return the endpoint status
        private int awaitStatus() throws Exception {
            try {
                return status.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                var cause = failure.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw failure;
            }
        }

        /// Closes streams and stops helper executors.
        @Override
        public void close() throws IOException {
            closeInput();
            try {
                status.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException ignored) {
                // The requesting test reports endpoint failures.
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ignored) {
                // Executor shutdown below terminates a stalled endpoint.
            } finally {
                hostInput.close();
                compilerOutput.close();
                compilerInput.close();
                readerExecutor.shutdownNow();
                compilerExecutor.shutdownNow();
            }
        }
    }

    /// Contains one decoded compiler packet.
    ///
    /// @param compilationId the packet compilation ID
    /// @param message the outbound wrapper
    @NotNullByDefault
    private record Received(
            long compilationId,
            OutboundMessage message
    ) {
        /// Creates one received packet.
        private Received {
        }
    }
}
