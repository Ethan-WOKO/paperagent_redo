package com.yanban.api.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.tool.ToolCall;
import com.yanban.core.tool.ToolDefinition;
import com.yanban.core.tool.ToolExecutionContext;
import com.yanban.core.tool.ToolRegistry;
import com.yanban.core.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LangChain4jToolProvider implements ToolProvider {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final AgentLangChain4jTools annotatedTools;
    private final Map<String, ToolBinding> annotatedBindings;

    public LangChain4jToolProvider(ToolRegistry toolRegistry,
                                   ObjectMapper objectMapper,
                                   AgentLangChain4jTools annotatedTools) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.annotatedTools = annotatedTools;
        this.annotatedBindings = buildAnnotatedBindings(annotatedTools);
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return provideTools(null, null);
    }

    public ToolProviderResult provideTools(AgentRuntimeRequest runtimeRequest, Set<String> allowedToolNames) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        Set<String> added = new LinkedHashSet<>();
        for (ToolBinding binding : annotatedBindings.values()) {
            if (allowedToolNames == null || allowedToolNames.contains(binding.specification().name())) {
                builder.add(binding.specification(), binding.executor());
                added.add(binding.specification().name());
            }
        }
        for (ToolDefinition definition : toolRegistry.listDefinitions()) {
            String name = definition.name();
            if (added.contains(name) || (allowedToolNames != null && !allowedToolNames.contains(name))) {
                continue;
            }
            builder.add(toToolSpecification(definition), fallbackExecutor(runtimeRequest, name));
        }
        return builder.build();
    }

    private Map<String, ToolBinding> buildAnnotatedBindings(AgentLangChain4jTools tools) {
        Map<String, ToolBinding> bindings = new LinkedHashMap<>();
        if (tools == null) {
            return bindings;
        }
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(tools);
        for (ToolSpecification specification : specifications) {
            Method method = findToolMethod(tools.getClass(), specification.name());
            if (method != null) {
                bindings.put(specification.name(), new ToolBinding(
                        specification,
                        new DefaultToolExecutor(tools, method)
                ));
            }
        }
        return bindings;
    }

    private Method findToolMethod(Class<?> type, String toolName) {
        for (Method method : type.getMethods()) {
            dev.langchain4j.agent.tool.Tool tool = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            if (tool == null) {
                continue;
            }
            if (toolName.equals(tool.name()) || List.of(tool.value()).contains(toolName) || method.getName().equals(toolName)) {
                return method;
            }
        }
        return null;
    }

    private dev.langchain4j.service.tool.ToolExecutor fallbackExecutor(AgentRuntimeRequest runtimeRequest, String toolName) {
        return (toolRequest, memoryId) -> {
            try {
                JsonNode arguments = objectMapper.readTree(defaultString(toolRequest.arguments(), "{}"));
                Long userId = runtimeRequest == null ? null : runtimeRequest.userId();
                ToolExecutionContext.setCurrentUserId(userId);
                ToolResult result = toolRegistry.execute(new ToolCall(toolRequest.id(), toolName, arguments));
                return serialize(result);
            } catch (Exception ex) {
                return failureContent(defaultString(ex.getMessage(), ex.getClass().getSimpleName()));
            } finally {
                ToolExecutionContext.clear();
            }
        };
    }

    public String failureContent(String errorMessage) {
        try {
            return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("success", false)
                    .put("error", defaultString(errorMessage, "tool_failed")));
        } catch (Exception ex) {
            return "{\"success\":false,\"error\":\"tool_result_serialization_failed\"}";
        }
    }

    private String serialize(ToolResult result) {
        try {
            if (result != null && result.success()) {
                return objectMapper.writeValueAsString(result.output());
            }
            return failureContent(result == null ? "tool_failed" : result.errorMessage());
        } catch (Exception ex) {
            return failureContent("tool_result_serialization_failed");
        }
    }

    private ToolSpecification toToolSpecification(ToolDefinition definition) {
        JsonNode parameters = definition.parameters();
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        JsonNode properties = parameters.path("properties");
        if (properties.isObject()) {
            properties.fields().forEachRemaining(entry -> builder.addProperty(entry.getKey(), toSchemaElement(entry.getValue())));
        }
        JsonNode required = parameters.path("required");
        if (required.isArray()) {
            List<String> requiredFields = new java.util.ArrayList<>();
            required.forEach(node -> requiredFields.add(node.asText()));
            builder.required(requiredFields);
        }
        if (parameters.has("additionalProperties")) {
            builder.additionalProperties(parameters.path("additionalProperties").asBoolean());
        }
        return ToolSpecification.builder()
                .name(definition.name())
                .description(definition.description())
                .parameters(builder.build())
                .build();
    }

    private JsonSchemaElement toSchemaElement(JsonNode schemaNode) {
        String type = schemaNode.path("type").asText("string").trim().toLowerCase(Locale.ROOT);
        String description = schemaNode.path("description").asText(null);
        return switch (type) {
            case "integer" -> JsonIntegerSchema.builder().description(description).build();
            case "boolean" -> JsonBooleanSchema.builder().description(description).build();
            case "number" -> JsonNumberSchema.builder().description(description).build();
            case "object" -> buildObjectSchema(schemaNode, description);
            default -> JsonStringSchema.builder().description(description).build();
        };
    }

    private JsonObjectSchema buildObjectSchema(JsonNode schemaNode, String description) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (StringUtils.hasText(description)) {
            builder.description(description);
        }
        JsonNode properties = schemaNode.path("properties");
        if (properties.isObject()) {
            properties.fields().forEachRemaining(entry -> builder.addProperty(entry.getKey(), toSchemaElement(entry.getValue())));
        }
        JsonNode required = schemaNode.path("required");
        if (required.isArray()) {
            List<String> requiredFields = new java.util.ArrayList<>();
            required.forEach(node -> requiredFields.add(node.asText()));
            builder.required(requiredFields);
        }
        return builder.build();
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private record ToolBinding(ToolSpecification specification, dev.langchain4j.service.tool.ToolExecutor executor) {
    }
}
