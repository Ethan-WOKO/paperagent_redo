package com.yanban.core.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yanban.core.research.FileHash;
import com.yanban.core.research.ParserVersionRef;
import com.yanban.core.research.ProjectManifestIdentity;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.core.research.ProjectVersionRef;
import com.yanban.core.research.ResearchEvidenceRef;
import com.yanban.core.research.ResearchRuntimeScope;
import com.yanban.core.research.SourceRange;
import com.yanban.core.research.TrustLabel;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SandboxCandidateContractsTest {
    private static final FileHash A = new FileHash("1".repeat(64));
    private static final FileHash B = new FileHash("2".repeat(64));
    private static final FileHash C = new FileHash("3".repeat(64));
    private static final List<SandboxFileSnapshot> BASE_FILES = List.of(
            file("paper/main.tex", A, 120), file("old.txt", A, 3), file("b.txt", A, 4), file("c.txt", C, 5),
            file("one.tex", A, 6), file("two.tex", A, 7), file("three.tex", A, 8),
            file("config/run.yaml", A, 9), file("a/ref.tex", A, 10), file("z/ref.tex", A, 11));
    private static final ProjectVersionRef VERSION = manifestVersion(BASE_FILES);
    private static final SandboxWorkspaceSnapshot SNAPSHOT = snapshot(21, BASE_FILES);
    private static final ResearchRuntimeScope AUTHORITY = authority(21, VERSION,
            Set.of(SandboxSnapshotAttestor.REQUIRED_READ_CAPABILITY));
    private static final SandboxSnapshotAttestation ATTESTATION =
            SandboxSnapshotAttestor.attestServerResolved(AUTHORITY, SNAPSHOT);
    private static final SandboxWorkspaceSnapshot EMPTY_SNAPSHOT = snapshot(21, List.of());

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void snapshotIsManifestBoundDeterministicAuthorityFreeAndSupportsEmptyManifest() throws Exception {
        List<SandboxFileSnapshot> files = List.of(file("paper/main.tex", A, 120), file("src/Main.java", B, 80));
        SandboxWorkspaceSnapshot first = snapshot(42, files);
        SandboxWorkspaceSnapshot reordered = snapshot(42, List.of(files.get(1), files.get(0)));

        assertThat(first).isEqualTo(reordered);
        assertThat(first.files()).extracting(item -> item.relativePath().value())
                .containsExactly("paper/main.tex", "src/Main.java");
        assertThat(EMPTY_SNAPSHOT.files()).isEmpty();
        assertThat(EMPTY_SNAPSHOT.workspace().projectVersion())
                .isEqualTo(ProjectManifestIdentity.derive(List.of()));

        String serialized = json.writeValueAsString(first);
        assertThat(serialized).doesNotContain("userId", "capability", "absolutePath", "host", "C:\\");
        assertThat(json.readValue(serialized, SandboxWorkspaceSnapshot.class)).isEqualTo(first);
        assertThatThrownBy(() -> json.writeValueAsString(ATTESTATION)).isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> json.writeValueAsString(AUTHORITY)).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void deserializedSelfConsistentSnapshotRequiresMatchingRuntimeAuthorityAndReadCapability() throws Exception {
        SandboxWorkspaceSnapshot clientSnapshot = json.readValue(json.writeValueAsString(SNAPSHOT),
                SandboxWorkspaceSnapshot.class);
        assertThat(clientSnapshot).isEqualTo(SNAPSHOT);

        assertThatThrownBy(() -> SandboxSnapshotAttestor.attestServerResolved(null, clientSnapshot))
                .hasMessageContaining("runtime authority");
        assertThatThrownBy(() -> SandboxSnapshotAttestor.attestServerResolved(
                authority(999, VERSION, Set.of(SandboxSnapshotAttestor.REQUIRED_READ_CAPABILITY)), clientSnapshot))
                .hasMessageContaining("does not own");
        assertThatThrownBy(() -> SandboxSnapshotAttestor.attestServerResolved(
                authority(21, EMPTY_SNAPSHOT.workspace().projectVersion(),
                        Set.of(SandboxSnapshotAttestor.REQUIRED_READ_CAPABILITY)), clientSnapshot))
                .hasMessageContaining("does not attest");
        assertThatThrownBy(() -> SandboxSnapshotAttestor.attestServerResolved(
                authority(21, VERSION, Set.of()), clientSnapshot))
                .hasMessageContaining("capability is not present");

        SandboxSnapshotAttestation attestation = SandboxSnapshotAttestor.attestServerResolved(AUTHORITY,
                clientSnapshot);
        assertThatThrownBy(() -> json.writeValueAsString(attestation)).isInstanceOf(JsonProcessingException.class);
        String snapshotJson = json.writeValueAsString(clientSnapshot);
        assertThat(snapshotJson).doesNotContain("trustedUserId", "trustedCapabilities", "userId", "capability");
    }

    @Test
    void snapshotRejectsArbitraryVersionAndCaseFoldedPathConflicts() {
        SandboxFileSnapshot upper = file("Paper/Main.tex", A, 1);
        SandboxFileSnapshot lower = file("paper/main.tex", B, 1);
        assertThatThrownBy(() -> new SandboxWorkspaceSnapshot(
                new SandboxWorkspaceRef(1, manifestVersion(List.of(upper))), List.of(upper, lower)))
                .hasMessageContaining("duplicate target path");

        assertThatThrownBy(() -> new SandboxWorkspaceSnapshot(
                new SandboxWorkspaceRef(1, new ProjectVersionRef("f".repeat(64))), List.of(upper)))
                .hasMessageContaining("manifest does not match");
    }

    @Test
    void portablePathContractRejectsAbsoluteTraversalUncDriveAndAlternateSeparators() {
        for (String unsafe : List.of("", "../paper.tex", "/paper.tex", "C:/paper.tex",
                "C:\\paper.tex", "\\\\server\\share\\paper.tex", "paper\\main.tex", "paper//main.tex")) {
            assertThatThrownBy(() -> path(unsafe)).as(unsafe).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void textPayloadUsesExactUtf8AndChangeTypesFreezePayloadAndHashSemantics() throws Exception {
        CandidateTextPayload unicode = CandidateTextPayload.fromText("α");
        assertThat(unicode.utf8Bytes()).isEqualTo(2);
        assertThat(unicode.contentHash()).isEqualTo(CandidateTextPayload.hash(CandidateTextPayload.encode("α")));
        assertThat(CandidateTextPayload.fromText("").utf8Bytes()).isZero();
        assertThatThrownBy(() -> CandidateTextPayload.fromText("\ud800"))
                .hasMessageContaining("not valid Unicode");

        CandidateFileChange add = add("new.txt", "new content", evidence("paper/main.tex", 1));
        CandidateFileChange modify = modify("paper/main.tex", A, "replacement", evidence("paper/main.tex", 2));
        CandidateFileChange delete = delete("old.txt", A, evidence("paper/main.tex", 3));
        assertThat(add.baseFileHash()).isNull();
        assertThat(add.resultFileHash()).isEqualTo(add.candidateText().contentHash());
        assertThat(modify.resultFileHash()).isEqualTo(modify.candidateText().contentHash());
        assertThat(delete.candidateText()).isNull();
        assertThat(delete.resultFileHash()).isNull();

        ObjectNode maliciousDelete = (ObjectNode) json.readTree(json.writeValueAsString(delete));
        maliciousDelete.set("candidateText", json.valueToTree(CandidateTextPayload.fromText("forbidden")));
        assertThatThrownBy(() -> json.treeToValue(maliciousDelete, CandidateFileChange.class))
                .isInstanceOf(JsonProcessingException.class).hasMessageContaining("DELETE forbids");
    }

    @Test
    void evidenceMustBeCompleteSameVersionAndIsDeterministicallyDeduplicated() {
        ResearchEvidenceRef later = evidence("paper/main.tex", 20);
        ResearchEvidenceRef earlier = evidence("config/run.yaml", 2);
        CandidateFileChange change = CandidateFileChange.modify(VERSION, path("paper/main.tex"), A,
                CandidateTextPayload.fromText("replacement"), List.of(later, earlier, later));

        assertThat(change.evidenceRefs()).containsExactly(earlier, later);
        ResearchEvidenceRef crossVersion = new ResearchEvidenceRef(EMPTY_SNAPSHOT.workspace().projectVersion(),
                path("paper/main.tex"), A, new SourceRange(1, 1), new ParserVersionRef("latex@1"),
                TrustLabel.UNTRUSTED_PROJECT_CONTENT);
        assertThatThrownBy(() -> CandidateFileChange.modify(VERSION, path("paper/main.tex"), A,
                CandidateTextPayload.fromText("replacement"), List.of(crossVersion)))
                .hasMessageContaining("immutable Project version");
        assertThatThrownBy(() -> new ResearchEvidenceRef(VERSION, path("paper/main.tex"), A,
                new SourceRange(1, 1), null, TrustLabel.UNTRUSTED_PROJECT_CONTENT))
                .hasMessageContaining("provenance is incomplete");
    }

    @Test
    void candidateRejectsEmptyCrossVersionAndCaseFoldedTargetConflicts() {
        assertThatThrownBy(() -> CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of()))
                .hasMessageContaining("candidate requires");
        CandidateFileChange otherVersion = CandidateFileChange.add(EMPTY_SNAPSHOT.workspace().projectVersion(),
                path("new.txt"), CandidateTextPayload.fromText("new"), List.of(new ResearchEvidenceRef(
                        EMPTY_SNAPSHOT.workspace().projectVersion(), path("source.txt"), A, new SourceRange(1, 1),
                        new ParserVersionRef("text@1"), TrustLabel.SERVER_ATTESTED_METADATA)));
        assertThatThrownBy(() -> CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(otherVersion)))
                .hasMessageContaining("Candidate Project version");

        assertThatThrownBy(() -> CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                add("Src/Main.java", "one", evidence("paper/main.tex", 1)),
                add("src/main.java", "two", evidence("paper/main.tex", 2)))))
                .hasMessageContaining("duplicate target paths");
    }

    @Test
    void fingerprintCoversExactCandidateTextAndIsStableAcrossInputOrder() {
        CandidateFileChange add = add("z/new.txt", "alpha", evidence("paper/main.tex", 5));
        CandidateFileChange modify = CandidateFileChange.modify(VERSION, path("paper/main.tex"), A,
                CandidateTextPayload.fromText("beta"), List.of(evidence("z/ref.tex", 9),
                        evidence("a/ref.tex", 1), evidence("z/ref.tex", 9)));
        CandidateChangeSet first = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(add, modify));
        CandidateChangeSet second = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(modify, add));
        CandidateChangeSet changedText = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                add("z/new.txt", "alpha!", evidence("paper/main.tex", 5)), modify));

        assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
        assertThat(changedText.fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(first.changes()).extracting(change -> change.relativePath().value())
                .containsExactly("paper/main.tex", "z/new.txt");
    }

    @Test
    void correctAddModifyDeleteValidateFromRealSnapshotAcrossAllFiveAreas() {
        CandidateChangeSet candidate = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                add("new.txt", "new", evidence("paper/main.tex", 1)),
                modify("paper/main.tex", A, "changed", evidence("paper/main.tex", 2)),
                delete("old.txt", A, evidence("paper/main.tex", 3))));
        CandidateValidationDecision decision = validate(candidate, ATTESTATION, generousBudget());

        assertThat(decision.result().valid()).isTrue();
        assertThat(decision.result().snapshotProjectVersion()).isEqualTo(VERSION);
        assertThat(decision.result().checks()).extracting(CandidateValidationResult.Check::area)
                .containsExactly(CandidateValidationResult.Area.STRUCTURE, CandidateValidationResult.Area.VERSION,
                        CandidateValidationResult.Area.EVIDENCE, CandidateValidationResult.Area.CONTENT_HASH,
                        CandidateValidationResult.Area.BUDGET);
        assertThat(decision.result().checks()).extracting(CandidateValidationResult.Check::status)
                .containsOnly(CandidateValidationResult.Status.PASSED);
        assertThat(candidate.applyValidation(decision).governanceStatus())
                .isEqualTo(CandidateChangeSet.GovernanceStatus.VALIDATED);
        assertThatThrownBy(() -> json.writeValueAsString(decision)).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void validatorRejectsWrongBaseHashAndRecomputesResultContentHash() throws Exception {
        CandidateChangeSet wrongBase = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                modify("paper/main.tex", B, "changed", evidence("paper/main.tex", 1))));
        CandidateValidationResult result = validate(wrongBase, ATTESTATION, generousBudget()).result();
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(CandidateValidationResult.Issue::code)
                .containsExactly(CandidateValidationResult.Code.BASE_FILE_HASH_MISMATCH);
        assertArea(result, CandidateValidationResult.Area.CONTENT_HASH, CandidateValidationResult.Status.FAILED);

        ObjectNode jsonChange = (ObjectNode) json.readTree(json.writeValueAsString(candidate())).path("changes").get(0);
        jsonChange.put("resultFileHash", "f".repeat(64));
        assertThatThrownBy(() -> json.treeToValue(jsonChange, CandidateFileChange.class))
                .isInstanceOf(JsonProcessingException.class).hasMessageContaining("does not match candidate UTF-8 content");
    }

    @Test
    void validatorRejectsExistingAddMissingModifyDeleteAndCaseMismatch() {
        List<CandidateValidationResult.Code> codes = List.of(
                validationCode(add("paper/main.tex", "duplicate", evidence("paper/main.tex", 1))),
                validationCode(modify("missing.txt", A, "change", evidence("paper/main.tex", 2))),
                validationCode(delete("gone.txt", A, evidence("paper/main.tex", 3))),
                validationCode(modify("Paper/Main.tex", A, "case", evidence("paper/main.tex", 4))));
        assertThat(codes).containsExactly(
                CandidateValidationResult.Code.ADD_TARGET_ALREADY_EXISTS,
                CandidateValidationResult.Code.MODIFY_TARGET_MISSING,
                CandidateValidationResult.Code.DELETE_TARGET_MISSING,
                CandidateValidationResult.Code.TARGET_PATH_CASE_MISMATCH);
    }

    @Test
    void validatorChecksEvidenceFileExistenceAndManifestHash() {
        ResearchEvidenceRef absent = new ResearchEvidenceRef(VERSION, path("missing-evidence.tex"), A,
                new SourceRange(1, 1), new ParserVersionRef("latex@1"), TrustLabel.UNTRUSTED_PROJECT_CONTENT);
        ResearchEvidenceRef wrongHash = new ResearchEvidenceRef(VERSION, path("paper/main.tex"), B,
                new SourceRange(2, 2), new ParserVersionRef("latex@1"), TrustLabel.UNTRUSTED_PROJECT_CONTENT);
        CandidateChangeSet candidate = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                CandidateFileChange.modify(VERSION, path("paper/main.tex"), A,
                        CandidateTextPayload.fromText("changed"), List.of(absent, wrongHash))));
        CandidateValidationResult result = validate(candidate, ATTESTATION, generousBudget()).result();

        assertThat(result.issues()).extracting(CandidateValidationResult.Issue::code).containsExactly(
                CandidateValidationResult.Code.EVIDENCE_FILE_MISSING,
                CandidateValidationResult.Code.EVIDENCE_FILE_HASH_MISMATCH);
        assertArea(result, CandidateValidationResult.Area.EVIDENCE, CandidateValidationResult.Status.FAILED);
    }

    @Test
    void staleSnapshotDecisionIsBoundToCandidateAndSnapshotVersion() {
        CandidateChangeSet candidate = candidate();
        CandidateValidationDecision stale = validate(candidate,
                SandboxSnapshotAttestor.attestServerResolved(authority(21,
                        EMPTY_SNAPSHOT.workspace().projectVersion(),
                        Set.of(SandboxSnapshotAttestor.REQUIRED_READ_CAPABILITY)), EMPTY_SNAPSHOT), generousBudget());
        assertThat(stale.result().hasIssue(CandidateValidationResult.Code.PROJECT_VERSION_STALE)).isTrue();
        assertThat(stale.result().checks()).filteredOn(check -> check.area() != CandidateValidationResult.Area.VERSION
                        && check.area() != CandidateValidationResult.Area.BUDGET)
                .extracting(CandidateValidationResult.Check::status).containsOnly(CandidateValidationResult.Status.SKIPPED);
        assertThat(candidate.applyValidation(stale).governanceStatus())
                .isEqualTo(CandidateChangeSet.GovernanceStatus.STALE);

        CandidateChangeSet different = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                modify("paper/main.tex", A, "different", evidence("paper/main.tex", 1))));
        CandidateValidationDecision originalDecision = validate(candidate, ATTESTATION, generousBudget());
        assertThatThrownBy(() -> different.applyValidation(originalDecision))
                .hasMessageContaining("does not belong to this Candidate");
    }

    @Test
    void budgetsCoverChangesEvidenceAndCandidateUtf8BytesAndSkipUncheckedAreas() {
        CandidateChangeSet candidate = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                add("a.txt", "1234", evidence("one.tex", 1)),
                modify("b.txt", A, "5678", evidence("two.tex", 2)),
                delete("c.txt", C, evidence("three.tex", 3))));
        CandidateValidationResult limited = validate(candidate, ATTESTATION,
                new CandidateValidationBudget(2, 2, 5)).result();

        assertThat(limited.issues()).extracting(CandidateValidationResult.Issue::code).containsExactly(
                CandidateValidationResult.Code.CHANGE_LIMIT_EXCEEDED,
                CandidateValidationResult.Code.EVIDENCE_LIMIT_EXCEEDED,
                CandidateValidationResult.Code.CANDIDATE_TEXT_BYTE_LIMIT_EXCEEDED);
        assertArea(limited, CandidateValidationResult.Area.BUDGET, CandidateValidationResult.Status.FAILED);
        assertThat(limited.checks()).filteredOn(check -> check.area() != CandidateValidationResult.Area.BUDGET)
                .extracting(CandidateValidationResult.Check::status)
                .containsOnly(CandidateValidationResult.Status.SKIPPED);
        assertThat(limited.usage()).isEqualTo(new CandidateValidationResult.Usage(3, 2, 3, 2, 8, 5));
        assertThat(validate(candidate, ATTESTATION, new CandidateValidationBudget(2, 2, 5)).result())
                .isEqualTo(limited);
    }

    @Test
    void maliciousLargeJsonAndTamperedTextIdentityFailClosed() throws Exception {
        String oversized = "x".repeat(CandidateTextPayload.MAX_UTF8_BYTES + 1);
        ObjectNode payload = json.createObjectNode().put("text", oversized)
                .put("utf8Bytes", oversized.length()).put("contentHash", "a".repeat(64));
        assertThatThrownBy(() -> json.treeToValue(payload, CandidateTextPayload.class))
                .isInstanceOf(JsonProcessingException.class).hasMessageContaining("absolute contract byte limit");

        ObjectNode tampered = (ObjectNode) json.valueToTree(CandidateTextPayload.fromText("safe"));
        tampered.put("text", "unsafe");
        assertThatThrownBy(() -> json.treeToValue(tampered, CandidateTextPayload.class))
                .isInstanceOf(JsonProcessingException.class).hasMessageContaining("identity does not match");
    }

    @Test
    void jsonRoundTripCannotRestoreValidatedOrAppliedAuthority() throws Exception {
        CandidateChangeSet draft = candidate();
        CandidateChangeSet validated = draft.applyValidation(validate(draft, ATTESTATION, generousBudget()));
        String serialized = json.writeValueAsString(validated);
        CandidateChangeSet restored = json.readValue(serialized, CandidateChangeSet.class);

        assertThat(restored.fingerprint()).isEqualTo(validated.fingerprint());
        assertThat(restored.governanceStatus()).isEqualTo(CandidateChangeSet.GovernanceStatus.DRAFT);
        assertThat(restored.applicationStatus()).isEqualTo(CandidateChangeSet.ApplicationStatus.NOT_APPLIED);
        assertThat(serialized).doesNotContain("userId", "capability", "absolutePath", "workingDirectory",
                "secret", "reasoning", "chainOfThought");
        CandidateValidationResult result = validate(draft, ATTESTATION, generousBudget()).result();
        String resultJson = json.writeValueAsString(result);
        assertThat(json.readValue(resultJson, CandidateValidationResult.class)).isEqualTo(result);
        assertThat(resultJson).doesNotContain("trustedUserId", "trustedCapabilities", "userId", "capability");

        assertThatThrownBy(() -> json.readValue(serialized.replace("NOT_APPLIED", "APPLIED"),
                CandidateChangeSet.class)).isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> json.readValue(serialized.replace("VALIDATED", "APPROVED"),
                CandidateChangeSet.class)).isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> json.readValue(serialized.replaceFirst(",\"applicationStatus\":\"NOT_APPLIED\"", ""),
                CandidateChangeSet.class)).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void reviewDiffIsDeterministicAuditProjectionWithoutValidationOrApplicationAuthority() throws Exception {
        CandidateChangeSet candidate = CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                add("new.txt", "new text", evidence("paper/main.tex", 1)),
                modify("paper/main.tex", A, "replacement", evidence("paper/main.tex", 2)),
                delete("old.txt", A, evidence("paper/main.tex", 3))));
        CandidateReviewDiff first = CandidateReviewDiff.derive(candidate);
        CandidateReviewDiff second = CandidateReviewDiff.derive(candidate);

        assertThat(first).isEqualTo(second);
        assertThat(first.format()).isEqualTo(CandidateReviewDiff.FORMAT);
        assertThat(first.sourceCandidateFingerprint()).isEqualTo(candidate.fingerprint());
        assertThat(first.entries()).extracting(CandidateReviewDiff.Entry::replacementText)
                .containsExactly("new text", null, "replacement");

        String serialized = json.writeValueAsString(first);
        assertThat(json.readValue(serialized, CandidateReviewDiff.class)).isEqualTo(first);
        assertThat(serialized).doesNotContain("governanceStatus", "applicationStatus", "VALIDATED",
                "NOT_APPLIED", "attestation", "userId", "capability");
        assertThat(java.util.Arrays.stream(CandidateReviewDiff.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("applyValidation", "validate", "attestServerResolved");
        assertThat(candidate.governanceStatus()).isEqualTo(CandidateChangeSet.GovernanceStatus.DRAFT);

        ObjectNode tampered = (ObjectNode) json.readTree(serialized);
        ((ObjectNode) tampered.path("entries").get(0)).put("replacementText", "tampered");
        assertThatThrownBy(() -> json.treeToValue(tampered, CandidateReviewDiff.class))
                .isInstanceOf(JsonProcessingException.class).hasMessageContaining("does not match its result hash");
        assertThat(candidate.fingerprint()).isEqualTo(first.sourceCandidateFingerprint());
    }

    @Test
    void permissiveMapperStillRejectsUnknownFieldsAndLegacyMissingProvenance() throws Exception {
        JsonNode tree = json.readTree(json.writeValueAsString(candidate()));
        ((ObjectNode) tree).put("userId", 999);
        ObjectMapper permissive = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertThatThrownBy(() -> permissive.treeToValue(tree, CandidateChangeSet.class))
                .isInstanceOf(JsonProcessingException.class).hasMessageContaining("unknown sandbox contract field");

        JsonNode nested = json.readTree(json.writeValueAsString(candidate()));
        ((ObjectNode) nested.path("workspace")).put("absolutePath", "C:\\secret");
        assertThatThrownBy(() -> permissive.treeToValue(nested, CandidateChangeSet.class))
                .isInstanceOf(JsonProcessingException.class).hasMessageContaining("unknown sandbox contract field");

        JsonNode missingParser = json.readTree(json.writeValueAsString(candidate()));
        ((ObjectNode) missingParser.path("changes").get(0).path("evidenceRefs").get(0)).remove("parserVersion");
        assertThatThrownBy(() -> json.treeToValue(missingParser, CandidateChangeSet.class))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> json.readValue(json.writeValueAsString(candidate()).replace("MODIFY", "RENAME"),
                CandidateChangeSet.class)).isInstanceOf(JsonProcessingException.class);
    }

    private CandidateChangeSet candidate() {
        return CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(
                modify("paper/main.tex", A, "replacement", evidence("paper/main.tex", 4))));
    }

    private static CandidateFileChange add(String relativePath, String text, ResearchEvidenceRef evidence) {
        return CandidateFileChange.add(VERSION, path(relativePath), CandidateTextPayload.fromText(text), List.of(evidence));
    }

    private static CandidateFileChange modify(String relativePath, FileHash base, String text,
                                              ResearchEvidenceRef evidence) {
        return CandidateFileChange.modify(VERSION, path(relativePath), base,
                CandidateTextPayload.fromText(text), List.of(evidence));
    }

    private static CandidateFileChange delete(String relativePath, FileHash base, ResearchEvidenceRef evidence) {
        return CandidateFileChange.delete(VERSION, path(relativePath), base, List.of(evidence));
    }

    private static ResearchEvidenceRef evidence(String relativePath, int line) {
        return new ResearchEvidenceRef(VERSION, path(relativePath), A, new SourceRange(line, line),
                new ParserVersionRef("latex@1"), TrustLabel.UNTRUSTED_PROJECT_CONTENT);
    }

    private static CandidateValidationDecision validate(CandidateChangeSet candidate,
                                                        SandboxSnapshotAttestation attestation,
                                                        CandidateValidationBudget budget) {
        return CandidateValidator.validate(candidate, attestation, budget);
    }

    private static CandidateValidationBudget generousBudget() {
        return new CandidateValidationBudget(100, 100, 1024 * 1024);
    }

    private static SandboxFileSnapshot file(String relativePath, FileHash hash, long size) {
        return new SandboxFileSnapshot(path(relativePath), hash, size);
    }

    private static SandboxWorkspaceSnapshot snapshot(long projectId, List<SandboxFileSnapshot> files) {
        return new SandboxWorkspaceSnapshot(new SandboxWorkspaceRef(projectId, manifestVersion(files)), files);
    }

    private static ResearchRuntimeScope authority(long projectId, ProjectVersionRef version,
                                                  Set<String> capabilities) {
        return new ResearchRuntimeScope(projectId, 7, capabilities, version);
    }

    private static ProjectVersionRef manifestVersion(List<SandboxFileSnapshot> files) {
        return ProjectManifestIdentity.derive(files.stream().map(item ->
                new ProjectManifestIdentity.Entry(item.relativePath(), item.fileHash(), item.sizeBytes())).toList());
    }

    private static ProjectRelativePath path(String value) { return ProjectRelativePath.of(value); }

    private static void assertArea(CandidateValidationResult result, CandidateValidationResult.Area area,
                                   CandidateValidationResult.Status status) {
        assertThat(result.checks()).filteredOn(check -> check.area() == area)
                .extracting(CandidateValidationResult.Check::status).containsExactly(status);
    }

    private static CandidateValidationResult.Code validationCode(CandidateFileChange change) {
        CandidateValidationResult result = validate(CandidateChangeSet.draft(SNAPSHOT.workspace(), List.of(change)),
                ATTESTATION, generousBudget()).result();
        assertArea(result, CandidateValidationResult.Area.STRUCTURE, CandidateValidationResult.Status.FAILED);
        if (change.type() != CandidateFileChange.Type.ADD) {
            assertArea(result, CandidateValidationResult.Area.CONTENT_HASH, CandidateValidationResult.Status.FAILED);
            assertThat(result.issues()).anyMatch(issue -> issue.code()
                    == CandidateValidationResult.Code.BASE_FILE_HASH_UNVERIFIABLE);
        }
        return result.issues().stream().filter(issue -> issue.area() == CandidateValidationResult.Area.STRUCTURE)
                .findFirst().orElseThrow().code();
    }
}
