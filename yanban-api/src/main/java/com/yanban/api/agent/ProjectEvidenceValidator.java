package com.yanban.api.agent;

import com.yanban.api.project.ProjectManifestResponse;
import com.yanban.api.project.ProjectService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Revalidates trusted observations against the currently authorized Project manifest. */
@Component
public class ProjectEvidenceValidator {
    private final ProjectService projects;

    public ProjectEvidenceValidator(ProjectService projects) {
        this.projects = projects;
    }

    public EvidenceLedger current(Long userId, ProjectRuntimeContext context, EvidenceLedger evidence) {
        if (context == null || evidence == null || !context.userId().equals(userId)) return EvidenceLedger.empty();
        ProjectManifestResponse manifest = projects.manifest(userId, context.projectId());
        List<EvidenceRef> current = evidence.evidence().stream().map(ref -> {
            if (!isTrusted(ref) || !belongsToProject(ref, context.projectId())) return null;
            boolean verified = ref.versionStatus() == EvidenceVersionStatus.VERIFIED
                    && manifest.version().equals(ref.projectVersion())
                    && manifest.files().stream().anyMatch(file -> file.path().equals(ref.file())
                    && file.sha256().equals(ref.fileHash()));
            return verified ? ref : null;
        }).filter(java.util.Objects::nonNull).toList();
        return new EvidenceLedger(current);
    }

    /** Attests legacy read/search tool metadata against the server-owned current manifest. */
    EvidenceRef attestCurrentFile(Long userId, ProjectRuntimeContext context, String id, String path,
                                  String hash, int startLine, int endLine, String parserVersion,
                                  String chunk, String reason) {
        if (context == null || !context.userId().equals(userId)) return null;
        ProjectManifestResponse manifest = projects.manifest(userId, context.projectId());
        boolean current = manifest.files().stream().anyMatch(file -> file.path().equals(path)
                && file.sha256().equals(hash));
        if (!current) return null;
        return new EvidenceRef(id, EvidenceSourceType.PROJECT, "PROJECT", path, chunk, null, hash, reason,
                manifest.version(), hash, startLine, endLine, parserVersion, EvidenceVersionStatus.VERIFIED);
    }

    static boolean isTrusted(EvidenceRef ref) {
        return ref != null && ref.id() != null && (ref.id().startsWith("trusted-tool:")
                || ref.id().startsWith("trusted-plan:"));
    }

    private static boolean belongsToProject(EvidenceRef ref, Long projectId) {
        if (ref == null || ref.id() == null || projectId == null) return false;
        return ref.id().startsWith("trusted-tool:" + projectId + ":")
                || ref.id().startsWith("trusted-plan:" + projectId + ":");
    }
}
