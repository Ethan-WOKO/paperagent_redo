package com.yanban.api.agent;

import com.yanban.core.agent.AgentTaskOutcome;
import com.yanban.core.agent.AgentTaskPhase;
import com.yanban.core.agent.AgentTaskState;
import com.yanban.core.agent.AgentTaskStatus;
import com.yanban.core.agent.AgentRunIdentity;
import com.yanban.core.agent.AgentRunStateMappings;

/** Read-only, migration-free projection shared by synchronous Chat/ReAct and Plan adapters. */
public record AgentRunProjection(AgentRunIdentity identity, AgentTaskState state, String canonicalAnswer, String persistenceLevel,
                                 boolean checkpointAvailable, boolean restartResumable) {

    public AgentRunProjection {
        if (identity == null || state == null) {
            throw new IllegalArgumentException("run projection requires identity and state");
        }
    }

    public static AgentRunProjection fromRuntime(AgentRuntimeResult result, AgentRunIdentity identity) {
        if (result.stopReason() == AgentStopReason.PAUSED) {
            return new AgentRunProjection(identity,
                    AgentTaskState.active(AgentTaskStatus.PAUSED, AgentTaskPhase.PAUSED),
                    null, "L0_REQUEST_BOUND", false, false);
        }
        if (result.stopReason() == AgentStopReason.WAITING_FOR_USER) {
            return new AgentRunProjection(identity,
                    AgentTaskState.active(AgentTaskStatus.WAITING_INPUT, AgentTaskPhase.WAITING_INPUT),
                    null, "L0_REQUEST_BOUND", false, false);
        }
        boolean controlledStop = result.runtimeStopSignal() != AgentRuntimeStopSignal.NONE
                || result.stopReason() == AgentStopReason.PLAN_PARTIAL
                || result.degraded();
        AgentTaskState state = AgentRunStateMappings.fromRuntimeTerminal(
                result.success() && !controlledStop,
                controlledStop && hasCanonicalAnswer(result));
        AgentTaskOutcome outcome = state.outcome();
        boolean publishable = outcome == AgentTaskOutcome.SUCCEEDED || outcome == AgentTaskOutcome.PARTIAL;
        return new AgentRunProjection(identity, state,
                publishable && hasCanonicalAnswer(result) ? result.assistantContent() : null,
                "L0_REQUEST_BOUND", false, false);
    }

    private static boolean hasCanonicalAnswer(AgentRuntimeResult result) {
        return result.assistantContent() != null && !result.assistantContent().isBlank();
    }
}
