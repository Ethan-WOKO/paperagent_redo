package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yanban.core.research.FileHash;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ProjectVersionRef;
import com.yanban.core.research.ResearchEvidenceRef;
import java.util.List;
import java.util.Objects;

/** One text-file transition. Rename remains DELETE plus ADD; binary payloads are deferred. */
public final class CandidateFileChange implements RejectsUnknownFields {
    public enum Type { ADD, MODIFY, DELETE }

    private final Type type;
    private final ProjectVersionRef projectVersion;
    private final ProjectRelativePath relativePath;
    private final FileHash baseFileHash;
    private final FileHash resultFileHash;
    private final CandidateTextPayload candidateText;
    private final List<ResearchEvidenceRef> evidenceRefs;

    private CandidateFileChange(Type type, ProjectVersionRef projectVersion, ProjectRelativePath relativePath,
                                FileHash baseFileHash, CandidateTextPayload candidateText,
                                List<ResearchEvidenceRef> evidenceRefs) {
        if (type == null || projectVersion == null || relativePath == null || evidenceRefs == null
                || evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("candidate file change is incomplete");
        }
        if (type == Type.ADD && baseFileHash != null) {
            throw new IllegalArgumentException("ADD forbids a base hash");
        }
        if ((type == Type.MODIFY || type == Type.DELETE) && baseFileHash == null) {
            throw new IllegalArgumentException(type + " requires a base hash");
        }
        if ((type == Type.ADD || type == Type.MODIFY) && candidateText == null) {
            throw new IllegalArgumentException(type + " requires candidate text");
        }
        if (type == Type.DELETE && candidateText != null) {
            throw new IllegalArgumentException("DELETE forbids candidate text");
        }
        this.type = type;
        this.projectVersion = projectVersion;
        this.relativePath = relativePath;
        this.baseFileHash = baseFileHash;
        this.candidateText = candidateText;
        this.resultFileHash = candidateText == null ? null : candidateText.contentHash();
        this.evidenceRefs = SandboxContractSupport.sortedDistinctEvidence(evidenceRefs);
        for (ResearchEvidenceRef evidence : this.evidenceRefs) {
            if (!projectVersion.equals(evidence.projectVersion())) {
                throw new IllegalArgumentException("candidate evidence must match the immutable Project version");
            }
        }
    }

    public static CandidateFileChange add(ProjectVersionRef version, ProjectRelativePath path,
                                          CandidateTextPayload text, List<ResearchEvidenceRef> evidence) {
        return new CandidateFileChange(Type.ADD, version, path, null, text, evidence);
    }

    public static CandidateFileChange modify(ProjectVersionRef version, ProjectRelativePath path, FileHash baseHash,
                                             CandidateTextPayload text, List<ResearchEvidenceRef> evidence) {
        return new CandidateFileChange(Type.MODIFY, version, path, baseHash, text, evidence);
    }

    public static CandidateFileChange delete(ProjectVersionRef version, ProjectRelativePath path, FileHash baseHash,
                                             List<ResearchEvidenceRef> evidence) {
        return new CandidateFileChange(Type.DELETE, version, path, baseHash, null, evidence);
    }

    @JsonCreator
    public static CandidateFileChange fromJson(
            @JsonProperty(value = "type", required = true) String type,
            @JsonProperty(value = "projectVersion", required = true) ProjectVersionRef projectVersion,
            @JsonProperty(value = "relativePath", required = true) ProjectRelativePath relativePath,
            @JsonProperty("baseFileHash") FileHash baseFileHash,
            @JsonProperty("resultFileHash") FileHash resultFileHash,
            @JsonProperty("candidateText") CandidateTextPayload candidateText,
            @JsonProperty(value = "evidenceRefs", required = true) List<ResearchEvidenceRef> evidenceRefs) {
        Type parsed;
        try {
            parsed = Type.valueOf(type);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("unknown candidate change type", ex);
        }
        CandidateFileChange change = new CandidateFileChange(parsed, projectVersion, relativePath,
                baseFileHash, candidateText, evidenceRefs);
        if (!Objects.equals(change.resultFileHash, resultFileHash)) {
            throw new IllegalArgumentException("result file hash does not match candidate UTF-8 content");
        }
        return change;
    }

    @JsonProperty("type") public Type type() { return type; }
    @JsonProperty("projectVersion") public ProjectVersionRef projectVersion() { return projectVersion; }
    @JsonProperty("relativePath") public ProjectRelativePath relativePath() { return relativePath; }
    @JsonProperty("baseFileHash") public FileHash baseFileHash() { return baseFileHash; }
    @JsonProperty("resultFileHash") public FileHash resultFileHash() { return resultFileHash; }
    @JsonProperty("candidateText") public CandidateTextPayload candidateText() { return candidateText; }
    @JsonProperty("evidenceRefs") public List<ResearchEvidenceRef> evidenceRefs() { return evidenceRefs; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CandidateFileChange that)) return false;
        return type == that.type && projectVersion.equals(that.projectVersion)
                && relativePath.equals(that.relativePath) && Objects.equals(baseFileHash, that.baseFileHash)
                && Objects.equals(resultFileHash, that.resultFileHash)
                && Objects.equals(candidateText, that.candidateText) && evidenceRefs.equals(that.evidenceRefs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, projectVersion, relativePath, baseFileHash, resultFileHash, candidateText, evidenceRefs);
    }
}
