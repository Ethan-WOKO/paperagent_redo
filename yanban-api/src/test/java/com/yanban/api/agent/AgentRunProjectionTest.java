package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanban.core.agent.AgentTaskOutcome;
import com.yanban.core.agent.AgentRunIdentity;
import com.yanban.core.agent.AgentTaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunProjectionTest {

    @Test
    void budgetStopWithUsefulAnswerIsPartialAndNotRestartResumable() {
        AgentRuntimeResult result = new AgentRuntimeResult(true, "bounded result", List.of(), 2,
                null, List.of(), List.of(), null, null, null)
                .withRuntimeStopSignal(AgentRuntimeStopSignal.MAX_STEPS_BUDGET_EXHAUSTED)
                .withCoordination(AgentStrategy.SINGLE_STEP_REACT,
                        AgentStopReason.MAX_STEPS_BUDGET_EXHAUSTED, "BUDGET_STOP", false, null);

        AgentRunProjection projection = project(result);

        assertThat(projection.state().outcome()).isEqualTo(AgentTaskOutcome.PARTIAL);
        assertThat(projection.canonicalAnswer()).isEqualTo("bounded result");
        assertThat(projection.persistenceLevel()).isEqualTo("L0_REQUEST_BOUND");
        assertThat(projection.checkpointAvailable()).isFalse();
        assertThat(projection.restartResumable()).isFalse();
    }

    @Test
    void failedIntermediateTextDoesNotBecomeCanonicalAnswer() {
        AgentRuntimeResult result = new AgentRuntimeResult(false, null, List.of(), 1,
                "model failed", List.of("attempt"), List.of(), null, null, null);
        AgentRunProjection projection = project(result);
        assertThat(projection.state().outcome()).isEqualTo(AgentTaskOutcome.FAILED);
        assertThat(projection.canonicalAnswer()).isNull();
    }

    @Test
    void failedRuntimeWithAssistantTextCannotPublishCanonicalAnswer() {
        AgentRuntimeResult result = new AgentRuntimeResult(false, "unverified failure summary", List.of(), 1,
                "failed", List.of(), List.of(), null, null, null)
                .withCoordination(AgentStrategy.DIRECT, AgentStopReason.RUNTIME_FAILED,
                        "FAILURE", false, null);
        AgentRunProjection projection = project(result);
        assertThat(projection.state().outcome()).isEqualTo(AgentTaskOutcome.FAILED);
        assertThat(projection.canonicalAnswer()).isNull();
    }

    @Test
    void failedPlanAdapterSummaryCannotPublishCanonicalAnswer() {
        AgentRuntimeResult result = new AgentRuntimeResult(false, "Plan 9 finished with status FAILED",
                List.of(), 1, "step failed", List.of(), List.of(), null, null, null)
                .withPlanId(9L)
                .withCoordination(AgentStrategy.PLAN_EXECUTE, AgentStopReason.RUNTIME_FAILED,
                        "FAILURE", false, null);
        AgentRunProjection projection = project(result);
        assertThat(projection.state().outcome()).isEqualTo(AgentTaskOutcome.FAILED);
        assertThat(projection.canonicalAnswer()).isNull();
    }

    @Test
    void pausedPlanIsActiveAndCannotPublishCanonicalAnswer() {
        AgentRuntimeResult result = new AgentRuntimeResult(false, "Plan paused", List.of(), 1,
                null, List.of(), List.of(), null, null, null)
                .withCoordination(AgentStrategy.PLAN_EXECUTE, AgentStopReason.PAUSED,
                        "PAUSED", false, null);
        AgentRunProjection projection = project(result);
        assertThat(projection.state().status()).isEqualTo(AgentTaskStatus.PAUSED);
        assertThat(projection.state().outcome()).isNull();
        assertThat(projection.canonicalAnswer()).isNull();
    }

    @Test
    void waitingPlanIsWaitingInputAndCannotPublishCanonicalAnswer() {
        AgentRuntimeResult result = new AgentRuntimeResult(false, "Plan waiting", List.of(), 1,
                null, List.of(), List.of(), null, null, null)
                .withCoordination(AgentStrategy.PLAN_EXECUTE, AgentStopReason.WAITING_FOR_USER,
                        "WAITING", false, null);
        AgentRunProjection projection = project(result);
        assertThat(projection.state().status()).isEqualTo(AgentTaskStatus.WAITING_INPUT);
        assertThat(projection.state().outcome()).isNull();
        assertThat(projection.canonicalAnswer()).isNull();
    }

    private AgentRunProjection project(AgentRuntimeResult result) {
        return AgentRunProjection.fromRuntime(result,
                new AgentRunIdentity("RUNTIME_TRACE", "test-trace", 1L, 1L, null));
    }
}
