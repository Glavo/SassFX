// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import com.sass_lang.embedded_protocol.CalculationOperator;
import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.ListSeparator;
import com.sass_lang.embedded_protocol.OutboundMessage;
import com.sass_lang.embedded_protocol.ProtocolErrorType;
import com.sass_lang.embedded_protocol.SingletonValue;
import com.sass_lang.embedded_protocol.Value;
import org.glavo.sassfx.SassCalculationValue;
import org.glavo.sassfx.SassColorSpace;
import org.glavo.sassfx.SassCustomFunction;
import org.glavo.sassfx.SassListSeparator;
import org.glavo.sassfx.SassValue;
import org.glavo.sassfx.SassValueException;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Converts Sass values between SassFX and Embedded Sass Protocol 3.2.0.
///
/// Function and mixin registries live for one compilation. Argument-list IDs
/// live for one function call and are discarded after its response.
@NotNullByDefault
final class EmbeddedValueCodec {
    /// Routes host-function calls for this compilation.
    private final EmbeddedCompilationDispatcher dispatcher;

    /// Maps compiler function identities to protocol IDs.
    private final Map<SassValue, Integer> functionIds = new HashMap<>();

    /// Maps protocol IDs back to compiler function identities.
    private final Map<Integer, SassValue> functions = new HashMap<>();

    /// Maps compiler mixin identities to protocol IDs.
    private final Map<SassValue, Integer> mixinIds = new HashMap<>();

    /// Maps protocol IDs back to compiler mixin identities.
    private final Map<Integer, SassValue> mixins = new HashMap<>();

    /// Creates a value codec for one compilation.
    ///
    /// @param dispatcher the host callback dispatcher
    EmbeddedValueCodec(EmbeddedCompilationDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /// Calls a global host function by name.
    ///
    /// @param name the normalized function name
    /// @param arguments the bound Sass arguments
    /// @return the host's Sass result
    /// @throws IOException if the host reports an ordinary function error
    SassValue callByName(
            String name,
            @Unmodifiable List<SassValue> arguments
    ) throws IOException {
        Objects.requireNonNull(name, "name");
        return call(name, null, arguments);
    }

    /// Calls a host function by its opaque host-assigned ID.
    ///
    /// @param functionId the host function ID
    /// @param arguments the bound Sass arguments
    /// @return the host's Sass result
    /// @throws IOException if the host reports an ordinary function error
    private SassValue callById(
            int functionId,
            @Unmodifiable List<SassValue> arguments
    ) throws IOException {
        return call(null, functionId, arguments);
    }

    /// Sends one host function callback and decodes its response.
    private SassValue call(
            @Nullable String name,
            @Nullable Integer functionId,
            @Unmodifiable List<SassValue> arguments
    ) throws IOException {
        Objects.requireNonNull(arguments, "arguments");
        var context = new EncodingContext();
        var request = OutboundMessage.FunctionCallRequest.newBuilder()
                .setId(0);
        if (name != null) {
            request.setName(name);
        } else {
            request.setFunctionId(Objects.requireNonNull(
                    functionId,
                    "functionId"
            ));
        }
        for (var argument : arguments) {
            request.addArguments(encode(argument, context));
        }

        var response = dispatcher.request(
                OutboundMessage.newBuilder()
                        .setFunctionCallRequest(request)
                        .build(),
                InboundMessage.MessageCase.FUNCTION_CALL_RESPONSE
        ).getFunctionCallResponse();
        markAccessedArgumentLists(
                response.getAccessedArgumentListsList(),
                context.argumentLists
        );
        return switch (response.getResultCase()) {
            case SUCCESS -> decode(response.getSuccess(), context);
            case ERROR -> throw new IOException(response.getError());
            case RESULT_NOT_SET -> throw protocolError(
                    "Missing mandatory field FunctionCallResponse.result"
            );
        };
    }

    /// Encodes one public Sass value for a function request.
    ///
    /// @param value the Sass value
    /// @param context the request-local encoding context
    /// @return the protocol value
    private Value encode(SassValue value, EncodingContext context) {
        Objects.requireNonNull(value, "value");
        var result = Value.newBuilder();
        return switch (value.type()) {
            case STRING -> result.setString(Value.String.newBuilder()
                    .setText(value.stringValue())
                    .setQuoted(value.isQuoted())).build();
            case NUMBER -> result.setNumber(encodeNumber(value)).build();
            case BOOLEAN -> result.setSingleton(
                    value.booleanValue()
                            ? SingletonValue.TRUE
                            : SingletonValue.FALSE
            ).build();
            case NULL -> result.setSingleton(SingletonValue.NULL).build();
            case ARGUMENT_LIST -> {
                var id = context.idFor(value);
                var list = Value.ArgumentList.newBuilder()
                        .setId(id)
                        .setSeparator(separator(value.separator()));
                for (var element : value.asList()) {
                    list.addContents(encode(element, context));
                }
                for (var entry : value.keywordContents().entrySet()) {
                    list.putKeywords(
                            entry.getKey(),
                            encode(entry.getValue(), context)
                    );
                }
                yield result.setArgumentList(list).build();
            }
            case LIST -> {
                var encoded = Value.List.newBuilder()
                        .setSeparator(separator(value.separator()))
                        .setHasBrackets(value.isBracketed());
                for (var element : value.asList()) {
                    encoded.addContents(encode(element, context));
                }
                yield result.setList(encoded).build();
            }
            case MAP -> {
                var encoded = Value.Map.newBuilder();
                for (var entry : value.mapContents().entrySet()) {
                    encoded.addEntries(Value.Map.Entry.newBuilder()
                            .setKey(encode(entry.getKey(), context))
                            .setValue(encode(entry.getValue(), context)));
                }
                yield result.setMap(encoded).build();
            }
            case COLOR -> {
                var encoded = Value.Color.newBuilder()
                        .setSpace(value.colorSpace().cssName());
                @Nullable var channel1 = value.colorChannelOrNull(0);
                @Nullable var channel2 = value.colorChannelOrNull(1);
                @Nullable var channel3 = value.colorChannelOrNull(2);
                @Nullable var alpha = value.colorChannelOrNull(3);
                if (channel1 != null) {
                    encoded.setChannel1(channel1);
                }
                if (channel2 != null) {
                    encoded.setChannel2(channel2);
                }
                if (channel3 != null) {
                    encoded.setChannel3(channel3);
                }
                if (alpha != null) {
                    encoded.setAlpha(alpha);
                }
                yield result.setColor(encoded).build();
            }
            case CALCULATION -> result.setCalculation(
                    encodeCalculation(value)
            ).build();
            case FUNCTION -> {
                var id = functionIds.computeIfAbsent(value, ignored -> {
                    var next = functionIds.size();
                    functions.put(next, value);
                    return next;
                });
                yield result.setCompilerFunction(
                        Value.CompilerFunction.newBuilder().setId(id)
                ).build();
            }
            case MIXIN -> {
                var id = mixinIds.computeIfAbsent(value, ignored -> {
                    var next = mixinIds.size();
                    mixins.put(next, value);
                    return next;
                });
                yield result.setCompilerMixin(
                        Value.CompilerMixin.newBuilder().setId(id)
                ).build();
            }
        };
    }

    /// Encodes a Sass number.
    private static Value.Number encodeNumber(SassValue number) {
        return Value.Number.newBuilder()
                .setValue(number.numberValue())
                .addAllNumerators(number.numeratorUnits())
                .addAllDenominators(number.denominatorUnits())
                .build();
    }

    /// Encodes a CSS calculation.
    private Value.Calculation encodeCalculation(SassValue calculation) {
        var result = Value.Calculation.newBuilder()
                .setName(calculation.calculationName());
        for (var argument : calculation.calculationArguments()) {
            result.addArguments(encodeCalculationValue(argument));
        }
        return result.build();
    }

    /// Encodes one calculation argument.
    private Value.Calculation.CalculationValue encodeCalculationValue(
            SassCalculationValue argument
    ) {
        var result = Value.Calculation.CalculationValue.newBuilder();
        if (argument instanceof SassCalculationValue.Value wrapped) {
            var value = wrapped.value();
            if (value.type() == org.glavo.sassfx.SassValueType.NUMBER) {
                return result.setNumber(encodeNumber(value)).build();
            }
            return result.setCalculation(encodeCalculation(value)).build();
        }
        if (argument instanceof SassCalculationValue.StringValue string) {
            return result.setString(string.text()).build();
        }
        if (argument instanceof SassCalculationValue.Operation operation) {
            return result.setOperation(
                    Value.Calculation.CalculationOperation.newBuilder()
                            .setOperator(switch (operation.operator()) {
                                case PLUS -> CalculationOperator.PLUS;
                                case MINUS -> CalculationOperator.MINUS;
                                case TIMES -> CalculationOperator.TIMES;
                                case DIVIDED_BY -> CalculationOperator.DIVIDE;
                            })
                            .setLeft(encodeCalculationValue(
                                    operation.left()
                            ))
                            .setRight(encodeCalculationValue(
                                    operation.right()
                            ))
            ).build();
        }
        throw protocolError(
                "Unsupported calculation argument: "
                        + argument.getClass().getName()
        );
    }

    /// Decodes one host-provided Sass value.
    ///
    /// @param value the protocol value
    /// @param context the function-call context that owns compiler argument lists
    /// @return the public Sass value
    private SassValue decode(Value value, EncodingContext context) {
        try {
            return decodeUnchecked(value, context);
        } catch (EmbeddedProtocolException failure) {
            throw failure;
        } catch (SassValueException failure) {
            // Calculation simplification errors are ordinary Sass evaluation
            // failures rather than malformed host protocol data.
            throw failure;
        } catch (RuntimeException failure) {
            throw protocolError(Objects.requireNonNullElse(
                    failure.getMessage(),
                    failure.getClass().getName()
            ));
        }
    }

    /// Decodes one host value after installing protocol-error translation.
    ///
    /// @param value the protocol value
    /// @param context the function-call context that owns compiler argument lists
    /// @return the decoded Sass value
    private SassValue decodeUnchecked(
            Value value,
            EncodingContext context
    ) {
        return switch (value.getValueCase()) {
            case STRING -> SassValue.string(
                    value.getString().getText(),
                    value.getString().getQuoted()
            );
            case NUMBER -> decodeNumber(value.getNumber());
            case SINGLETON -> switch (value.getSingleton()) {
                case TRUE -> SassValue.booleanValue(true);
                case FALSE -> SassValue.booleanValue(false);
                case NULL -> SassValue.nullValue();
                case UNRECOGNIZED -> throw new SassValueException(
                        "Unknown Value.singleton " + value.getSingleton()
                );
            };
            case LIST -> decodeList(value.getList(), context);
            case MAP -> decodeMap(value.getMap(), context);
            case ARGUMENT_LIST -> decodeArgumentList(
                    value.getArgumentList(),
                    context
            );
            case COLOR -> decodeColor(value.getColor());
            case CALCULATION -> decodeCalculation(value.getCalculation());
            case COMPILER_FUNCTION -> requireOpaque(
                    functions,
                    value.getCompilerFunction().getId(),
                    "CompilerFunction",
                    "functions"
            );
            case COMPILER_MIXIN -> requireOpaque(
                    mixins,
                    value.getCompilerMixin().getId(),
                    "CompilerMixin",
                    "mixins"
            );
            case HOST_FUNCTION -> decodeHostFunction(value.getHostFunction());
            case VALUE_NOT_SET -> throw protocolError(
                    "Missing mandatory field Value.value"
            );
        };
    }

    /// Decodes a protocol number.
    private static SassValue decodeNumber(Value.Number number) {
        return SassValue.number(
                number.getValue(),
                number.getNumeratorsList(),
                number.getDenominatorsList()
        );
    }

    /// Decodes a protocol list.
    ///
    /// @param list the protocol list
    /// @param context the function-call context
    /// @return the decoded Sass list
    private SassValue decodeList(
            Value.List list,
            EncodingContext context
    ) {
        var contents = new ArrayList<SassValue>(list.getContentsCount());
        for (var element : list.getContentsList()) {
            contents.add(decode(element, context));
        }
        if (list.getSeparator() == ListSeparator.UNDECIDED
                && contents.size() > 1) {
            throw undecidedSeparatorError(
                    Value.newBuilder().setList(list).build(),
                    contents.size()
            );
        }
        try {
            return SassValue.list(
                    contents,
                    separator(list.getSeparator()),
                    list.getHasBrackets()
            );
        } catch (IllegalArgumentException failure) {
            throw protocolError(failure.getMessage());
        }
    }

    /// Decodes a protocol map without losing insertion order.
    ///
    /// @param map the protocol map
    /// @param context the function-call context
    /// @return the decoded insertion-ordered Sass map
    private SassValue decodeMap(
            Value.Map map,
            EncodingContext context
    ) {
        var contents = new LinkedHashMap<SassValue, SassValue>();
        for (var entry : map.getEntriesList()) {
            var key = decode(entry.getKey(), context);
            if (contents.containsKey(key)) {
                throw protocolError("Sass map contains a duplicate key.");
            }
            contents.put(key, decode(entry.getValue(), context));
        }
        return SassValue.map(contents);
    }

    /// Resolves a compiler argument-list reference or decodes a host-created
    /// argument list.
    ///
    /// @param list the protocol argument list
    /// @param context the function-call context that owns nonzero IDs
    /// @return the referenced or newly decoded Sass argument list
    private SassValue decodeArgumentList(
            Value.ArgumentList list,
            EncodingContext context
    ) {
        if (list.getId() != 0) {
            return context.argumentListForId(list.getId());
        }
        var contents = new ArrayList<SassValue>();
        for (var element : list.getContentsList()) {
            contents.add(decode(element, context));
        }
        if (list.getSeparator() == ListSeparator.UNDECIDED
                && contents.size() > 1) {
            throw undecidedSeparatorError(
                    Value.newBuilder().setArgumentList(list).build(),
                    contents.size()
            );
        }
        var keywords = new LinkedHashMap<String, SassValue>();
        for (var entry : list.getKeywordsMap().entrySet()) {
            keywords.put(
                    entry.getKey(),
                    decode(entry.getValue(), context)
            );
        }
        return SassValue.argumentList(
                contents,
                separator(list.getSeparator()),
                keywords
        );
    }

    /// Creates the protocol error used for a multi-element list whose
    /// separator is undecided.
    ///
    /// @param value the complete protocol value
    /// @param length the number of elements
    /// @return the protocol parameter error
    private static EmbeddedProtocolException undecidedSeparatorError(
            Value value,
            int length
    ) {
        return protocolError(
                "List " + value
                        + " can't have an undecided separator because it has "
                        + length + " elements"
        );
    }

    /// Decodes a protocol color with missing-channel presence intact.
    private static SassValue decodeColor(Value.Color color) {
        final SassColorSpace space;
        try {
            space = SassColorSpace.fromCssName(color.getSpace());
        } catch (IllegalArgumentException failure) {
            throw new SassValueException(failure.getMessage());
        }
        try {
            return SassValue.color(
                    space,
                    color.hasChannel1() ? color.getChannel1() : null,
                    color.hasChannel2() ? color.getChannel2() : null,
                    color.hasChannel3() ? color.getChannel3() : null,
                    color.hasAlpha() ? color.getAlpha() : null
            );
        } catch (IllegalArgumentException failure) {
            if (color.hasAlpha()) {
                throw protocolError(
                        "Color.alpha must be between 0 and 1, was "
                                + color.getAlpha()
                );
            }
            throw new SassValueException(failure.getMessage());
        }
    }

    /// Decodes a protocol calculation using Dart Sass-compatible factories.
    private SassValue decodeCalculation(
            Value.Calculation calculation
    ) {
        var arguments = new ArrayList<SassCalculationValue>();
        for (var argument : calculation.getArgumentsList()) {
            arguments.add(decodeCalculationValue(argument));
        }
        return switch (calculation.getName()) {
            case "calc" -> {
                if (arguments.size() != 1) {
                    throw protocolError(
                            "Value.Calculation.arguments must have exactly "
                                    + "one argument for calc()."
                    );
                }
                yield SassValue.calculation("calc", arguments);
            }
            case "min" -> {
                requireCalculationArguments("min", arguments);
                yield SassValue.calculation("min", arguments);
            }
            case "max" -> {
                requireCalculationArguments("max", arguments);
                yield SassValue.calculation("max", arguments);
            }
            case "clamp" -> {
                if (arguments.isEmpty() || arguments.size() > 3) {
                    throw protocolError(
                            "Value.Calculation.arguments must have 1 to 3 "
                                    + "arguments for clamp()."
                    );
                }
                yield SassValue.calculation("clamp", arguments);
            }
            default -> throw protocolError(
                    "Value.Calculation.name \"" + calculation.getName()
                            + "\" is not a recognized calculation type."
            );
        };
    }

    /// Decodes one protocol calculation argument.
    private SassCalculationValue decodeCalculationValue(
            Value.Calculation.CalculationValue value
    ) {
        return switch (value.getValueCase()) {
            case NUMBER -> new SassCalculationValue.Value(
                    decodeNumber(value.getNumber())
            );
            case STRING -> new SassCalculationValue.StringValue(
                    value.getString()
            );
            case INTERPOLATION ->
                    new SassCalculationValue.StringValue(
                            "(" + value.getInterpolation() + ")"
                    );
            case CALCULATION -> new SassCalculationValue.Value(
                    decodeCalculation(value.getCalculation())
            );
            case OPERATION -> {
                var operation = value.getOperation();
                yield new SassCalculationValue.Operation(
                        switch (operation.getOperator()) {
                            case PLUS -> SassCalculationValue.Operator.PLUS;
                            case MINUS -> SassCalculationValue.Operator.MINUS;
                            case TIMES -> SassCalculationValue.Operator.TIMES;
                            case DIVIDE ->
                                    SassCalculationValue.Operator.DIVIDED_BY;
                            case UNRECOGNIZED -> throw new SassValueException(
                                    "Unknown CalculationOperator "
                                            + operation.getOperator()
                            );
                        },
                        decodeCalculationValue(operation.getLeft()),
                        decodeCalculationValue(operation.getRight())
                );
            }
            case VALUE_NOT_SET -> throw protocolError(
                    "Missing mandatory field Value.Calculation.value"
            );
        };
    }

    /// Decodes a host function and binds it to the active evaluator identity.
    private SassValue decodeHostFunction(Value.HostFunction function) {
        var signature = function.getSignature();
        @Nullable var signatureError =
                EmbeddedFunctionSignature.error(signature);
        if (signatureError != null) {
            throw new SassValueException(signatureError);
        }
        return SassValue.function(new SassCustomFunction(
                signature,
                arguments -> callById(
                        function.getId(),
                        arguments
                )
        ));
    }

    /// Marks compiler-created argument lists observed by the host.
    private static void markAccessedArgumentLists(
            List<Integer> ids,
            Map<Integer, SassValue> argumentLists
    ) {
        for (var id : ids) {
            if (id == 0) {
                throw protocolError(
                        "Value.ArgumentList.id 0 can't be marked as accessed"
                );
            }
            @Nullable var arguments = argumentLists.get(id);
            if (arguments == null) {
                throw protocolError(
                        "Value.ArgumentList.id "
                                + Integer.toUnsignedString(id)
                                + " doesn't match any known argument lists"
                );
            }
            arguments.keywords();
        }
    }

    /// Requires a known compiler-owned opaque value.
    private static SassValue requireOpaque(
            Map<Integer, SassValue> values,
            int id,
            String field,
            String collection
    ) {
        @Nullable var value = values.get(id);
        if (value == null) {
            throw protocolError(
                    field + ".id " + Integer.toUnsignedString(id)
                            + " doesn't match any known " + collection
            );
        }
        return value;
    }

    /// Requires a nonempty variadic calculation argument list.
    ///
    /// @param name the calculation function name
    /// @param arguments the decoded arguments
    private static void requireCalculationArguments(
            String name,
            List<SassCalculationValue> arguments
    ) {
        if (arguments.isEmpty()) {
            throw protocolError(
                    "Value.Calculation.arguments must have at least 1 "
                            + "argument for " + name + "()."
            );
        }
    }

    /// Converts a public list separator to the protocol enum.
    private static ListSeparator separator(SassListSeparator separator) {
        return switch (separator) {
            case COMMA -> ListSeparator.COMMA;
            case SPACE -> ListSeparator.SPACE;
            case SLASH -> ListSeparator.SLASH;
            case UNDECIDED -> ListSeparator.UNDECIDED;
        };
    }

    /// Converts a protocol list separator to the public enum.
    private static SassListSeparator separator(ListSeparator separator) {
        return switch (separator) {
            case COMMA -> SassListSeparator.COMMA;
            case SPACE -> SassListSeparator.SPACE;
            case SLASH -> SassListSeparator.SLASH;
            case UNDECIDED -> SassListSeparator.UNDECIDED;
            case UNRECOGNIZED -> throw new SassValueException(
                    "Unknown ListSeparator " + separator
            );
        };
    }

    /// Creates a protocol parameter error for invalid host data.
    private static EmbeddedProtocolException protocolError(String message) {
        return new EmbeddedProtocolException(
                ProtocolErrorType.PARAMS,
                -1,
                message
        );
    }

    /// Tracks argument-list IDs for one outbound function request.
    @NotNullByDefault
    private static final class EncodingContext {
        /// Maps IDs back to the compiler-created argument lists.
        private final Map<Integer, SassValue> argumentLists =
                new HashMap<>();

        /// Contains the next request-local argument-list ID.
        private int nextArgumentListId = 1;

        /// Allocates an argument-list ID beginning at one.
        ///
        /// Each encoded occurrence receives its own ID, matching the protocol
        /// compiler's call-local traversal even if the same internal object
        /// appears more than once.
        ///
        /// @param arguments the compiler-created argument list
        /// @return its request-local ID
        private int idFor(SassValue arguments) {
            var id = nextArgumentListId++;
            argumentLists.put(id, arguments);
            return id;
        }

        /// Returns the compiler argument list associated with a host-returned
        /// request-local ID.
        ///
        /// @param id the unsigned protocol ID stored in a Java integer
        /// @return the compiler-created argument list
        /// @throws EmbeddedProtocolException if the ID is unknown
        private SassValue argumentListForId(int id) {
            @Nullable var arguments = argumentLists.get(id);
            if (arguments == null) {
                throw protocolError(
                        "Value.ArgumentList.id "
                                + Integer.toUnsignedString(id)
                                + " doesn't match any known argument lists"
                );
            }
            return arguments;
        }
    }
}
