package com.yanban.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentPlanStepTest {

    @Test
    void completedExecutionFactCannotBeSupersededOrSkippedByReplan() {
        AgentPlanStep step = new AgentPlanStep(
                1L, "completed", 1, "Completed", "Read trusted evidence", "ANALYSIS",
                "[]", "[]", "Evidence is available");
        step.markCompleted("immutable evidence");

        assertThatThrownBy(() -> step.markSuperseded("new plan"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed step");
        assertThatThrownBy(() -> step.markSkipped("new plan"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed step");
        assertThat(step.getStatus()).isEqualTo(AgentPlanStepStatus.COMPLETED.name());
        assertThat(step.getResult()).isEqualTo("immutable evidence");
    }
}
