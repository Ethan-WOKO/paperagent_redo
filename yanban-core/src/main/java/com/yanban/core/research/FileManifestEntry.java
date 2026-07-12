package com.yanban.core.research;

/** Immutable manifest reference usable without a database implementation. */
public record FileManifestEntry(ProjectVersionRef projectVersion, ProjectRelativePath relativePath,
                                FileHash fileHash, long sizeBytes) {
    public FileManifestEntry {
        if (projectVersion == null || relativePath == null || fileHash == null || sizeBytes < 0) {
            throw new IllegalArgumentException("manifest entry requires version, relative path, hash, and non-negative size");
        }
    }
}
