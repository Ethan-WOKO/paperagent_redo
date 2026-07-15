package com.yanban.core.agent.sandbox;

import com.yanban.core.research.FileHash;
import com.yanban.core.research.ProjectRelativePath;

/** Content-addressed file projection in a sandbox snapshot; no content or host path is stored. */
public record SandboxFileSnapshot(ProjectRelativePath relativePath, FileHash fileHash, long sizeBytes)
        implements RejectsUnknownFields {
    public SandboxFileSnapshot {
        if (relativePath == null || fileHash == null || sizeBytes < 0) {
            throw new IllegalArgumentException("sandbox file requires a relative path, hash, and non-negative size");
        }
    }
}
