package com.yanban.core.research;

/** Provenance common to all structured index entries and evidence references. */
public record IndexedProvenance(FileManifestEntry source, SourceRange range, ParserVersionRef parserVersion,
                                IndexFreshness freshness, boolean partial, boolean truncated,
                                TrustLabel trustLabel) {
    public IndexedProvenance {
        if (source == null || range == null || parserVersion == null
                || freshness == null || trustLabel == null) {
            throw new IllegalArgumentException("index provenance is incomplete");
        }
    }
}
