// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.internal.ast;

import org.glavo.sassfx.SourceSpan;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents one argument of a legacy Sass `@import` rule.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SassImport extends SassNode permits DynamicImport, StaticImport {
    /// Returns the source range occupied by this import argument.
    ///
    /// @return the import argument span
    @Override
    SourceSpan span();
}
