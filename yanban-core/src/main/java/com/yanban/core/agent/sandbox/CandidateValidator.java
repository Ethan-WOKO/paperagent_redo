package com.yanban.core.agent.sandbox;

import com.yanban.core.research.ResearchEvidenceRef;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic validator over a server-attested current manifest and explicit work budget. */
public final class CandidateValidator {
    private CandidateValidator() { }

    public static CandidateValidationDecision validate(CandidateChangeSet candidate,
                                                       SandboxSnapshotAttestation attestation,
                                                       CandidateValidationBudget budget) {
        if (candidate == null || attestation == null || budget == null) {
            throw new IllegalArgumentException("candidate validation inputs are incomplete");
        }
        SandboxWorkspaceSnapshot snapshot = attestation.snapshot();
        int requestedChanges = candidate.changes().size();
        int requestedEvidence = candidate.changes().stream().mapToInt(change -> change.evidenceRefs().size()).sum();
        long requestedBytes = candidate.changes().stream()
                .filter(change -> change.candidateText() != null)
                .mapToLong(change -> change.candidateText().utf8Bytes()).sum();
        int inspectedChanges = Math.min(requestedChanges, budget.maxChanges());
        int inspectedEvidence = Math.min(requestedEvidence, budget.maxEvidenceRefs());
        long inspectedBytes = Math.min(requestedBytes, budget.maxCandidateUtf8Bytes());

        Map<CandidateValidationResult.Area, CandidateValidationResult.Status> statuses =
                new EnumMap<>(CandidateValidationResult.Area.class);
        for (CandidateValidationResult.Area area : CandidateValidationResult.Area.values()) {
            statuses.put(area, CandidateValidationResult.Status.SKIPPED);
        }
        List<CandidateValidationResult.Issue> issues = new ArrayList<>();

        if (requestedChanges > budget.maxChanges()) {
            issues.add(issue(CandidateValidationResult.Area.BUDGET,
                    CandidateValidationResult.Code.CHANGE_LIMIT_EXCEEDED, null));
        }
        if (requestedEvidence > budget.maxEvidenceRefs()) {
            issues.add(issue(CandidateValidationResult.Area.BUDGET,
                    CandidateValidationResult.Code.EVIDENCE_LIMIT_EXCEEDED, null));
        }
        if (requestedBytes > budget.maxCandidateUtf8Bytes()) {
            issues.add(issue(CandidateValidationResult.Area.BUDGET,
                    CandidateValidationResult.Code.CANDIDATE_TEXT_BYTE_LIMIT_EXCEEDED, null));
        }
        boolean budgetExceeded = !issues.isEmpty();
        statuses.put(CandidateValidationResult.Area.BUDGET, budgetExceeded
                ? CandidateValidationResult.Status.FAILED : CandidateValidationResult.Status.PASSED);

        boolean versionMatches = candidate.workspace().equals(snapshot.workspace());
        if (!budgetExceeded) {
            if (!versionMatches) {
                statuses.put(CandidateValidationResult.Area.VERSION, CandidateValidationResult.Status.FAILED);
                issues.add(issue(CandidateValidationResult.Area.VERSION,
                        CandidateValidationResult.Code.PROJECT_VERSION_STALE, null));
            } else {
                statuses.put(CandidateValidationResult.Area.VERSION, CandidateValidationResult.Status.PASSED);
                validateAgainstSnapshot(candidate, snapshot, statuses, issues);
            }
        }

        List<CandidateValidationResult.Check> checks = statuses.entrySet().stream()
                .map(entry -> new CandidateValidationResult.Check(entry.getKey(), entry.getValue())).toList();
        CandidateValidationResult result = new CandidateValidationResult(candidate.fingerprint(),
                snapshot.workspace().projectVersion(), checks, issues,
                new CandidateValidationResult.Usage(requestedChanges, inspectedChanges,
                        requestedEvidence, inspectedEvidence, requestedBytes, inspectedBytes));
        return new CandidateValidationDecision(result);
    }

    private static void validateAgainstSnapshot(CandidateChangeSet candidate, SandboxWorkspaceSnapshot snapshot,
                                                Map<CandidateValidationResult.Area, CandidateValidationResult.Status> statuses,
                                                List<CandidateValidationResult.Issue> issues) {
        Map<String, SandboxFileSnapshot> files = new HashMap<>();
        for (SandboxFileSnapshot file : snapshot.files()) {
            files.put(SandboxContractSupport.pathConflictKey(file.relativePath()), file);
        }

        boolean structureOk = candidate.governanceStatus() != CandidateChangeSet.GovernanceStatus.STALE;
        if (!structureOk) {
            issues.add(issue(CandidateValidationResult.Area.STRUCTURE,
                    CandidateValidationResult.Code.CANDIDATE_STATE_NOT_VALIDATABLE, null));
        }
        boolean hashesOk = true;
        boolean evidenceOk = true;
        for (CandidateFileChange change : candidate.changes()) {
            SandboxFileSnapshot current = files.get(SandboxContractSupport.pathConflictKey(change.relativePath()));
            if (change.type() == CandidateFileChange.Type.ADD && current != null) {
                structureOk = false;
                issues.add(issue(CandidateValidationResult.Area.STRUCTURE,
                        CandidateValidationResult.Code.ADD_TARGET_ALREADY_EXISTS, change.relativePath()));
            }
            if (change.type() == CandidateFileChange.Type.MODIFY && current == null) {
                structureOk = false;
                hashesOk = false;
                issues.add(issue(CandidateValidationResult.Area.STRUCTURE,
                        CandidateValidationResult.Code.MODIFY_TARGET_MISSING, change.relativePath()));
                issues.add(issue(CandidateValidationResult.Area.CONTENT_HASH,
                        CandidateValidationResult.Code.BASE_FILE_HASH_UNVERIFIABLE, change.relativePath()));
            }
            if (change.type() == CandidateFileChange.Type.DELETE && current == null) {
                structureOk = false;
                hashesOk = false;
                issues.add(issue(CandidateValidationResult.Area.STRUCTURE,
                        CandidateValidationResult.Code.DELETE_TARGET_MISSING, change.relativePath()));
                issues.add(issue(CandidateValidationResult.Area.CONTENT_HASH,
                        CandidateValidationResult.Code.BASE_FILE_HASH_UNVERIFIABLE, change.relativePath()));
            }
            if (current != null && !current.relativePath().equals(change.relativePath())) {
                structureOk = false;
                issues.add(issue(CandidateValidationResult.Area.STRUCTURE,
                        CandidateValidationResult.Code.TARGET_PATH_CASE_MISMATCH, change.relativePath()));
                if (change.type() != CandidateFileChange.Type.ADD) {
                    hashesOk = false;
                    issues.add(issue(CandidateValidationResult.Area.CONTENT_HASH,
                            CandidateValidationResult.Code.BASE_FILE_HASH_UNVERIFIABLE, change.relativePath()));
                }
            }
            if (current != null && change.type() != CandidateFileChange.Type.ADD
                    && !current.fileHash().equals(change.baseFileHash())) {
                hashesOk = false;
                issues.add(issue(CandidateValidationResult.Area.CONTENT_HASH,
                        CandidateValidationResult.Code.BASE_FILE_HASH_MISMATCH, change.relativePath()));
            }
            if (change.candidateText() != null) {
                var recalculated = CandidateTextPayload.hash(CandidateTextPayload.encode(change.candidateText().text()));
                if (!recalculated.equals(change.resultFileHash())) {
                    hashesOk = false;
                    issues.add(issue(CandidateValidationResult.Area.CONTENT_HASH,
                            CandidateValidationResult.Code.RESULT_CONTENT_HASH_MISMATCH, change.relativePath()));
                }
            }
            for (ResearchEvidenceRef evidence : change.evidenceRefs()) {
                SandboxFileSnapshot source = files.get(SandboxContractSupport.pathConflictKey(evidence.relativePath()));
                if (source == null || !source.relativePath().equals(evidence.relativePath())) {
                    evidenceOk = false;
                    issues.add(issue(CandidateValidationResult.Area.EVIDENCE,
                            CandidateValidationResult.Code.EVIDENCE_FILE_MISSING, evidence.relativePath()));
                } else if (!source.fileHash().equals(evidence.fileHash())) {
                    evidenceOk = false;
                    issues.add(issue(CandidateValidationResult.Area.EVIDENCE,
                            CandidateValidationResult.Code.EVIDENCE_FILE_HASH_MISMATCH, evidence.relativePath()));
                }
            }
        }
        statuses.put(CandidateValidationResult.Area.STRUCTURE, status(structureOk));
        statuses.put(CandidateValidationResult.Area.CONTENT_HASH, status(hashesOk));
        statuses.put(CandidateValidationResult.Area.EVIDENCE, status(evidenceOk));
    }

    private static CandidateValidationResult.Status status(boolean passed) {
        return passed ? CandidateValidationResult.Status.PASSED : CandidateValidationResult.Status.FAILED;
    }

    private static CandidateValidationResult.Issue issue(CandidateValidationResult.Area area,
                                                         CandidateValidationResult.Code code,
                                                         com.yanban.core.research.ProjectRelativePath path) {
        return new CandidateValidationResult.Issue(area, code, path);
    }
}
