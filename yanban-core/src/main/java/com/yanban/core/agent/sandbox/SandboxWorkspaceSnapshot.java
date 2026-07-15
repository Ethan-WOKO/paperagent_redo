package com.yanban.core.agent.sandbox;

import com.yanban.core.research.ProjectManifestIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic, read-only manifest projection for a future isolated working copy. */
public record SandboxWorkspaceSnapshot(SandboxWorkspaceRef workspace, List<SandboxFileSnapshot> files)
        implements RejectsUnknownFields {
    public SandboxWorkspaceSnapshot {
        if (workspace == null || files == null) {
            throw new IllegalArgumentException("sandbox snapshot requires workspace identity and files");
        }
        List<SandboxFileSnapshot> sorted = new ArrayList<>(files.size());
        Set<String> conflictKeys = new HashSet<>();
        for (SandboxFileSnapshot file : files) {
            if (file == null || !conflictKeys.add(SandboxContractSupport.pathConflictKey(file.relativePath()))) {
                throw new IllegalArgumentException("sandbox snapshot contains a duplicate target path");
            }
            sorted.add(file);
        }
        sorted.sort(Comparator.comparing(file -> file.relativePath().value()));
        files = List.copyOf(sorted);
        var manifestVersion = ProjectManifestIdentity.derive(files.stream().map(file ->
                new ProjectManifestIdentity.Entry(file.relativePath(), file.fileHash(), file.sizeBytes())).toList());
        if (!workspace.projectVersion().equals(manifestVersion)) {
            throw new IllegalArgumentException("sandbox snapshot manifest does not match its Project version");
        }
    }
}
