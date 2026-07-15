package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authority-free, multi-file Candidate contract. The current yanban-api CandidateChangeSet is a
 * legacy single-file response projection; a future adapter must not map this contract until a
 * server-side validation decision exists.
 */
public final class CandidateChangeSet implements RejectsUnknownFields {
    public enum GovernanceStatus { DRAFT, VALIDATED, INVALID, STALE }
    public enum ApplicationStatus { NOT_APPLIED }

    private final SandboxWorkspaceRef workspace;
    private final List<CandidateFileChange> changes;
    private final GovernanceStatus governanceStatus;
    private final ApplicationStatus applicationStatus;
    private final CandidateFingerprint fingerprint;

    private CandidateChangeSet(SandboxWorkspaceRef workspace, List<CandidateFileChange> changes,
                               GovernanceStatus governanceStatus) {
        if (workspace == null || changes == null || changes.isEmpty() || governanceStatus == null) {
            throw new IllegalArgumentException("candidate requires workspace, changes, and governance status");
        }
        List<CandidateFileChange> sorted = new ArrayList<>(changes.size());
        Set<String> conflictKeys = new HashSet<>();
        for (CandidateFileChange change : changes) {
            if (change == null || !workspace.projectVersion().equals(change.projectVersion())) {
                throw new IllegalArgumentException("every change must match the Candidate Project version");
            }
            if (!conflictKeys.add(SandboxContractSupport.pathConflictKey(change.relativePath()))) {
                throw new IllegalArgumentException("candidate contains duplicate target paths");
            }
            sorted.add(change);
        }
        sorted.sort(SandboxContractSupport.CHANGE_ORDER);
        this.workspace = workspace;
        this.changes = List.copyOf(sorted);
        this.governanceStatus = governanceStatus;
        this.applicationStatus = ApplicationStatus.NOT_APPLIED;
        this.fingerprint = SandboxContractSupport.fingerprint(workspace, this.changes);
    }

    public static CandidateChangeSet draft(SandboxWorkspaceRef workspace, List<CandidateFileChange> changes) {
        return new CandidateChangeSet(workspace, changes, GovernanceStatus.DRAFT);
    }

    @JsonCreator
    public static CandidateChangeSet fromJson(
            @JsonProperty(value = "workspace", required = true) SandboxWorkspaceRef workspace,
            @JsonProperty(value = "changes", required = true) List<CandidateFileChange> changes,
            @JsonProperty(value = "governanceStatus", required = true) String governanceStatus,
            @JsonProperty(value = "applicationStatus", required = true) String applicationStatus,
            @JsonProperty(value = "fingerprint", required = true) CandidateFingerprint fingerprint) {
        if (governanceStatus == null || applicationStatus == null || fingerprint == null) {
            throw new IllegalArgumentException("serialized candidate is missing fail-closed state");
        }
        if (!ApplicationStatus.NOT_APPLIED.name().equals(applicationStatus)) {
            throw new IllegalArgumentException("serialized candidate cannot request application");
        }
        GovernanceStatus restored;
        try {
            restored = GovernanceStatus.valueOf(governanceStatus);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown Candidate governance status", ex);
        }
        if (restored == GovernanceStatus.VALIDATED) restored = GovernanceStatus.DRAFT;
        CandidateChangeSet candidate = new CandidateChangeSet(workspace, changes, restored);
        if (!candidate.fingerprint.equals(fingerprint)) {
            throw new IllegalArgumentException("serialized Candidate fingerprint does not match its content");
        }
        return candidate;
    }

    public CandidateChangeSet applyValidation(CandidateValidationDecision decision) {
        if (decision == null || !fingerprint.equals(decision.result().candidateFingerprint())) {
            throw new IllegalArgumentException("validation decision does not belong to this Candidate");
        }
        boolean snapshotMatchesCandidate = workspace.projectVersion().equals(decision.result().snapshotProjectVersion());
        if (!snapshotMatchesCandidate
                && !decision.result().hasIssue(CandidateValidationResult.Code.PROJECT_VERSION_STALE)) {
            throw new IllegalArgumentException("validation decision belongs to a different sandbox snapshot");
        }
        GovernanceStatus next = decision.result().valid() ? GovernanceStatus.VALIDATED
                : decision.result().hasIssue(CandidateValidationResult.Code.PROJECT_VERSION_STALE)
                ? GovernanceStatus.STALE : GovernanceStatus.INVALID;
        return new CandidateChangeSet(workspace, changes, next);
    }

    @JsonProperty("workspace")
    public SandboxWorkspaceRef workspace() { return workspace; }

    @JsonProperty("changes")
    public List<CandidateFileChange> changes() { return changes; }

    @JsonProperty("governanceStatus")
    public GovernanceStatus governanceStatus() { return governanceStatus; }

    @JsonProperty("applicationStatus")
    public ApplicationStatus applicationStatus() { return applicationStatus; }

    @JsonProperty("fingerprint")
    public CandidateFingerprint fingerprint() { return fingerprint; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CandidateChangeSet that)) return false;
        return workspace.equals(that.workspace) && changes.equals(that.changes)
                && governanceStatus == that.governanceStatus && applicationStatus == that.applicationStatus
                && fingerprint.equals(that.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspace, changes, governanceStatus, applicationStatus, fingerprint);
    }
}
