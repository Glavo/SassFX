// SPDX-License-Identifier: MPL-2.0
package org.glavo.scssfx;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/// Describes the load that caused a [SassImporter] canonicalization request.
///
/// Reading [#containingUrl()] during
/// [SassImporter#canonicalize(URI, SassCanonicalizeContext)] records that the
/// importer used the containing
/// stylesheet identity. The compiler will not reuse that canonicalization
/// result for a request originating from another containing stylesheet.
@NotNullByDefault
public final class SassCanonicalizeContext {
    /// Contains the canonical URL of the containing stylesheet, or
    /// {@code null} when no containing identity is available.
    private final @Nullable URI containingUrl;

    /// Contains whether the request originates from a legacy import.
    private final boolean fromImport;

    /// Contains whether the importer observed [#containingUrl()].
    private boolean containingUrlAccessed;

    /// Creates a canonicalization context.
    ///
    /// @param containingUrl the canonical URL of the containing stylesheet, or
    ///                      {@code null} when the load has no containing
    ///                      stylesheet
    /// @param fromImport whether the request originates from a legacy
    ///                   {@code @import} rule
    public SassCanonicalizeContext(
            @Nullable URI containingUrl,
            boolean fromImport
    ) {
        this.containingUrl = containingUrl;
        this.fromImport = fromImport;
    }

    /// Returns the canonical URL of the containing stylesheet.
    ///
    /// Calling this method marks the current canonicalization as dependent on
    /// its containing stylesheet, even when the returned value is
    /// {@code null}.
    ///
    /// @return the containing canonical URL, or {@code null}
    public @Nullable URI containingUrl() {
        containingUrlAccessed = true;
        return containingUrl;
    }

    /// Returns whether the request originates from a legacy import.
    ///
    /// @return whether {@code @import} initiated the request
    public boolean fromImport() {
        return fromImport;
    }

    /// Returns the containing URL without recording importer access.
    ///
    /// This operation exists for protocol adapters that must send the value to
    /// a remote importer before learning whether that importer used it.
    ///
    /// @return the containing canonical URL, or {@code null}
    @ApiStatus.Internal
    public @Nullable URI containingUrlWithoutMarking() {
        return containingUrl;
    }

    /// Records that an out-of-process importer used the containing URL.
    @ApiStatus.Internal
    public void markContainingUrlAccessed() {
        containingUrlAccessed = true;
    }

    /// Returns whether this canonicalization observed the containing URL.
    ///
    /// @return whether the result depends on the containing stylesheet
    @ApiStatus.Internal
    public boolean wasContainingUrlAccessed() {
        return containingUrlAccessed;
    }
}
