package com.yanban.core.agent.sandbox;

import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ResearchEvidenceRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class SandboxContractSupport {
    static final Comparator<CandidateFileChange> CHANGE_ORDER = Comparator
            .comparing((CandidateFileChange change) -> change.relativePath().value())
            .thenComparing(CandidateFileChange::type);

    private static final Comparator<ResearchEvidenceRef> EVIDENCE_ORDER = Comparator
            .comparing((ResearchEvidenceRef evidence) -> evidence.relativePath().value())
            .thenComparing(evidence -> evidence.range().startLine())
            .thenComparing(evidence -> evidence.range().endLine())
            .thenComparing(evidence -> evidence.fileHash().sha256())
            .thenComparing(evidence -> evidence.parserVersion().value())
            .thenComparing(evidence -> evidence.trustLabel().name());

    private static final byte[] DOMAIN = "yanban-candidate-change-set-v1\0".getBytes(StandardCharsets.UTF_8);

    private SandboxContractSupport() { }

    static String pathConflictKey(ProjectRelativePath path) {
        return path.value().toLowerCase(Locale.ROOT);
    }

    static List<ResearchEvidenceRef> sortedDistinctEvidence(List<ResearchEvidenceRef> evidenceRefs) {
        if (evidenceRefs.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("candidate evidence must not contain null");
        }
        return evidenceRefs.stream().distinct().sorted(EVIDENCE_ORDER).toList();
    }

    static CandidateFingerprint fingerprint(SandboxWorkspaceRef workspace, List<CandidateFileChange> changes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            field(digest, Long.toString(workspace.projectId()));
            field(digest, workspace.projectVersion().value());
            for (CandidateFileChange change : changes) {
                field(digest, change.type().name());
                field(digest, change.relativePath().value());
                field(digest, change.baseFileHash() == null ? "" : change.baseFileHash().sha256());
                field(digest, change.resultFileHash() == null ? "" : change.resultFileHash().sha256());
                if (change.candidateText() != null) {
                    fieldBytes(digest, CandidateTextPayload.encode(change.candidateText().text()));
                }
                for (ResearchEvidenceRef evidence : change.evidenceRefs()) {
                    field(digest, evidence.projectVersion().value());
                    field(digest, evidence.relativePath().value());
                    field(digest, evidence.fileHash().sha256());
                    field(digest, Integer.toString(evidence.range().startLine()));
                    field(digest, Integer.toString(evidence.range().endLine()));
                    field(digest, evidence.parserVersion().value());
                    field(digest, evidence.trustLabel().name());
                }
            }
            return new CandidateFingerprint(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void field(MessageDigest digest, String value) {
        fieldBytes(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void fieldBytes(MessageDigest digest, byte[] bytes) {
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) 0);
    }
}
