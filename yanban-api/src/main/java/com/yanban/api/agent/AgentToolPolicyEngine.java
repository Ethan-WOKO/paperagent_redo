package com.yanban.api.agent;

import com.yanban.core.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentToolPolicyEngine {

    private static final String SEARCH_KNOWLEDGE = "search_knowledge";
    private static final String ECHO = "echo";
    private static final String MCP_PREFIX = "mcp_";

    private final ToolRegistry toolRegistry;
    private final AgentLangChain4jTools langChain4jTools;

    @Autowired
    public AgentToolPolicyEngine(ToolRegistry toolRegistry,
                                 AgentLangChain4jTools langChain4jTools) {
        this.toolRegistry = toolRegistry;
        this.langChain4jTools = langChain4jTools;
    }

    AgentToolPolicyEngine(ToolRegistry toolRegistry) {
        this(toolRegistry, null);
    }

    public Decision decide(String userMessage, boolean ragDisabled, Set<String> skillAllowedTools) {
        Set<String> registeredTools = registeredToolNames();
        if (skillAllowedTools != null && !skillAllowedTools.isEmpty()) {
            List<String> allowed = skillAllowedTools.stream()
                    .filter(registeredTools::contains)
                    .filter(toolName -> !ragDisabled || !SEARCH_KNOWLEDGE.equals(toolName))
                    .distinct()
                    .toList();
            return new Decision(allowed, allowed.isEmpty() ? 0 : Math.min(3, allowed.size()), 1, "skill_allowlist");
        }
        List<String> allowed = registeredTools.stream()
                .filter(this::isGeneralAgentVisibleTool)
                .filter(toolName -> !ragDisabled || !SEARCH_KNOWLEDGE.equals(toolName))
                .toList();
        return new Decision(allowed, allowed.isEmpty() ? 0 : Math.min(4, allowed.size()), 1,
                ragDisabled ? "llm_native_tool_routing_rag_disabled" : "llm_native_tool_routing");
    }

    private boolean isGeneralAgentVisibleTool(String toolName) {
        return toolName != null
                && !toolName.isBlank()
                && !ECHO.equals(toolName)
                && !toolName.startsWith(MCP_PREFIX);
    }

    private Set<String> registeredToolNames() {
        Set<String> names = new LinkedHashSet<>();
        toolRegistry.listDefinitions().forEach(definition -> names.add(definition.name()));
        if (langChain4jTools != null) {
            ToolSpecifications.toolSpecificationsFrom(langChain4jTools)
                    .forEach(specification -> names.add(specification.name()));
        }
        return names;
    }

    public record Decision(
            List<String> allowedTools,
            int maxToolCalls,
            int maxDuplicateToolCalls,
            String reason
    ) {
    }
}
