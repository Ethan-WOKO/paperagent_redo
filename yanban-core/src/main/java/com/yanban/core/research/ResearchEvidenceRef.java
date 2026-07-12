package com.yanban.core.research;

/** Safe model/audit projection of evidence: no host path, user id, project id, or content. */
public record ResearchEvidenceRef(ProjectVersionRef projectVersion, ProjectRelativePath relativePath,
                                  FileHash fileHash, SourceRange range, ParserVersionRef parserVersion,
                                  TrustLabel trustLabel) {
    public ResearchEvidenceRef {
        if (projectVersion == null || relativePath == null || fileHash == null || range == null
                || parserVersion == null || trustLabel == null) {
            throw new IllegalArgumentException("evidence provenance is incomplete");
        }
    }

    public static ResearchEvidenceRef from(IndexedProvenance provenance) {
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }
        return new ResearchEvidenceRef(provenance.source().projectVersion(), provenance.source().relativePath(),
                provenance.source().fileHash(), provenance.range(), provenance.parserVersion(), provenance.trustLabel());
    }
}
