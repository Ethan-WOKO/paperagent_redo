package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.agent.AgentMessage;
import com.yanban.core.agent.AgentMessageRepository;
import com.yanban.core.agent.AgentTurn;
import com.yanban.core.agent.AgentTurnRepository;
import com.yanban.core.model.ChatMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentCanonicalContextBuilderTest {
    private static final long SESSION = 24L;
    private static final long USER = 7L;

    @Mock AgentMessageRepository messages;
    @Mock AgentTurnRepository turns;

    private AgentContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AgentContextBuilder(messages, turns, new ObjectMapper());
    }

    @Test
    void assemblesCurrentOnceCanonicalTurnsSummaryProjectEvidenceAndGovernedChinesePreference() {
        Fixture fixture = fixture(List.of(
                pair(1, "第一问", "第一答"),
                pair(2, "第二问", "第二答"),
                pair(3, "第三问引用第二问", "第三答")
        ));
        fixture.persisted.add(message(999, "process", "temporary process", SESSION, USER));
        fixture.persisted.add(message(1000, "assistant", "duplicate assistant", SESSION, USER));
        when(messages.findBySessionIdOrderByCreatedAtAsc(SESSION)).thenReturn(fixture.persisted);
        when(turns.findBySessionIdAndUserIdOrderByStartedAtDescIdDesc(SESSION, USER))
                .thenReturn(descending(fixture.turns));

        EvidenceRef evidence = new EvidenceRef("web-24", EvidenceSourceType.WEB, "web",
                null, "paragraph-1", "https://example.test", "2026-07-23", "selected for request");
        AgentContextPackage result = builder.build(request(
                "当前原始消息 WORKER24",
                "较早历史的真实滚动摘要",
                new AgentLongTermMemoryContext(
                        "已确认偏好：请使用中文回答。", 1, 1, 0,
                        "hits=1; confirmation=CONFIRMED; scope=USER; provenance=USER_MESSAGE"),
                List.of(new AgentContextEvidence(evidence, "external excerpt")),
                8_000
        ));

        assertThat(result.messages()).noneMatch(message -> "当前原始消息 WORKER24".equals(message.content()));
        assertThat(result.currentUserMessage().content()).isEqualTo("当前原始消息 WORKER24");
        assertThat(result.debugView().currentMessage().content()).isEqualTo("当前原始消息 WORKER24");
        assertThat(result.messages()).extracting(ChatMessage::content)
                .containsSubsequence("第一问", "第一答", "第二问", "第二答", "第三问引用第二问", "第三答")
                .doesNotContain("temporary process", "duplicate assistant");
        assertThat(result.debugView().recentTurns()).hasSize(3);
        assertThat(result.debugView().sessionSummary())
                .extracting(AgentContextDebugView.DebugText::content,
                        AgentContextDebugView.DebugText::present,
                        AgentContextDebugView.DebugText::source)
                .containsExactly("较早历史的真实滚动摘要", true, "agent_session_summaries");
        assertThat(result.debugView().project())
                .extracting(AgentContextDebugView.DebugProject::projectId,
                        AgentContextDebugView.DebugProject::projectVersion)
                .containsExactly(42L, "a".repeat(64));
        assertThat(result.debugView().longTermMemory().content()).contains("使用中文回答");
        assertThat(result.debugView().evidence()).extracting(EvidenceRef::id).containsExactly("web-24");
        assertThat(result.debugView().evidence()).noneMatch(ref -> ref.id().contains("summary")
                || ref.id().contains("memory"));
        assertThat(result.debugView().sections())
                .filteredOn(section -> List.of("session_summary", "long_term_memory", "evidence")
                        .contains(section.type()))
                .allSatisfy(section -> assertThat(section.estimatedCharacters()).isPositive());
    }

    @Test
    void dropsOldestWholeTurnsUnderBudgetAndNeverLeavesHalfTurn() {
        String large = "x".repeat(390);
        Fixture fixture = fixture(List.of(
                pair(1, "old-user-" + large, "old-assistant-" + large),
                pair(2, "middle-user-" + large, "middle-assistant-" + large),
                pair(3, "latest-user-" + large, "latest-assistant-" + large)
        ));
        when(messages.findBySessionIdOrderByCreatedAtAsc(SESSION)).thenReturn(fixture.persisted);
        when(turns.findBySessionIdAndUserIdOrderByStartedAtDescIdDesc(SESSION, USER))
                .thenReturn(descending(fixture.turns));

        AgentContextPackage result = builder.build(request("current", null,
                AgentLongTermMemoryContext.empty(), List.of(), 2_500));

        List<String> contents = result.messages().stream().map(ChatMessage::content).toList();
        assertThat(result.debugView().currentMessage().content()).isEqualTo("current");
        assertThat(result.debugView().recentTurns()).isNotEmpty();
        result.debugView().recentTurns().forEach(turn -> {
            assertThat(contents).contains(turn.user(), turn.assistant());
            assertThat(turn.user()).isNotBlank();
            assertThat(turn.assistant()).isNotBlank();
        });
        assertThat(result.debugView().droppedItems()).anySatisfy(item -> {
            assertThat(item.type()).isEqualTo("message");
            assertThat(item.reason()).contains("context character budget");
            assertThat(item.count()).isEven();
        });
    }

    @Test
    void ignoresForeignSessionMessagesEvenWhenRepositoryInputIsPolluted() {
        Fixture fixture = fixture(List.of(pair(1, "owned question", "owned answer")));
        fixture.persisted.add(message(500, "user", "FOREIGN USER", 999L, USER));
        fixture.persisted.add(message(501, "assistant", "FOREIGN ASSISTANT", 999L, USER));
        when(messages.findBySessionIdOrderByCreatedAtAsc(SESSION)).thenReturn(fixture.persisted);
        when(turns.findBySessionIdAndUserIdOrderByStartedAtDescIdDesc(SESSION, USER))
                .thenReturn(descending(fixture.turns));

        AgentContextPackage result = builder.build(request("owned current", null,
                AgentLongTermMemoryContext.empty(), List.of(), 8_000));

        assertThat(result.messages()).extracting(ChatMessage::content)
                .contains("owned question", "owned answer")
                .doesNotContain("FOREIGN USER", "FOREIGN ASSISTANT");
        assertThat(result.debugView().currentMessage().content()).isEqualTo("owned current");
    }

    @Test
    void projectsEmptyAndTruncatedSummaryStatesWithoutInventingSummaryContent() {
        when(messages.findBySessionIdOrderByCreatedAtAsc(SESSION)).thenReturn(List.of());
        when(turns.findBySessionIdAndUserIdOrderByStartedAtDescIdDesc(SESSION, USER)).thenReturn(List.of());

        AgentContextPackage empty = builder.build(request("current", null,
                AgentLongTermMemoryContext.empty(), List.of(), 8_000));
        assertThat(empty.debugView().sessionSummary().present()).isFalse();
        assertThat(empty.debugView().sessionSummary().truncated()).isFalse();
        assertThat(empty.debugView().sessionSummary().source()).isEqualTo("agent_session_summaries");

        String storedSummary = "older-summary-".repeat(500);
        AgentContextPackage truncated = builder.build(request("current", storedSummary,
                AgentLongTermMemoryContext.empty(), List.of(), 2_000));
        assertThat(truncated.debugView().sessionSummary().present()).isTrue();
        assertThat(truncated.debugView().sessionSummary().truncated()).isTrue();
        assertThat(truncated.debugView().sessionSummary().content())
                .isNotEqualTo(storedSummary)
                .endsWith("[truncated]");
        assertThat(truncated.debugView().droppedItems()).anySatisfy(item -> {
            assertThat(item.type()).isEqualTo("session_summary_content");
            assertThat(item.reason()).contains("Truncated");
        });
    }

    private AgentContextBuildRequest request(String current, String summary,
                                             AgentLongTermMemoryContext memory,
                                             List<AgentContextEvidence> evidence,
                                             int budget) {
        return new AgentContextBuildRequest(
                SESSION, USER, "mock", "model", summary, memory, null, null,
                8, budget, null, evidence, current,
                new AgentContextProjectState(42L, "a".repeat(64))
        );
    }

    private Fixture fixture(List<Pair> pairs) {
        List<AgentMessage> persisted = new ArrayList<>();
        List<AgentTurn> canonical = new ArrayList<>();
        for (Pair pair : pairs) {
            long userId = pair.index * 10L + 1;
            long assistantId = pair.index * 10L + 2;
            persisted.add(message(userId, "user", pair.user, SESSION, USER));
            persisted.add(message(pair.index * 10L + 3, "process", "process " + pair.index, SESSION, USER));
            persisted.add(message(assistantId, "assistant", pair.assistant, SESSION, USER));
            AgentTurn turn = mock(AgentTurn.class);
            when(turn.getId()).thenReturn((long) pair.index);
            when(turn.getUserMessageId()).thenReturn(userId);
            when(turn.getAssistantMessageId()).thenReturn(assistantId);
            when(turn.getStatus()).thenReturn(AgentTurn.STATUS_COMPLETED);
            canonical.add(turn);
        }
        return new Fixture(persisted, canonical);
    }

    private AgentMessage message(long id, String role, String content, long sessionId, long userId) {
        AgentMessage message = mock(AgentMessage.class);
        org.mockito.Mockito.lenient().when(message.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(message.getRole()).thenReturn(role);
        org.mockito.Mockito.lenient().when(message.getContent()).thenReturn(content);
        org.mockito.Mockito.lenient().when(message.getSessionId()).thenReturn(sessionId);
        org.mockito.Mockito.lenient().when(message.getUserId()).thenReturn(userId);
        return message;
    }

    private List<AgentTurn> descending(List<AgentTurn> source) {
        List<AgentTurn> copy = new ArrayList<>(source);
        Collections.reverse(copy);
        return copy;
    }

    private Pair pair(int index, String user, String assistant) {
        return new Pair(index, user, assistant);
    }

    private record Pair(int index, String user, String assistant) { }
    private record Fixture(List<AgentMessage> persisted, List<AgentTurn> turns) { }
}
