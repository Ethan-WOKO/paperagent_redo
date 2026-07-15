package com.yanban.core.agent.sandbox;

import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ProjectVersionRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Serializable audit projection. It cannot by itself promote a Candidate. */
public record CandidateValidationResult(
        CandidateFingerprint candidateFingerprint,
        ProjectVersionRef snapshotProjectVersion,
        List<Check> checks,
        List<Issue> issues,
        Usage usage
) implements RejectsUnknownFields {

    public enum Area { STRUCTURE, VERSION, EVIDENCE, CONTENT_HASH, BUDGET }
    public enum Status { PASSED, FAILED, SKIPPED }
    public enum Code {
        CANDIDATE_STATE_NOT_VALIDATABLE,
        PROJECT_VERSION_STALE,
        ADD_TARGET_ALREADY_EXISTS,
        MODIFY_TARGET_MISSING,
        DELETE_TARGET_MISSING,
        TARGET_PATH_CASE_MISMATCH,
        BASE_FILE_HASH_UNVERIFIABLE,
        BASE_FILE_HASH_MISMATCH,
        RESULT_CONTENT_HASH_MISMATCH,
        EVIDENCE_FILE_MISSING,
        EVIDENCE_FILE_HASH_MISMATCH,
        CHANGE_LIMIT_EXCEEDED,
        EVIDENCE_LIMIT_EXCEEDED,
        CANDIDATE_TEXT_BYTE_LIMIT_EXCEEDED
    }

    public record Check(Area area, Status status) implements RejectsUnknownFields {
        public Check {
            if (area == null || status == null) throw new IllegalArgumentException("validation check is incomplete");
        }
    }

    public record Issue(Area area, Code code, ProjectRelativePath relativePath)
            implements RejectsUnknownFields {
        public Issue {
            if (area == null || code == null) throw new IllegalArgumentException("validation issue is incomplete");
        }
    }

    public record Usage(int requestedChanges, int inspectedChanges,
                        int requestedEvidenceRefs, int inspectedEvidenceRefs,
                        long requestedCandidateUtf8Bytes, long inspectedCandidateUtf8Bytes)
            implements RejectsUnknownFields {
        public Usage {
            if (requestedChanges < 0 || inspectedChanges < 0 || requestedEvidenceRefs < 0
                    || inspectedEvidenceRefs < 0 || inspectedChanges > requestedChanges
                    || inspectedEvidenceRefs > requestedEvidenceRefs || requestedCandidateUtf8Bytes < 0
                    || inspectedCandidateUtf8Bytes < 0
                    || inspectedCandidateUtf8Bytes > requestedCandidateUtf8Bytes) {
                throw new IllegalArgumentException("validation usage is inconsistent");
            }
        }
    }

    public CandidateValidationResult {
        if (candidateFingerprint == null || snapshotProjectVersion == null || checks == null
                || issues == null || usage == null) {
            throw new IllegalArgumentException("validation result is incomplete");
        }
        List<Check> sortedChecks = new ArrayList<>(checks);
        sortedChecks.sort(Comparator.comparing(Check::area));
        Set<Area> areas = EnumSet.noneOf(Area.class);
        for (Check check : sortedChecks) {
            if (check == null || !areas.add(check.area())) {
                throw new IllegalArgumentException("validation checks must contain each area exactly once");
            }
        }
        if (!areas.equals(EnumSet.allOf(Area.class))) {
            throw new IllegalArgumentException("validation checks must cover every contract area");
        }
        List<Issue> sortedIssues = issues.stream().distinct().sorted(Comparator
                .comparing(Issue::area)
                .thenComparing(Issue::code)
                .thenComparing(issue -> issue.relativePath() == null ? "" : issue.relativePath().value()))
                .toList();
        for (Issue issue : sortedIssues) {
            Status status = sortedChecks.stream().filter(check -> check.area() == issue.area())
                    .findFirst().orElseThrow().status();
            if (status != Status.FAILED) {
                throw new IllegalArgumentException("validation issue requires a failed area");
            }
        }
        checks = List.copyOf(sortedChecks);
        issues = List.copyOf(sortedIssues);
    }

    public boolean valid() {
        return issues.isEmpty() && checks.stream().allMatch(check -> check.status() == Status.PASSED);
    }

    public boolean hasIssue(Code code) {
        return issues.stream().anyMatch(issue -> issue.code() == code);
    }
}
