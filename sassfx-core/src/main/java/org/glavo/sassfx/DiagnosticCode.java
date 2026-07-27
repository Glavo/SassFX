// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Stable machine-readable identifiers for compiler diagnostics.
///
/// Messages are rendered from a code plus typed arguments by
/// [DiagnosticMessages]. Callers and tests must branch on codes rather than
/// English message text.
@NotNullByDefault
public enum DiagnosticCode {
    /// Generic parse failure with a fully rendered message argument.
    PARSE_ERROR,

    /// Generic evaluation failure with a fully rendered message argument.
    EVALUATION_ERROR,

    /// Indented Sass nesting without a block header.
    INDENTED_NESTING_WITHOUT_HEADER,

    /// Unexpected text after a closed loud comment in indented Sass.
    INDENTED_TEXT_AFTER_COMMENT,

    /// Inconsistent indentation in indented Sass.
    INDENTED_INCONSISTENT_INDENT,

    /// Expected a specific token or construct during parse.
    EXPECTED_TOKEN,

    /// Unsupported or missing language construct.
    UNSUPPORTED_FEATURE,

    /// Module system failure (missing member, namespace collision, …).
    MODULE_ERROR,

    /// Selector or extend algebra failure.
    SELECTOR_ERROR,

    /// Serialization failure for CSS or BSS output.
    SERIALIZE_ERROR,

    /// Undefined binary or unary operation on values (colors, strings, …).
    UNDEFINED_OPERATION
}
