// SPDX-License-Identifier: MPL-2.0
package org.glavo.sassfx.embedded;

import com.sass_lang.embedded_protocol.InboundMessage;
import com.sass_lang.embedded_protocol.OutboundMessage;
import org.glavo.sassfx.SassCanonicalizeContext;
import org.glavo.sassfx.SassFileImporter;
import org.glavo.sassfx.SassImporter;
import org.glavo.sassfx.SassImporterResult;
import org.glavo.sassfx.Syntax;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Adapts Embedded Sass host importer callbacks to the SassFX importer API.
@NotNullByDefault
final class EmbeddedHostImporter implements SassImporter {
    /// Matches the lowercase URL schemes accepted by Dart Sass importers.
    private static final Pattern VALID_URL_SCHEME =
            Pattern.compile("[a-z0-9+.-]+");

    /// Contains the importer identifier assigned by the host.
    private final int importerId;

    /// Contains schemes whose absolute URLs still require contextual lookup.
    private final @Unmodifiable Set<String> nonCanonicalSchemes;

    /// Routes synchronous callback requests to the host.
    private final EmbeddedCompilationDispatcher dispatcher;

    /// Creates a contents importer backed by host callbacks.
    ///
    /// @param importerId the host importer ID
    /// @param nonCanonicalSchemes contextual absolute URL schemes
    /// @param dispatcher the compilation callback dispatcher
    EmbeddedHostImporter(
            int importerId,
            List<String> nonCanonicalSchemes,
            EmbeddedCompilationDispatcher dispatcher
    ) {
        this.importerId = importerId;
        Objects.requireNonNull(nonCanonicalSchemes, "nonCanonicalSchemes");
        var validatedSchemes = new LinkedHashSet<String>();
        for (var scheme : nonCanonicalSchemes) {
            Objects.requireNonNull(scheme, "non-canonical scheme");
            if (!VALID_URL_SCHEME.matcher(scheme).matches()) {
                throw new IllegalArgumentException(
                        "\"" + scheme + "\" isn't a valid URL scheme "
                                + "(for example \"file\")."
                );
            }
            validatedSchemes.add(scheme);
        }
        this.nonCanonicalSchemes = Set.copyOf(validatedSchemes);
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /// Reports whether the host requested contextual handling for a scheme.
    ///
    /// @param scheme the absolute URL scheme
    /// @return whether the scheme is non-canonical
    @Override
    public boolean isNonCanonicalScheme(String scheme) {
        return nonCanonicalSchemes.contains(
                Objects.requireNonNull(scheme, "scheme")
                        .toLowerCase(Locale.ROOT)
        );
    }

    /// Requests canonicalization from the host.
    ///
    /// @param url the requested Sass URL
    /// @param context the canonicalization context
    /// @return the canonical URL, or {@code null} when the host declines
    /// @throws IOException if the host reports an error or the connection fails
    @Override
    public @Nullable URI canonicalize(
            URI url,
            SassCanonicalizeContext context
    ) throws IOException {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(context, "context");
        var callback = OutboundMessage.CanonicalizeRequest.newBuilder()
                .setId(0)
                .setImporterId(importerId)
                .setUrl(url.toString())
                .setFromImport(context.fromImport());
        @Nullable var containingUrl = context.containingUrlWithoutMarking();
        if (containingUrl != null) {
            callback.setContainingUrl(containingUrl.toString());
        }
        var response = dispatcher.request(
                OutboundMessage.newBuilder()
                        .setCanonicalizeRequest(callback)
                        .build(),
                InboundMessage.MessageCase.CANONICALIZE_RESPONSE
        ).getCanonicalizeResponse();
        if (!response.getContainingUrlUnused()) {
            context.markContainingUrlAccessed();
        }
        return switch (response.getResultCase()) {
            case URL -> {
                var canonicalUrl = parseAbsoluteUrl(
                        response.getUrl(),
                        "The importer"
                );
                if (nonCanonicalSchemes.contains(
                        canonicalUrl.getScheme().toLowerCase(Locale.ROOT)
                )) {
                    throw new IOException(
                            "Importer HostImporter canonicalized " + url
                                    + " to " + canonicalUrl
                                    + ", which uses a scheme declared as "
                                    + "non-canonical."
                    );
                }
                yield canonicalUrl;
            }
            case ERROR -> throw new IOException(response.getError());
            case RESULT_NOT_SET -> null;
        };
    }

    /// Requests stylesheet contents from the host.
    ///
    /// @param canonicalUrl the canonical URL returned by the host
    /// @return the imported source, or {@code null} when unavailable
    /// @throws IOException if the host reports an error or the connection fails
    @Override
    public @Nullable SassImporterResult load(URI canonicalUrl)
            throws IOException {
        Objects.requireNonNull(canonicalUrl, "canonicalUrl");
        var callback = OutboundMessage.ImportRequest.newBuilder()
                .setId(0)
                .setImporterId(importerId)
                .setUrl(canonicalUrl.toString());
        var response = dispatcher.request(
                OutboundMessage.newBuilder()
                        .setImportRequest(callback)
                        .build(),
                InboundMessage.MessageCase.IMPORT_RESPONSE
        ).getImportResponse();
        return switch (response.getResultCase()) {
            case SUCCESS -> {
                var success = response.getSuccess();
                @Nullable URI sourceMapUrl = success.hasSourceMapUrl()
                        && !success.getSourceMapUrl().isEmpty()
                        ? parseAbsoluteUrl(
                                success.getSourceMapUrl(),
                                "The importer"
                        )
                        : null;
                yield new SassImporterResult(
                        success.getContents(),
                        syntax(success.getSyntax()),
                        sourceMapUrl
                );
            }
            case ERROR -> throw new IOException(response.getError());
            case RESULT_NOT_SET -> null;
        };
    }

    /// Creates a file importer backed by the host.
    ///
    /// @param importerId the host file-importer ID
    /// @param dispatcher the compilation callback dispatcher
    /// @return the file importer
    static SassFileImporter fileImporter(
            int importerId,
            EmbeddedCompilationDispatcher dispatcher
    ) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        return (url, context) -> {
            var callback = OutboundMessage.FileImportRequest.newBuilder()
                    .setId(0)
                    .setImporterId(importerId)
                    .setUrl(url.toString())
                    .setFromImport(context.fromImport());
            @Nullable var containingUrl =
                    context.containingUrlWithoutMarking();
            if (containingUrl != null) {
                callback.setContainingUrl(containingUrl.toString());
            }
            var response = dispatcher.request(
                    OutboundMessage.newBuilder()
                            .setFileImportRequest(callback)
                            .build(),
                    InboundMessage.MessageCase.FILE_IMPORT_RESPONSE
            ).getFileImportResponse();
            if (!response.getContainingUrlUnused()) {
                context.markContainingUrlAccessed();
            }
            return switch (response.getResultCase()) {
                case FILE_URL -> {
                    var fileUrl = parseAbsoluteUrl(
                            response.getFileUrl(),
                            "The file importer"
                    );
                    if (!"file".equalsIgnoreCase(fileUrl.getScheme())) {
                        throw new IOException(
                                "The file importer must return a file: URL, "
                                        + "was \"" + fileUrl + "\""
                        );
                    }
                    yield fileUrl;
                }
                case ERROR -> throw new IOException(response.getError());
                case RESULT_NOT_SET -> null;
            };
        };
    }

    /// Converts a protocol syntax enum to the compiler syntax.
    ///
    /// @param syntax the protocol syntax
    /// @return the compiler syntax
    private static Syntax syntax(
            com.sass_lang.embedded_protocol.Syntax syntax
    ) throws IOException {
        return switch (syntax) {
            case SCSS -> Syntax.SCSS;
            case INDENTED -> Syntax.SASS;
            case CSS -> Syntax.CSS;
            case UNRECOGNIZED -> throw new IOException(
                    "Host returned an unrecognized Sass syntax."
            );
        };
    }

    /// Parses and validates an absolute callback URL using Embedded Sass
    /// diagnostic wording.
    ///
    /// @param text the URL text
    /// @param source the callback source named in failures
    /// @return the absolute URL
    /// @throws IOException if the URL is invalid or relative
    private static URI parseAbsoluteUrl(
            String text,
            String source
    ) throws IOException {
        try {
            var uri = URI.create(text);
            if (!uri.isAbsolute()) {
                throw new IOException(
                        source + " must return an absolute URL, was \""
                                + uri + "\""
                );
            }
            return uri;
        } catch (IllegalArgumentException failure) {
            throw new IOException(
                    source + " must return a URL, was \"" + text + "\"",
                    failure
            );
        }
    }
}
