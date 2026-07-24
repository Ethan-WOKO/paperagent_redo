package com.yanban.api.agent;

import java.util.List;

/**
 * Safe, persisted projection of the exact server-built ContextPackage. It intentionally excludes
 * runtime policy text, evidence/file bodies, secrets, environment data, and model reasoning.
 */
public record AgentContextDebugView(
        int requestedBudgetCharacters,
        int effectiveBudgetCharacters,
        int estimatedCharacters,
        DebugText currentMessage,
        List<DebugTurn> recentTurns,
        DebugText sessionSummary,
        DebugProject project,
        DebugMemory longTermMemory,
        List<EvidenceRef> evidence,
        List<AgentContextSection> sections,
        List<AgentContextDroppedItem> droppedItems
) {
    public AgentContextDebugView {
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        sections = sections == null ? List.of() : List.copyOf(sections);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
    }

    public record DebugText(String content, boolean present, boolean truncated, String source) { }

    public record DebugTurn(Long turnId, Long userMessageId, Long assistantMessageId,
                            String user, String assistant, int estimatedCharacters) { }

    public record DebugProject(Long projectId, String projectVersion, String source) { }

    public record DebugMemory(String content, int includedCount, int omittedCount,
                              boolean truncated, String source, String note) { }
}
