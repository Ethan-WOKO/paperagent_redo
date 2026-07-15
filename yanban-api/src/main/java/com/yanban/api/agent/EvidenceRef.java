package com.yanban.api.agent;

import com.yanban.core.research.FileHash;
import com.yanban.core.research.ProjectVersionRef;
import org.springframework.util.StringUtils;

/**
 * Stable, display-safe provenance for one piece of non-runtime context.
 */
public record EvidenceRef(
        String id,
        EvidenceSourceType sourceType,
        String source,
        String file,
        String chunk,
        String citation,
        String version,
        String selectionReason,
        String projectVersion,
        String fileHash,
        Integer startLine,
        Integer endLine,
        String parserVersion,
        EvidenceVersionStatus versionStatus
) {
    public EvidenceRef(String id,
                       String source,
                       String file,
                       String chunk,
                       String citation,
                       String version,
                       String selectionReason) {
        this(id, EvidenceSourceType.fromLegacySource(source), source, file, chunk, citation, version, selectionReason,
                null, null, null, null, null, EvidenceVersionStatus.LEGACY_UNVERSIONED);
    }

    public EvidenceRef(String id, EvidenceSourceType sourceType, String source, String file, String chunk,
                       String citation, String version, String selectionReason) {
        this(id, sourceType, source, file, chunk, citation, version, selectionReason,
                null, null, null, null, null, EvidenceVersionStatus.LEGACY_UNVERSIONED);
    }

    public EvidenceRef {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("evidence id must not be blank");
        }
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException("evidence source must not be blank");
        }
        sourceType = sourceType == null ? EvidenceSourceType.LEGACY_UNVERSIONED : sourceType;
        if ((sourceType == EvidenceSourceType.RAG || sourceType == EvidenceSourceType.PROJECT)
                && (!StringUtils.hasText(file) || !StringUtils.hasText(chunk) || !StringUtils.hasText(version))) {
            throw new IllegalArgumentException(sourceType + " evidence requires file, chunk and version");
        }
        boolean completeVersionBinding = StringUtils.hasText(projectVersion) && StringUtils.hasText(fileHash)
                && startLine != null && startLine > 0 && endLine != null && endLine >= startLine
                && StringUtils.hasText(parserVersion);
        versionStatus = versionStatus == null ? EvidenceVersionStatus.LEGACY_UNVERSIONED : versionStatus;
        if (versionStatus == EvidenceVersionStatus.VERIFIED || versionStatus == EvidenceVersionStatus.STALE) {
            if (!completeVersionBinding) {
                throw new IllegalArgumentException("versioned evidence requires project version, file hash, range and parser version");
            }
            projectVersion = new ProjectVersionRef(projectVersion).value();
            fileHash = new FileHash(fileHash).sha256();
        } else if (StringUtils.hasText(projectVersion) || StringUtils.hasText(fileHash)
                || startLine != null || endLine != null || StringUtils.hasText(parserVersion)) {
            throw new IllegalArgumentException("legacy evidence cannot carry a partial version binding");
        }
    }

    /** Preserves the complete provenance while explicitly denying current verification. */
    public EvidenceRef stale() {
        if (versionStatus != EvidenceVersionStatus.VERIFIED) {
            throw new IllegalStateException("only verified evidence can become stale");
        }
        return new EvidenceRef(id, sourceType, source, file, chunk, citation, version, selectionReason,
                projectVersion, fileHash, startLine, endLine, parserVersion, EvidenceVersionStatus.STALE);
    }
}
