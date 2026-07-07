package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.tool.ToolCall;
import com.yanban.core.tool.ToolDefinition;
import com.yanban.core.tool.ToolExecutor;
import com.yanban.core.tool.ToolRegistry;
import com.yanban.core.tool.ToolResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentToolPolicyEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesGeneralAgentToolsWithoutKeywordFiltering() {
        AgentToolPolicyEngine engine = new AgentToolPolicyEngine(registry());

        AgentToolPolicyEngine.Decision decision = engine.decide("西瓜的功效", false, null);

        assertThat(decision.allowedTools()).containsExactly(
                "search_web",
                "search_literature",
                "search_knowledge",
                "literature_search_start",
                "literature_search_status",
                "literature_search_result",
                "literature_search_cancel",
                "paper_polish_status",
                "paper_polish_result",
                "paper_task_cancel"
        );
        assertThat(decision.reason()).isEqualTo("llm_native_tool_routing");
        assertThat(decision.maxToolCalls()).isEqualTo(4);
    }

    @Test
    void honorsRagDisabledForKnowledgeTool() {
        AgentToolPolicyEngine engine = new AgentToolPolicyEngine(registry());

        AgentToolPolicyEngine.Decision decision = engine.decide("根据我上传的文档总结要点", true, null);

        assertThat(decision.allowedTools()).doesNotContain("search_knowledge");
        assertThat(decision.reason()).isEqualTo("llm_native_tool_routing_rag_disabled");
    }

    @Test
    void selectedSkillAllowlistStillRestrictsVisibleTools() {
        AgentToolPolicyEngine engine = new AgentToolPolicyEngine(registry());

        AgentToolPolicyEngine.Decision decision = engine.decide("西瓜的功效", false, Set.of("search_web", "missing_tool"));

        assertThat(decision.allowedTools()).containsExactly("search_web");
        assertThat(decision.reason()).isEqualTo("skill_allowlist");
        assertThat(decision.maxToolCalls()).isEqualTo(1);
    }

    private ToolRegistry registry() {
        return new ToolRegistry()
                .register(new StubTool("search_web"))
                .register(new StubTool("search_literature"))
                .register(new StubTool("search_knowledge"))
                .register(new StubTool("literature_search_start"))
                .register(new StubTool("literature_search_status"))
                .register(new StubTool("literature_search_result"))
                .register(new StubTool("literature_search_cancel"))
                .register(new StubTool("paper_polish_status"))
                .register(new StubTool("paper_polish_result"))
                .register(new StubTool("paper_task_cancel"))
                .register(new StubTool("echo"))
                .register(new StubTool("mcp_private"));
    }

    private class StubTool implements ToolExecutor {
        private final ToolDefinition definition;

        private StubTool(String name) {
            this.definition = new ToolDefinition(
                    name,
                    "stub " + name,
                    objectMapper.createObjectNode().put("type", "object")
            );
        }

        @Override
        public ToolDefinition definition() {
            return definition;
        }

        @Override
        public ToolResult execute(ToolCall call) {
            return ToolResult.success(call.id(), call.name(), objectMapper.createObjectNode());
        }
    }
}
