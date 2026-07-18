package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlanReflectionRuntimeAdapterTest {

    @Test
    void reflectionSummaryExposesDegradedAndFailedLimitations() {
        PlanAgentService planAgentService = mock(PlanAgentService.class);
        PlanReflectionRuntimeAdapter adapter = new PlanReflectionRuntimeAdapter(planAgentService, new AgentStrategySelector());
        AgentPlanResponse response = new AgentPlanResponse(
                81L,
                21L,
                "Assess roadmap risks",
                "Assess roadmap risks",
                "FAILED",
                true,
                null,
                "plan stalled on missing evidence",
                null,
                null,
                null,
                null,
                List.of(
                        new AgentPlanStepResponse(1L, "step_1", 1, "Collect evidence", "Collect evidence", "ANALYSIS",
                                List.of(), List.of(), "Evidence is collected", "DEGRADED", 2,
                                "partial evidence only", "missing primary source", null, null),
                        new AgentPlanStepResponse(2L, "step_2", 2, "Write summary", "Write summary", "SYNTHESIS",
                                List.of("step_1"), List.of(), "Summary is complete", "FAILED", 2,
                                null, "dependency evidence still incomplete", null, null),
                        new AgentPlanStepResponse(3L, "step_3", 3, "Bounded synthesis", "Retain usable findings",
                                "SYNTHESIS", List.of("step_1", "step_2"), List.of(), "Limitations are explicit",
                                "DEGRADED", 0,
                                "Governed completion status: PARTIAL\n\n"
                                        + "bounded final synthesis\n[projectEvidenceRefs=internal]",
                                "DEPENDENCY_PARTIAL: step_2", null, null)
                )
        );
        when(planAgentService.createAndExecuteRuntimeReflectionPlan(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("Assess roadmap risks")))
                .thenReturn(new PlanAgentService.PlanExecutionResult(
                        response, AgentRuntimeStopSignal.NONE, EvidenceLedger.empty(), DomainRuntimeFacts.empty()));

        ProjectRuntimeContext projectContext = new ProjectRuntimeContext(11L, 42L);
        AgentRuntimeResult result = adapter.run(new AgentRuntimeRequest(
                AgentStrategy.PLAN_EXECUTE_WITH_REFLECTION,
                21L,
                List.of(),
                11L,
                "/plan reflect Assess roadmap risks",
                "deepseek",
                "deepseek-chat",
                null,
                null,
                8,
                true,
                null,
                null,
                null,
                null,
                AgentRuntimeMode.LANGCHAIN4J,
                AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING,
                List.of(),
                0,
                1,
                "trace-reflect",
                null,
                null
        ).withProjectContext(projectContext));

        assertThat(result.success()).isFalse();
        assertThat(result.outcome()).isEqualTo("PARTIAL");
        assertThat(result.stopReason()).isEqualTo(AgentStopReason.PLAN_PARTIAL);
        assertThat(result.assistantContent()).contains("Plan status: FAILED");
        assertThat(result.assistantContent()).contains("step_1 [DEGRADED]");
        assertThat(result.assistantContent()).contains("step_2 [FAILED]");
        assertThat(result.assistantContent()).contains("plan error: plan stalled on missing evidence");
        assertThat(result.assistantContent()).contains("Follow-up suggestions:", "Final Plan synthesis:",
                        "bounded final synthesis")
                .doesNotContain("projectEvidenceRefs=", "Governed completion status: PARTIAL");
        assertThat(result.planId()).isEqualTo(81L);
        ArgumentCaptor<AgentRuntimeRequest> request = ArgumentCaptor.forClass(AgentRuntimeRequest.class);
        verify(planAgentService).createAndExecuteRuntimeReflectionPlan(request.capture(),
                org.mockito.ArgumentMatchers.eq("Assess roadmap risks"));
        assertThat(request.getValue().projectContext()).isEqualTo(projectContext);
    }
}
