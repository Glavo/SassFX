// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Defines stable identifiers assigned to parser and evaluator failures.
@ApiStatus.Internal
@NotNullByDefault
public enum DiagnosticCode {
    /// Generic parse failure.
    PARSE_ERROR,

    /// Generic evaluation failure.
    EVALUATION_ERROR,

    /// Indented Sass nesting without a block header.
    INDENTED_NESTING_WITHOUT_HEADER,

    /// Unexpected text after a closed loud comment in indented Sass.
    INDENTED_TEXT_AFTER_COMMENT,

    /// Inconsistent indentation in indented Sass.
    INDENTED_INCONSISTENT_INDENT,

    /// Undefined binary or unary operation on values (colors, strings, …).
    UNDEFINED_OPERATION
}
