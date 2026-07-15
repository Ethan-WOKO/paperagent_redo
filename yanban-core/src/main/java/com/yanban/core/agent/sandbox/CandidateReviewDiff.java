package com.yanban.core.agent.sandbox;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yanban.core.research.FileHash;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ProjectVersionRef;
import java.util.List;
import java.util.Objects;

/**
 * Authority-free review projection derived from Candidate full text. It is never an apply input,
 * validation decision, or source of governance state. FULL_TEXT_REPLACEMENT_V1 is canonical for
 * review only; CandidateTextPayload remains the authoritative proposed content.
 */
public final class CandidateReviewDiff implements RejectsUnknownFields {
    public static final String FORMAT = "FULL_TEXT_REPLACEMENT_V1";

    public record Entry(CandidateFileChange.Type type, ProjectRelativePath relativePath,
                        FileHash baseFileHash, FileHash resultFileHash, String replacementText)
            implements RejectsUnknownFields {
        public Entry {
            if (type == null || relativePath == null) {
                throw new IllegalArgumentException("review diff entry is incomplete");
            }
            if (type == CandidateFileChange.Type.ADD && baseFileHash != null) {
                throw new IllegalArgumentException("ADD review diff forbids a base hash");
            }
            if (type != CandidateFileChange.Type.ADD && baseFileHash == null) {
                throw new IllegalArgumentException(type + " review diff requires a base hash");
            }
            if (type == CandidateFileChange.Type.DELETE) {
                if (resultFileHash != null || replacementText != null) {
                    throw new IllegalArgumentException("DELETE review diff forbids replacement content");
                }
            } else {
                if (resultFileHash == null || replacementText == null
                        || !CandidateTextPayload.hash(CandidateTextPayload.encode(replacementText)).equals(resultFileHash)) {
                    throw new IllegalArgumentException("review diff replacement text does not match its result hash");
                }
            }
        }
    }

    private final String format;
    private final CandidateFingerprint sourceCandidateFingerprint;
    private final ProjectVersionRef projectVersion;
    private final List<Entry> entries;

    private CandidateReviewDiff(String format, CandidateFingerprint sourceCandidateFingerprint,
                                ProjectVersionRef projectVersion, List<Entry> entries) {
        if (!FORMAT.equals(format) || sourceCandidateFingerprint == null || projectVersion == null
                || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("review diff projection is incomplete or unsupported");
        }
        this.format = format;
        this.sourceCandidateFingerprint = sourceCandidateFingerprint;
        this.projectVersion = projectVersion;
        this.entries = List.copyOf(entries);
    }

    public static CandidateReviewDiff derive(CandidateChangeSet candidate) {
        if (candidate == null) throw new IllegalArgumentException("Candidate must not be null");
        List<Entry> entries = candidate.changes().stream().map(change -> new Entry(change.type(),
                change.relativePath(), change.baseFileHash(), change.resultFileHash(),
                change.candidateText() == null ? null : change.candidateText().text())).toList();
        return new CandidateReviewDiff(FORMAT, candidate.fingerprint(),
                candidate.workspace().projectVersion(), entries);
    }

    @JsonCreator
    public static CandidateReviewDiff fromJson(
            @JsonProperty(value = "format", required = true) String format,
            @JsonProperty(value = "sourceCandidateFingerprint", required = true)
            CandidateFingerprint sourceCandidateFingerprint,
            @JsonProperty(value = "projectVersion", required = true) ProjectVersionRef projectVersion,
            @JsonProperty(value = "entries", required = true) List<Entry> entries) {
        return new CandidateReviewDiff(format, sourceCandidateFingerprint, projectVersion, entries);
    }

    @JsonProperty("format") public String format() { return format; }
    @JsonProperty("sourceCandidateFingerprint")
    public CandidateFingerprint sourceCandidateFingerprint() { return sourceCandidateFingerprint; }
    @JsonProperty("projectVersion") public ProjectVersionRef projectVersion() { return projectVersion; }
    @JsonProperty("entries") public List<Entry> entries() { return entries; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CandidateReviewDiff that)) return false;
        return format.equals(that.format) && sourceCandidateFingerprint.equals(that.sourceCandidateFingerprint)
                && projectVersion.equals(that.projectVersion) && entries.equals(that.entries);
    }

    @Override
    public int hashCode() { return Objects.hash(format, sourceCandidateFingerprint, projectVersion, entries); }
}
