// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.value;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Represents an immutable value produced by SassScript evaluation.
///
/// Value operations report span-free [SassValueException] instances. The
/// evaluator that invoked an operation is responsible for associating the
/// failure with its expression span.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SassValue permits
        SassNull,
        SassBoolean,
        SassNumber,
        SassString,
        SassList,
        SassMap,
        SassColor {
    /// Returns this value's separator when viewed as a Sass list.
    ///
    /// Atomic values use an undecided separator.
    ///
    /// @return the list separator
    default ListSeparator separator() {
        return ListSeparator.UNDECIDED;
    }

    /// Returns whether this value's list view has square brackets.
    ///
    /// @return `false` for atomic values
    default boolean hasBrackets() {
        return false;
    }

    /// Returns this value viewed as a Sass list.
    ///
    /// Atomic values return an immutable singleton list containing themselves.
    ///
    /// @return the immutable list view
    default @Unmodifiable List<SassValue> asList() {
        return List.of(this);
    }

    /// Returns the number of elements in this value's Sass list view.
    ///
    /// @return one for atomic values
    default int lengthAsList() {
        return 1;
    }

    /// Returns whether this value is true in a Sass boolean context.
    ///
    /// All values other than `false` and `null` are true by default.
    ///
    /// @return whether this value is truthy
    default boolean isTruthy() {
        return true;
    }

    /// Returns whether this value is omitted from a CSS list serialization.
    ///
    /// Only Sass null, unquoted empty strings, and unbracketed lists whose
    /// elements are all blank override the non-blank default.
    ///
    /// @return whether this value is blank
    default boolean isBlank() {
        return false;
    }

    /// Returns a plain-CSS representation of this value.
    ///
    /// @return the CSS representation
    /// @throws SassValueException if this value cannot be represented in CSS
    default String toCssString() {
        return toString();
    }

    /// Returns a plain-CSS representation with configurable string quoting.
    ///
    /// Atomic non-string values ignore {@code quote}. Composite values pass it
    /// to their elements.
    ///
    /// @param quote whether quoted strings retain surrounding quotes
    /// @return the CSS representation
    /// @throws SassValueException if this value cannot be represented in CSS
    default String toCssString(boolean quote) {
        return toCssString();
    }

    /// Evaluates the SassScript single-equals operation.
    ///
    /// @param other the right operand
    /// @return an unquoted string containing both operands separated by `=`
    /// @throws SassValueException if either operand cannot be represented in CSS
    default SassValue singleEquals(SassValue other) {
        return new SassString(toCssString() + "=" + other.toCssString(), false);
    }

    /// Evaluates the SassScript greater-than operation.
    ///
    /// @param other the right operand
    /// @return the comparison result
    /// @throws SassValueException when the operands are not comparable
    default SassBoolean greaterThan(SassValue other) {
        throw undefinedOperation(">", other);
    }

    /// Evaluates the SassScript greater-than-or-equal operation.
    ///
    /// @param other the right operand
    /// @return the comparison result
    /// @throws SassValueException when the operands are not comparable
    default SassBoolean greaterThanOrEquals(SassValue other) {
        throw undefinedOperation(">=", other);
    }

    /// Evaluates the SassScript less-than operation.
    ///
    /// @param other the right operand
    /// @return the comparison result
    /// @throws SassValueException when the operands are not comparable
    default SassBoolean lessThan(SassValue other) {
        throw undefinedOperation("<", other);
    }

    /// Evaluates the SassScript less-than-or-equal operation.
    ///
    /// @param other the right operand
    /// @return the comparison result
    /// @throws SassValueException when the operands are not comparable
    default SassBoolean lessThanOrEquals(SassValue other) {
        throw undefinedOperation("<=", other);
    }

    /// Evaluates the SassScript addition operation.
    ///
    /// The fallback operation concatenates CSS representations. When the
    /// right operand is a string, its quoting style is retained.
    ///
    /// @param other the right operand
    /// @return the sum or concatenated string
    /// @throws SassValueException if an operand cannot be represented in CSS
    default SassValue plus(SassValue other) {
        if (other instanceof SassString string) {
            return new SassString(toCssString() + string.text(), string.hasQuotes());
        }
        return new SassString(toCssString() + other.toCssString(), false);
    }

    /// Evaluates the SassScript subtraction operation.
    ///
    /// The fallback operation joins CSS representations with a hyphen.
    ///
    /// @param other the right operand
    /// @return the difference or concatenated string
    /// @throws SassValueException if an operand cannot be represented in CSS
    default SassValue minus(SassValue other) {
        return new SassString(toCssString() + "-" + other.toCssString(), false);
    }

    /// Evaluates the SassScript multiplication operation.
    ///
    /// @param other the right operand
    /// @return the product
    /// @throws SassValueException when multiplication is undefined
    default SassValue times(SassValue other) {
        throw undefinedOperation("*", other);
    }

    /// Evaluates the SassScript division operation.
    ///
    /// The fallback operation joins CSS representations with a slash.
    ///
    /// @param other the right operand
    /// @return the quotient or slash-separated string
    /// @throws SassValueException if an operand cannot be represented in CSS
    default SassValue dividedBy(SassValue other) {
        return new SassString(toCssString() + "/" + other.toCssString(), false);
    }

    /// Evaluates the SassScript modulo operation.
    ///
    /// @param other the right operand
    /// @return the remainder
    /// @throws SassValueException when modulo is undefined
    default SassValue modulo(SassValue other) {
        throw undefinedOperation("%", other);
    }

    /// Evaluates unary plus.
    ///
    /// @return this value prefixed with plus as an unquoted string
    /// @throws SassValueException if this value cannot be represented in CSS
    default SassValue unaryPlus() {
        return new SassString("+" + toCssString(), false);
    }

    /// Evaluates unary minus.
    ///
    /// @return this value prefixed with minus as an unquoted string
    /// @throws SassValueException if this value cannot be represented in CSS
    default SassValue unaryMinus() {
        return new SassString("-" + toCssString(), false);
    }

    /// Evaluates the historical unary slash operation.
    ///
    /// @return this value prefixed with slash as an unquoted string
    /// @throws SassValueException if this value cannot be represented in CSS
    default SassValue unaryDivide() {
        return new SassString("/" + toCssString(), false);
    }

    /// Evaluates boolean negation.
    ///
    /// @return `false` for values that use the default truthiness
    default SassBoolean unaryNot() {
        return SassBoolean.FALSE;
    }

    /// Returns this value without deprecated slash-division presentation metadata.
    ///
    /// Non-number values return themselves unchanged.
    ///
    /// @return the semantic value without slash presentation metadata
    default SassValue withoutSlash() {
        return this;
    }

    /// Returns this value when it is a Sass number.
    ///
    /// @return this number
    /// @throws SassValueException if this value is not a number
    default SassNumber assertNumber() {
        throw new SassValueException(this + " is not a number.");
    }

    /// Returns this value when it is a Sass string.
    ///
    /// @return this string
    /// @throws SassValueException if this value is not a string
    default SassString assertString() {
        throw new SassValueException(this + " is not a string.");
    }

    /// Returns this value when it is a Sass color.
    ///
    /// @return this color
    /// @throws SassValueException if this value is not a color
    default SassColor assertColor() {
        throw new SassValueException(this + " is not a color.");
    }

    /// Converts a Sass list index into a zero-based Java list index.
    ///
    /// Positive indexes start at one. Negative indexes count from the end.
    ///
    /// @param sassIndex the Sass index value
    /// @param length    the list length
    /// @return the zero-based index
    /// @throws SassValueException if the index is invalid
    default int sassIndexToListIndex(SassValue sassIndex, int length) {
        var index = sassIndex.assertNumber().assertInt();
        if (index == 0) {
            throw new SassValueException("List index may not be 0.");
        }
        if (index > 0) {
            if (index > length) {
                throw new SassValueException(
                        "Invalid index " + index + " for a list with " + length + " elements."
                );
            }
            return index - 1;
        }
        if (index < -length) {
            throw new SassValueException(
                    "Invalid index " + index + " for a list with " + length + " elements."
            );
        }
        return length + index;
    }

    /// Creates the standard undefined-operation failure for two operands.
    ///
    /// @param operator the Sass operator spelling
    /// @param other    the right operand
    /// @return the operation failure
    private SassValueException undefinedOperation(String operator, SassValue other) {
        return new SassValueException(
                "Undefined operation \"" + this + " " + operator + " " + other + "\"."
        );
    }
}
