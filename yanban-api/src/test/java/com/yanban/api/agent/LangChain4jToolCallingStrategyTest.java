package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yanban.core.model.ChatChunk;
import com.yanban.core.tool.ToolCall;
import com.yanban.core.tool.ToolDefinition;
import com.yanban.core.tool.ToolExecutionContext;
import com.yanban.core.tool.ToolExecutor;
import com.yanban.core.tool.ToolRegistry;
import com.yanban.core.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class LangChain4jToolCallingStrategyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executesAllowedToolAndReturnsFinalAnswer() {
        ToolRegistry registry = new ToolRegistry().register(new StubToolExecutor("search_web", objectMapper));
        LangChain4jChatModelAdapter chatModel = mock(LangChain4jChatModelAdapter.class);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any(AgentRuntimeRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("search_web")
                                .arguments("{\"query\":\"latest radar paper\"}")
                                .build())))
                        .tokenUsage(new TokenUsage(10, 5, 15))
                        .build())
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Here is the answer with sources."))
                        .tokenUsage(new TokenUsage(6, 7, 13))
                        .build());
        LangChain4jToolCallingStrategy strategy = new LangChain4jToolCallingStrategy(
                chatModel,
                toolProvider(registry),
                objectMapper
        );

        AgentRuntimeResult result = strategy.run(request(List.of("search_web"), 2, 1));

        assertThat(result.success()).isTrue();
        assertThat(result.assistantContent()).isEqualTo("Here is the answer with sources.");
        assertThat(result.toolTrace()).hasSize(1);
        assertThat(result.toolTrace().get(0)).contains("tool=search_web");
        assertThat(result.totalTokens()).isEqualTo(28);
    }

    @Test
    void blocksDuplicateToolCallsBeyondBudget() {
        ToolRegistry registry = new ToolRegistry().register(new StubToolExecutor("search_web", objectMapper));
        LangChain4jChatModelAdapter chatModel = mock(LangChain4jChatModelAdapter.class);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any(AgentRuntimeRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("search_web")
                                .arguments("{\"query\":\"latest radar paper\"}")
                                .build())))
                        .build())
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                                .id("call-2")
                                .name("search_web")
                                .arguments("{\"query\":\"latest radar paper\"}")
                                .build())))
                        .build());
        LangChain4jToolCallingStrategy strategy = new LangChain4jToolCallingStrategy(
                chatModel,
                toolProvider(registry),
                objectMapper
        );

        AgentRuntimeResult result = strategy.run(request(List.of("search_web"), 3, 1));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Duplicate tool call blocked");
        assertThat(result.fallbacks()).isNotEmpty();
    }

    @Test
    void summarizesExistingResultsWhenToolCallBudgetIsExhausted() {
        ToolRegistry registry = new ToolRegistry().register(new StubToolExecutor("search_web", objectMapper));
        LangChain4jChatModelAdapter chatModel = mock(LangChain4jChatModelAdapter.class);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class), any(AgentRuntimeRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                                .id("call-1")
                                .name("search_web")
                                .arguments("{\"query\":\"polarization FDA-MIMO\"}")
                                .build())))
                        .build())
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from(List.of(ToolExecutionRequest.builder()
                                .id("call-2")
                                .name("search_web")
                                .arguments("{\"query\":\"polarization diversity radar\"}")
                                .build())))
                        .build())
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("Final answer based on the first search result."))
                        .build());
        LangChain4jToolCallingStrategy strategy = new LangChain4jToolCallingStrategy(
                chatModel,
                toolProvider(registry),
                objectMapper
        );

        AgentRuntimeResult result = strategy.run(request(List.of("search_web"), 1, 2));

        assertThat(result.success()).isTrue();
        assertThat(result.assistantContent()).isEqualTo("Final answer based on the first search result.");
        assertThat(result.fallbacks()).contains("Tool-call budget exceeded: maxToolCalls=1");
        assertThat(result.toolTrace()).hasSize(2);
        assertThat(result.toolTrace().get(1)).contains("success=false error=Tool-call budget exceeded");
    }

    @Test
    void annotatedToolReceivesUserIdAsToolMemoryId() {
        ToolRegistry registry = new ToolRegistry().register(new UserAwareStubToolExecutor("search_knowledge", objectMapper));
        LangChain4jToolProvider provider = toolProvider(registry);

        String content = provider
                .provideTools(request(List.of("search_knowledge"), 2, 1), java.util.Set.of("search_knowledge"))
                .toolExecutorByName("search_knowledge")
                .execute(ToolExecutionRequest.builder()
                        .id("call-user")
                        .name("search_knowledge")
                        .arguments("{\"query\":\"project name\"}")
                        .build(), 8L);

        assertThat(content).contains("\"userId\":8");
    }

    @Test
    void streamsFinalAnswerWhenTokenConsumerIsPresent() {
        ToolRegistry registry = new ToolRegistry();
        LangChain4jChatModelAdapter chatModel = mock(LangChain4jChatModelAdapter.class);
        when(chatModel.stream(any(dev.langchain4j.model.chat.request.ChatRequest.class), any(AgentRuntimeRequest.class)))
                .thenReturn(Flux.just(
                        ChatChunk.token("Hello"),
                        ChatChunk.token(" world"),
                        ChatChunk.done("stop")
                ));
        LangChain4jToolCallingStrategy strategy = new LangChain4jToolCallingStrategy(
                chatModel,
                toolProvider(registry),
                objectMapper
        );
        List<String> tokens = new ArrayList<>();

        AgentRuntimeResult result = strategy.run(request(List.of(), 0, 1, tokens::add));

        assertThat(result.success()).isTrue();
        assertThat(result.assistantContent()).isEqualTo("Hello world");
        assertThat(tokens).containsExactly("Hello", " world");
    }

    private AgentRuntimeRequest request(List<String> allowedTools, Integer maxToolCalls, Integer maxDuplicateToolCalls) {
        return request(allowedTools, maxToolCalls, maxDuplicateToolCalls, null);
    }

    private AgentRuntimeRequest request(List<String> allowedTools,
                                        Integer maxToolCalls,
                                        Integer maxDuplicateToolCalls,
                                        java.util.function.Consumer<String> tokenConsumer) {
        return new AgentRuntimeRequest(
                AgentStrategy.DIRECT,
                4L,
                List.of(),
                8L,
                "help me search",
                "deepseek",
                "deepseek-v4-flash",
                null,
                null,
                3,
                false,
                null,
                null,
                null,
                null,
                AgentRuntimeMode.LANGCHAIN4J,
                AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING,
                allowedTools,
                maxToolCalls,
                maxDuplicateToolCalls,
                "trace-tool",
                tokenConsumer,
                null
        );
    }

    private LangChain4jToolProvider toolProvider(ToolRegistry registry) {
        return new LangChain4jToolProvider(
                registry,
                objectMapper,
                new AgentLangChain4jTools(registry, objectMapper)
        );
    }

    private static final class StubToolExecutor implements ToolExecutor {

        private final ToolDefinition definition;
        private final ObjectMapper objectMapper;

        private StubToolExecutor(String name, ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            ObjectNode parameters = objectMapper.createObjectNode();
            parameters.put("type", "object");
            parameters.putObject("properties")
                    .putObject("query")
                    .put("type", "string");
            parameters.putArray("required").add("query");
            this.definition = new ToolDefinition(name, "stub tool", parameters);
        }

        @Override
        public ToolDefinition definition() {
            return definition;
        }

        @Override
        public ToolResult execute(ToolCall call) {
            ObjectNode output = objectMapper.createObjectNode();
            output.put("query", call.arguments().path("query").asText());
            output.put("source", "stub");
            return ToolResult.success(call.id(), call.name(), output);
        }
    }

    private static final class UserAwareStubToolExecutor implements ToolExecutor {

        private final ToolDefinition definition;
        private final ObjectMapper objectMapper;

        private UserAwareStubToolExecutor(String name, ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            ObjectNode parameters = objectMapper.createObjectNode();
            parameters.put("type", "object");
            parameters.putObject("properties")
                    .putObject("query")
                    .put("type", "string");
            parameters.putArray("required").add("query");
            this.definition = new ToolDefinition(name, "user aware stub tool", parameters);
        }

        @Override
        public ToolDefinition definition() {
            return definition;
        }

        @Override
        public ToolResult execute(ToolCall call) {
            ObjectNode output = objectMapper.createObjectNode();
            output.put("query", call.arguments().path("query").asText());
            output.put("userId", ToolExecutionContext.getCurrentUserId());
            return ToolResult.success(call.id(), call.name(), output);
        }
    }
}
