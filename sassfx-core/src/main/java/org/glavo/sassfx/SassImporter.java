// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx;

import org.jetbrains.annotations.NotNullByDefault;

/// Identifies a custom Sass importer configured for a compilation.
///
/// Importers configured on one compilation are consulted in list order before
/// filesystem load paths. Implementations must use either
/// [SassContentsImporter] to provide stylesheet contents or [SassFileImporter]
/// to delegate loading to the compiler's filesystem resolver.
///
/// Implementations may be invoked more than once for the same request and must
/// not rely on a particular invocation count. A single importer instance may
/// be used concurrently when the same [CompileOptions] is shared by concurrent
/// compilations; implementations are responsible for synchronizing mutable
/// state.
@NotNullByDefault
public sealed interface SassImporter
        permits SassContentsImporter, SassFileImporter {
}
