package com.yanban.core.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchIndexContractTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void evidenceMapsFromIndexProvenanceWithOnlyPortableMetadataAndSerializesCompatibly() throws Exception {
        IndexedProvenance provenance = provenance(IndexFreshness.CURRENT, false, false);
        ResearchEvidenceRef evidence = ResearchEvidenceRef.from(provenance);

        assertThat(evidence.projectVersion().value()).isEqualTo("manifest-sha256:abc");
        assertThat(evidence.relativePath().value()).isEqualTo("paper/main.tex");
        assertThat(evidence.fileHash().sha256()).isEqualTo(HASH);
        var serialized = json.readTree(json.writeValueAsString(evidence));
        assertThat(serialized.path("projectVersion").asText()).isEqualTo("manifest-sha256:abc");
        assertThat(serialized.path("relativePath").asText()).isEqualTo("paper/main.tex");
        assertThat(serialized.path("fileHash").asText()).isEqualTo(HASH);
        assertThat(serialized.path("range").path("startLine").asInt()).isEqualTo(10);
        assertThat(serialized.path("parserVersion").asText()).isEqualTo("latex-parser@1");
        assertThat(serialized.path("trustLabel").asText()).isEqualTo("SERVER_ATTESTED_METADATA");
        assertThat(json.writeValueAsString(evidence)).doesNotContain("projectId", "userId", "C:\\\\");
    }

    @Test
    void indexModelsRepresentCurrentStaleInvalidatedPartialAndTruncatedStates() {
        LatexSectionIndex current = new LatexSectionIndex("sec:intro", "Introduction", 1,
                provenance(IndexFreshness.CURRENT, false, false));
        ExperimentAssetIndex stale = new ExperimentAssetIndex("CSV_METRICS", "validation metrics",
                provenance(IndexFreshness.STALE, true, false));
        CrossMaterialLinkIndex invalidated = new CrossMaterialLinkIndex("learning rate",
                List.of(provenance(IndexFreshness.INVALIDATED, false, true), provenance(IndexFreshness.INVALIDATED, false, true)),
                "configured-by", IndexFreshness.INVALIDATED);

        assertThat(current.provenance().freshness()).isEqualTo(IndexFreshness.CURRENT);
        assertThat(stale.provenance().partial()).isTrue();
        assertThat(invalidated.linkedMaterials()).allSatisfy(item -> assertThat(item.truncated()).isTrue());
        assertThatThrownBy(() -> new CrossMaterialLinkIndex("one", List.of(provenance(IndexFreshness.CURRENT, false, false)),
                "weak", IndexFreshness.CURRENT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashesVersionsRangesAndBudgetLimitsAreValidated() {
        assertThatThrownBy(() -> new FileHash("not-a-hash")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRange(2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectRelativePath.of("../paper.tex")).isInstanceOf(IllegalArgumentException.class);
        ResearchBudget budget = new ResearchBudget(1, 2, 3, 4);
        assertThatThrownBy(() -> budget.validate(new ResearchBudgetUsage(1, 3, 0, 0)))
                .isInstanceOf(ResearchContractException.class)
                .extracting(error -> ((ResearchContractException) error).errorCode())
                .isEqualTo(ResearchToolErrorCode.BUDGET_EXCEEDED);
    }

    @Test
    void parserVersionIsPortableAndSharedByEvidenceAndIndexProvenance() throws Exception {
        ParserVersionRef portable = new ParserVersionRef("latex-parser@1");
        ResearchEvidenceRef evidence = new ResearchEvidenceRef(new ProjectVersionRef("manifest-sha256:abc"),
                ProjectRelativePath.of("paper/main.tex"), new FileHash(HASH), new SourceRange(1, 1), portable,
                TrustLabel.SERVER_ATTESTED_METADATA);
        IndexedProvenance index = new IndexedProvenance(new FileManifestEntry(new ProjectVersionRef("manifest-sha256:abc"),
                ProjectRelativePath.of("paper/main.tex"), new FileHash(HASH), 1), new SourceRange(1, 1), portable,
                IndexFreshness.CURRENT, false, false, TrustLabel.SERVER_ATTESTED_METADATA);

        assertThat(json.writeValueAsString(evidence)).contains("\"parserVersion\":\"latex-parser@1\"");
        assertThat(index.parserVersion()).isSameAs(portable);
        assertThat(ResearchEvidenceRef.class.getRecordComponents()[4].getType()).isEqualTo(ParserVersionRef.class);
        assertThat(IndexedProvenance.class.getRecordComponents()[2].getType()).isEqualTo(ParserVersionRef.class);
        for (String unsafe : List.of("C:\\secret\\parser.jar", "C:parser.jar", "\\\\server\\share\\parser",
                "file:///secret/parser", "http://host/parser", "https://host/parser", "parser/path", " parser@1", "parser@1 ", "parser\u0000@1")) {
            assertThatThrownBy(() -> new ParserVersionRef(unsafe)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void equivalentObjectArgumentsProduceTheSameServerScopedDeduplicationKey() {
        ObjectNode first = json.createObjectNode().put("query", "loss").put("maxMatches", 10);
        ObjectNode second = json.createObjectNode().put("maxMatches", 10).put("query", "loss");
        ProjectVersionRef version = new ProjectVersionRef("manifest-sha256:abc");

        ResearchCallKey left = ResearchCallKey.of(ResearchToolContracts.PROJECT_CROSS_MATERIAL_SEARCH, version, first);
        ResearchCallKey right = ResearchCallKey.of(ResearchToolContracts.PROJECT_CROSS_MATERIAL_SEARCH, version, second);

        assertThat(left).isEqualTo(right);
        assertThat(left.value()).contains("project_cross_material_search", "manifest-sha256:abc");
    }

    private IndexedProvenance provenance(IndexFreshness freshness, boolean partial, boolean truncated) {
        return new IndexedProvenance(new FileManifestEntry(new ProjectVersionRef("manifest-sha256:abc"),
                ProjectRelativePath.of("paper/main.tex"), new FileHash(HASH), 42), new SourceRange(10, 15),
                new ParserVersionRef("latex-parser@1"), freshness, partial, truncated, TrustLabel.SERVER_ATTESTED_METADATA);
    }
}
