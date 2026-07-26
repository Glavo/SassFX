// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx.internal.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

/// Represents one parsed Sass `@supports` condition.
///
/// Conditions retain SassScript expressions and interpolated CSS fragments
/// until evaluation, while boolean operators and declarations remain
/// structurally distinguishable.
@ApiStatus.Internal
@NotNullByDefault
public sealed interface SupportsCondition extends SassNode permits
        SupportsDeclaration,
        SupportsFunction,
        SupportsAnything,
        SupportsInterpolation,
        SupportsNegation,
        SupportsOperation {
}
