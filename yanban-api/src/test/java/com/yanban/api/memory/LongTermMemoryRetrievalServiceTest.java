package com.yanban.api.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.agent.AgentLongTermMemoryContext;
import com.yanban.core.agent.AgentLongTermMemory;
import com.yanban.core.agent.AgentLongTermMemoryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LongTermMemoryRetrievalServiceTest {

    private static final long USER_ID = 42L;

    @Mock
    private AgentLongTermMemoryRepository memories;

    private LongTermMemoryRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new LongTermMemoryRetrievalService(memories, new ObjectMapper());
    }

    @Test
    void retrievesRelevantMemoryByKeywordAndTag() {
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(
                        memory("PREFERENCE", "User prefers GraphRAG answers with explicit ablation caveats.", "[\"GraphRAG\",\"style\"]", "0.86"),
                        memory("PREFERENCE", "User studies compiler optimization.", "[\"systems\"]", "0.90")
                ));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "Please help with GraphRAG evaluation.");

        assertThat(context.hasContent()).isTrue();
        assertThat(context.hitCount()).isEqualTo(1);
        assertThat(context.content()).contains("GraphRAG", "ablation caveats");
        assertThat(context.content()).doesNotContain("compiler optimization");
        assertThat(context.note()).contains("hits=1", "candidates=2", "GraphRAG");
    }

    @Test
    void returnsEmptyWhenNoMemoryMatchesQuery() {
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(memory("FACT", "User studies reinforcement learning.", "[\"RL\"]", "0.80")));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "Discuss literature review structure.");

        assertThat(context.hasContent()).isFalse();
        assertThat(context.hitCount()).isZero();
        assertThat(context.note()).contains("No relevant long-term memory");
    }

    @Test
    void filtersDeletedSupersededAndLowConfidenceMemories() {
        AgentLongTermMemory deleted = memory("FACT", "Deleted GraphRAG memory should not appear.", "[\"GraphRAG\"]", "0.90");
        deleted.markDeleted();
        AgentLongTermMemory superseded = memory("FACT", "Superseded GraphRAG memory should not appear.", "[\"GraphRAG\"]", "0.90");
        superseded.markSuperseded(99L);
        AgentLongTermMemory lowConfidence = memory("FACT", "Low confidence GraphRAG memory should not appear.", "[\"GraphRAG\"]", "0.10");
        AgentLongTermMemory active = memory("FACT", "Active GraphRAG memory should appear.", "[\"GraphRAG\"]", "0.70");

        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(deleted, superseded, lowConfidence, active));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.hasContent()).isTrue();
        assertThat(context.hitCount()).isEqualTo(1);
        assertThat(context.content()).contains("Active GraphRAG memory");
        assertThat(context.content()).doesNotContain("Deleted", "Superseded", "Low confidence");
    }

    @Test
    void failsClosedForCrossUserProjectScopeAndUnconfirmedSources() {
        AgentLongTermMemory crossUser = memory(99L, null, AgentLongTermMemory.SCOPE_USER,
                AgentLongTermMemory.SOURCE_USER_CONFIRMED, "Cross-user GraphRAG memory.", "0.90");
        AgentLongTermMemory project = memory(USER_ID, 7L, "PROJECT",
                AgentLongTermMemory.SOURCE_USER_CONFIRMED, "Unversioned project GraphRAG fact.", "0.90");
        AgentLongTermMemory inferred = memory(USER_ID, null, AgentLongTermMemory.SCOPE_USER,
                "MODEL_INFERRED", "Model-inferred GraphRAG guess.", "0.90");
        AgentLongTermMemory confirmed = memory(USER_ID, null, AgentLongTermMemory.SCOPE_USER,
                AgentLongTermMemory.SOURCE_USER_CONFIRMED, "Confirmed GraphRAG preference.", "0.90");
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(crossUser, project, inferred, confirmed));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.content()).contains("Confirmed GraphRAG preference")
                .doesNotContain("Cross-user", "Unversioned project", "Model-inferred");
        assertThat(context.candidateCount()).isEqualTo(1);
    }

    @Test
    void deduplicatesContentAndRejectsSensitiveOrAbsolutePathText() {
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(
                        memory("PREFERENCE", "GraphRAG answers need caveats.", "[\"GraphRAG\"]", "0.90"),
                        memory("PREFERENCE", "  graphrag answers need   caveats. ", "[\"GraphRAG\"]", "0.89"),
                        memory("FACT", "GraphRAG source is C:\\\\Users\\\\alice\\\\secret.txt", "[\"GraphRAG\"]", "0.95"),
                        memory("FACT", "GraphRAG api_key=do-not-expose", "[\"GraphRAG\"]", "0.95")));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.hitCount()).isEqualTo(1);
        assertThat(context.omittedCount()).isEqualTo(1);
        assertThat(context.content()).contains("GraphRAG answers need caveats")
                .doesNotContain("alice", "do-not-expose");
    }

    @Test
    void deduplicatesBeforeHitLimitSoDistinctLowerRankedMemoryIsRetainedDeterministically() {
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(
                        memory("PREFERENCE", "GraphRAG answers need explicit caveats.", "[\"GraphRAG\"]", "0.95"),
                        memory("PREFERENCE", " graphrag answers need explicit   caveats. ", "[\"GraphRAG\"]", "0.94"),
                        memory("PREFERENCE", "GRAPHRAG ANSWERS NEED EXPLICIT CAVEATS.", "[\"GraphRAG\"]", "0.93"),
                        memory("PREFERENCE", "GraphRAG answers need explicit caveats.", "[\"GraphRAG\"]", "0.92"),
                        memory("PREFERENCE", "GraphRAG answers need explicit caveats.", "[\"GraphRAG\"]", "0.91"),
                        memory("FACT", "GraphRAG evaluation uses a held-out benchmark.", "[\"GraphRAG\"]", "0.80")));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.hitCount()).isEqualTo(2);
        assertThat(context.omittedCount()).isEqualTo(4);
        assertThat(context.content()).contains("GraphRAG answers need explicit caveats",
                "GraphRAG evaluation uses a held-out benchmark");
        assertThat(context.content().indexOf("GraphRAG answers need explicit caveats"))
                .isLessThan(context.content().indexOf("GraphRAG evaluation uses a held-out benchmark"));
    }

    @Test
    void rejectsExpandedCredentialAndLocalPathFormsButKeepsOrdinaryWebUrls() {
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(
                        memory("FACT", "GraphRAG file is /workspace/private/result.txt", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG file is /mnt/data/result.txt", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG file is /root/result.txt", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG file is /data/result.txt", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG file is file:///srv/private/result.txt", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG api key is abcdefghijklmnop", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG Authorization Bearer abcdefghijklmnop", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG token is sk-abcdefghijklmnop", "[\"GraphRAG\"]", "0.90"),
                        memory("FACT", "GraphRAG paper is available at https://example.org/papers/graphrag", "[\"GraphRAG\"]", "0.80")));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.hitCount()).isEqualTo(1);
        assertThat(context.candidateCount()).isEqualTo(1);
        assertThat(context.content()).contains("https://example.org/papers/graphrag")
                .doesNotContain("/workspace", "/mnt", "/root", "/data", "file://", "abcdefghijklmnop", "sk-");
    }

    @Test
    void rejectsUnsafeTagsInvalidTagStructuresAndUnknownMemoryTypesBeforeScoringOrFormatting() {
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(
                        memory("PREFERENCE", "GraphRAG preference with credential tag.",
                                "[\"api key is abcdefghijklmnop\"]", "0.95"),
                        memory("PREFERENCE", "GraphRAG preference with path tag.",
                                "[\"C:\\\\Users\\\\alice\\\\secret.txt\"]", "0.95"),
                        memory("PREFERENCE", "GraphRAG preference with oversized tag.",
                                "[\"" + "x".repeat(65) + "\"]", "0.95"),
                        memory("PREFERENCE", "GraphRAG preference with malformed tags.",
                                "{\"tag\":\"GraphRAG\"}", "0.95"),
                        memory("<script>UNKNOWN</script>", "GraphRAG preference with unknown type.",
                                "[\"GraphRAG\"]", "0.95"),
                        memory("PREFERENCE", "GraphRAG preference with safe academic metadata.",
                                "[\"GraphRAG\",\"https://example.org/topic\"]", "0.80")));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.hitCount()).isEqualTo(1);
        assertThat(context.candidateCount()).isEqualTo(1);
        assertThat(context.content()).contains("safe academic metadata", "GraphRAG", "https://example.org/topic")
                .doesNotContain("credential tag", "path tag", "oversized tag", "malformed tags", "unknown type",
                        "abcdefghijklmnop", "alice", "<script>");
    }

    @Test
    void rejectsTooManyOrNonTextualTagsAndDeduplicatesSafeTagsInStableOrder() {
        String tooManyTags = "[" + java.util.stream.IntStream.range(0, 13)
                .mapToObj(index -> "\"tag" + index + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(
                        memory("FACT", "GraphRAG record with too many tags.", tooManyTags, "0.95"),
                        memory("FACT", "GraphRAG record with non-text tag.", "[\"GraphRAG\",7]", "0.95"),
                        memory("FACT", "GraphRAG record with stable tags.",
                                "[\"GraphRAG\",\"evaluation\",\"GraphRAG\"]", "0.80")));

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.hitCount()).isEqualTo(1);
        assertThat(context.content()).contains("tags=GraphRAG/evaluation")
                .doesNotContain("too many tags", "non-text tag", "GraphRAG/evaluation/GraphRAG");
    }

    @Test
    void reportsBudgetOmissions() {
        List<AgentLongTermMemory> rows = List.of(
                memory("FACT", repeated("GraphRAG memory A "), "[\"GraphRAG\"]", "0.90"),
                memory("FACT", repeated("GraphRAG memory B "), "[\"GraphRAG\"]", "0.89"),
                memory("FACT", repeated("GraphRAG memory C "), "[\"GraphRAG\"]", "0.88"),
                memory("FACT", repeated("GraphRAG memory D "), "[\"GraphRAG\"]", "0.87"),
                memory("FACT", repeated("GraphRAG memory E "), "[\"GraphRAG\"]", "0.86")
        );
        when(memories.findByUserIdAndStatusOrderByUpdatedAtDesc(eq(USER_ID), eq(AgentLongTermMemory.STATUS_ACTIVE), any(Pageable.class)))
                .thenReturn(rows);

        AgentLongTermMemoryContext context = service.retrieve(USER_ID, "GraphRAG");

        assertThat(context.hasContent()).isTrue();
        assertThat(context.hitCount()).isLessThan(5);
        assertThat(context.omittedCount()).isGreaterThan(0);
        assertThat(context.note()).contains("omitted=");
    }

    private AgentLongTermMemory memory(String type, String content, String tagsJson, String confidence) {
        return new AgentLongTermMemory(
                USER_ID,
                null,
                AgentLongTermMemory.SCOPE_USER,
                type,
                content,
                tagsJson,
                AgentLongTermMemory.SOURCE_USER_CONFIRMED,
                null,
                new BigDecimal(confidence),
                null
        );
    }

    private AgentLongTermMemory memory(Long userId, Long projectId, String scope, String sourceType,
                                       String content, String confidence) {
        return new AgentLongTermMemory(userId, projectId, scope, "FACT", content, "[\"GraphRAG\"]",
                sourceType, null, new BigDecimal(confidence), null);
    }

    private String repeated(String value) {
        return value.repeat(40);
    }
}
