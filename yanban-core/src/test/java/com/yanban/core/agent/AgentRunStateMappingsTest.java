package com.yanban.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentRunStateMappingsTest {

    @Test
    void mapsTurnAndPlanStatesWithoutStringGuessing() {
        assertThat(AgentRunStateMappings.fromTurn(AgentTurn.STATUS_COMPLETED, true).outcome())
                .isEqualTo(AgentTaskOutcome.PARTIAL);
        assertThat(AgentRunStateMappings.fromPlan(AgentPlanStatus.REVIEWING, false, false).phase())
                .isEqualTo(AgentTaskPhase.PLANNING);
        assertThat(AgentRunStateMappings.fromPlan(AgentPlanStatus.PAUSED, false, false).status())
                .isEqualTo(AgentTaskStatus.PAUSED);
        assertThat(AgentRunStateMappings.fromPlan(AgentPlanStatus.COMPLETED, false, true).outcome())
                .isEqualTo(AgentTaskOutcome.PARTIAL);
        assertThat(AgentRunStateMappings.fromPlan(AgentPlanStatus.COMPLETED, false, false).outcome())
                .isEqualTo(AgentTaskOutcome.FAILED);
        assertThat(AgentRunStateMappings.fromRuntimeTerminal(false, true).outcome())
                .isEqualTo(AgentTaskOutcome.PARTIAL);
    }

    @Test
    void rejectsUnknownStatesInsteadOfUsingSimilarity() {
        assertThatThrownBy(() -> AgentRunStateMappings.fromTurn("complete", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentRunStateMappings.fromPersistedTask("RUNNING", "execute", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runIdentityIsStableAcrossProjectionReads() {
        AgentRunIdentity identity = new AgentRunIdentity("AGENT_TURN", "12", 7L, 3L, 42L);
        assertThat(identity.runId()).isEqualTo("AGENT_TURN:12");
    }

    @Test
    void runIdentityRejectsNullOrBlankSourceIdAndTrimsValidValues() {
        assertThatThrownBy(() -> new AgentRunIdentity("RUNTIME_TRACE", null, 7L, 3L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentRunIdentity("RUNTIME_TRACE", "   ", 7L, 3L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AgentRunIdentity(" RUNTIME_TRACE ", " trace-1 ", 7L, 3L, null).runId())
                .isEqualTo("RUNTIME_TRACE:trace-1");
    }

    @Test
    void turnRejectsASecondTerminalTransitionAndCanonicalAnswer() {
        AgentTurn turn = new AgentTurn(3L, 7L, 10L);
        turn.complete(11L);
        assertThat(turn.getAssistantMessageId()).isEqualTo(11L);
        assertThatThrownBy(() -> turn.complete(12L)).isInstanceOf(IllegalStateException.class);
    }
}
