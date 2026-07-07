package com.yanban.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.core.model.ChatChunk;
import com.yanban.core.model.ChatMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LangChain4jToolCallingStrategy {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jToolCallingStrategy.class);
    private static final String TOOL_ROUTING_SYSTEM_PROMPT = """
            You may decide whether to answer directly or call tools.
            Prefer tool use over guessing when evidence is needed.
            Use search_knowledge for the user's uploaded, project, or private knowledge-base documents.
            Use search_web for current, latest, recent, public-web, or externally verifiable facts.
            Use literature_search_start to create a new literature-search background task, then use literature_search_status and literature_search_result to follow it.
            Use paper_polish_status, paper_polish_result, and paper_task_cancel only for existing paper-task progress, results, or cancellation.
            If no tool is needed, answer directly and concisely.
            """;
    private static final String TOOL_BUDGET_FINAL_ANSWER_PROMPT = """
            The tool-call budget has been reached. Do not call any more tools.
            Use only the conversation and tool results already available to produce the best final answer.
            If evidence is incomplete, say that briefly and explain what can be concluded from the retrieved results.
            """;
    private static final String TOOL_BUDGET_SKIPPED_TOOL_RESULT = """
            Tool call skipped because the tool-call budget has been reached.
            Please answer from the tool results that are already present in the conversation.
            """;

    private final LangChain4jChatModelAdapter chatModel;
    private final LangChain4jToolProvider toolProvider;
    private final ObjectMapper objectMapper;

    public LangChain4jToolCallingStrategy(LangChain4jChatModelAdapter chatModel,
                                          LangChain4jToolProvider toolProvider,
                                          ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.toolProvider = toolProvider;
        this.objectMapper = objectMapper;
    }

    public boolean supports(AgentRuntimeRequest request) {
        return request != null && request.toolCallingMode() == AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING;
    }

    public AgentRuntimeResult run(AgentRuntimeRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>(toLangChainMessages(request.history()));
        messages.add(SystemMessage.from(TOOL_ROUTING_SYSTEM_PROMPT));
        if (StringUtils.hasText(request.skillPrompt())) {
            messages.add(SystemMessage.from(request.skillPrompt()));
        }
        messages.add(UserMessage.from(request.userMessage()));

        List<String> toolTrace = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        Map<String, Integer> toolCallCounts = new LinkedHashMap<>();
        Set<String> allowedTools = request.allowedToolNames() == null ? null : new LinkedHashSet<>(request.allowedToolNames());
        ToolProviderResult toolProviderResult = toolProvider.provideTools(request, allowedTools);
        List<ToolSpecification> toolSpecifications = new ArrayList<>(toolProviderResult.tools().keySet());
        TokenUsage totalUsage = null;
        int toolCalls = 0;
        log.info("LangChain4j tool run start sessionId={} userId={} model={} allowedTools={} maxSteps={} maxToolCalls={} maxDuplicateToolCalls={}",
                request.sessionId(),
                request.userId(),
                request.model(),
                allowedTools == null ? List.of() : List.copyOf(allowedTools),
                request.maxSteps(),
                request.maxToolCalls(),
                request.maxDuplicateToolCalls());
        emitProcess(request, "正在分析问题，并判断是否需要调用工具。");

        for (int step = 0; step < request.maxSteps(); step++) {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .parameters(ChatRequestParameters.builder()
                            .modelName(request.model())
                            .temperature(request.temperature())
                            .maxOutputTokens(request.maxTokens())
                            .toolSpecifications(toolSpecifications)
                            .build())
                    .build();
            dev.langchain4j.model.chat.response.ChatResponse response = callModel(chatRequest, request);
            totalUsage = TokenUsage.sum(totalUsage, response == null ? null : response.tokenUsage());
            AiMessage aiMessage = response == null ? null : response.aiMessage();
            if (aiMessage == null) {
                fallbacks.add("langchain4j_empty_response");
                return failure(messages, toolTrace, fallbacks, step + 1, totalUsage, "LangChain4j returned an empty response");
            }
            messages.add(aiMessage);
            log.info("LangChain4j step={} assistantPreview={} toolRequests={}",
                    step + 1,
                    abbreviate(aiMessage.text()),
                    summarizeToolRequests(aiMessage.toolExecutionRequests()));

            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
            if (requests == null || requests.isEmpty()) {
                String content = aiMessage.text();
                log.info("LangChain4j completed without tool call step={} assistantPreview={}",
                        step + 1,
                        abbreviate(content));
                emitProcess(request, toolCalls > 0 ? "工具结果已整理完成，正在生成最终回答。" : "已判断无需调用工具，正在生成最终回答。");
                if (!isStreaming(request)) {
                    emitToken(request, content);
                }
                return success(messages, toolTrace, fallbacks, step + 1, totalUsage, content);
            }

            emitProcess(request, "已决定调用 " + requests.size() + " 个工具：" + summarizeToolNames(requests));
            for (int i = 0; i < requests.size(); i++) {
                ToolExecutionRequest toolRequest = requests.get(i);
                if (request.maxToolCalls() != null && toolCalls >= request.maxToolCalls()) {
                    String error = "Tool-call budget exceeded: maxToolCalls=" + request.maxToolCalls();
                    fallbacks.add(error);
                    addSkippedToolResults(messages, toolTrace, requests, i, step + 1, error);
                    return finalAnswerWithoutMoreTools(messages, toolTrace, fallbacks, step + 1, totalUsage, request, error);
                }
                String signature = toolRequest.name() + "|" + normalizeArguments(toolRequest.arguments());
                int duplicateCount = toolCallCounts.getOrDefault(signature, 0);
                if (request.maxDuplicateToolCalls() != null && duplicateCount >= request.maxDuplicateToolCalls()) {
                    String error = "Duplicate tool call blocked: " + signature;
                    fallbacks.add(error);
                    return failure(messages, toolTrace, fallbacks, step + 1, totalUsage, error);
                }
                toolCallCounts.put(signature, duplicateCount + 1);
                toolCalls++;

                emitProcess(request, "正在调用工具：" + toolRequest.name());
                ToolExecutionOutcome toolResult = executeTool(toolProviderResult, toolRequest, request.userId());
                log.info("LangChain4j tool step={} tool={} args={} success={} error={}",
                        step + 1,
                        toolRequest.name(),
                        abbreviate(defaultString(toolRequest.arguments(), "{}")),
                        toolResult.success(),
                        toolResult.success() ? null : abbreviate(defaultString(toolResult.errorMessage(), "tool_failed")));
                toolTrace.add(buildToolTraceLine(step + 1, toolRequest, toolResult));
                emitProcess(request, toolResult.success()
                        ? "工具调用完成：" + toolRequest.name()
                        : "工具调用失败：" + toolRequest.name() + "，" + defaultString(toolResult.errorMessage(), "tool_failed"));
                messages.add(ToolExecutionResultMessage.from(
                        toolRequest.id(),
                        toolRequest.name(),
                        toolResult.content()
                ));
            }
        }

        String error = "LangChain4j tool-calling exceeded maxSteps=" + request.maxSteps();
        fallbacks.add(error);
        return failure(messages, toolTrace, fallbacks, request.maxSteps(), totalUsage, error);
    }

    private AgentRuntimeResult finalAnswerWithoutMoreTools(List<dev.langchain4j.data.message.ChatMessage> messages,
                                                           List<String> toolTrace,
                                                           List<String> fallbacks,
                                                           int steps,
                                                           TokenUsage usage,
                                                           AgentRuntimeRequest request,
                                                           String reason) {
        emitProcess(request, "Tool-call budget reached. Generating the final answer from existing results.");
        messages.add(SystemMessage.from(TOOL_BUDGET_FINAL_ANSWER_PROMPT));
        ChatRequest finalRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(request.model())
                        .temperature(request.temperature())
                        .maxOutputTokens(request.maxTokens())
                        .build())
                .build();
        dev.langchain4j.model.chat.response.ChatResponse response = callModel(finalRequest, request);
        TokenUsage totalUsage = TokenUsage.sum(usage, response == null ? null : response.tokenUsage());
        AiMessage aiMessage = response == null ? null : response.aiMessage();
        if (aiMessage == null || !StringUtils.hasText(aiMessage.text())) {
            return failure(messages, toolTrace, fallbacks, steps, totalUsage,
                    "LangChain4j returned an empty final response after " + reason);
        }
        messages.add(aiMessage);
        if (!isStreaming(request)) {
            emitToken(request, aiMessage.text());
        }
        return success(messages, toolTrace, fallbacks, steps, totalUsage, aiMessage.text());
    }

    private void addSkippedToolResults(List<dev.langchain4j.data.message.ChatMessage> messages,
                                       List<String> toolTrace,
                                       List<ToolExecutionRequest> requests,
                                       int fromIndex,
                                       int step,
                                       String reason) {
        for (int i = fromIndex; i < requests.size(); i++) {
            ToolExecutionRequest request = requests.get(i);
            messages.add(ToolExecutionResultMessage.from(
                    request.id(),
                    request.name(),
                    TOOL_BUDGET_SKIPPED_TOOL_RESULT
            ));
            toolTrace.add("step=" + step
                    + " tool=" + request.name()
                    + " args=" + defaultString(request.arguments(), "{}")
                    + " success=false error=" + reason);
        }
    }

    private dev.langchain4j.model.chat.response.ChatResponse callModel(ChatRequest chatRequest, AgentRuntimeRequest request) {
        if (!isStreaming(request)) {
            return chatModel.chat(chatRequest, request);
        }
        return streamModel(chatRequest, request);
    }

    private boolean isStreaming(AgentRuntimeRequest request) {
        return request != null && request.tokenConsumer() != null;
    }

    private dev.langchain4j.model.chat.response.ChatResponse streamModel(ChatRequest chatRequest, AgentRuntimeRequest request) {
        StreamAccumulator accumulator = new StreamAccumulator(request);
        chatModel.stream(chatRequest, request)
                .toIterable()
                .forEach(accumulator::accept);
        AiMessage aiMessage = accumulator.toAiMessage();
        return dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(aiMessage)
                .modelName(chatRequest == null ? null : chatRequest.modelName())
                .build();
    }

    private ToolExecutionOutcome executeTool(ToolProviderResult providerResult, ToolExecutionRequest toolRequest, Long userId) {
        try {
            dev.langchain4j.service.tool.ToolExecutor executor = providerResult.toolExecutorByName(toolRequest.name());
            if (executor == null) {
                return new ToolExecutionOutcome(false, toolProvider.failureContent("tool_not_found: " + toolRequest.name()), "tool_not_found");
            }
            return new ToolExecutionOutcome(true, executor.execute(toolRequest, userId), null);
        } catch (Exception ex) {
            String error = defaultString(ex.getMessage(), ex.getClass().getSimpleName());
            return new ToolExecutionOutcome(false, toolProvider.failureContent(error), error);
        }
    }

    private String buildToolTraceLine(int step, ToolExecutionRequest toolRequest, ToolExecutionOutcome toolResult) {
        return "step=" + step
                + " tool=" + toolRequest.name()
                + " args=" + defaultString(toolRequest.arguments(), "{}")
                + " success=" + toolResult.success()
                + (toolResult.success() ? "" : " error=" + defaultString(toolResult.errorMessage(), "tool_failed"));
    }

    private List<dev.langchain4j.data.message.ChatMessage> toLangChainMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<dev.langchain4j.data.message.ChatMessage> converted = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.role() == null ? "" : message.role().trim().toLowerCase(Locale.ROOT);
            switch (role) {
                case "system" -> converted.add(SystemMessage.from(defaultString(message.content())));
                case "assistant" -> {
                    if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                        converted.add(AiMessage.from(defaultString(message.content())));
                    } else {
                        List<ToolExecutionRequest> requests = message.toolCalls().stream()
                                .filter(call -> call.function() != null)
                                .map(call -> ToolExecutionRequest.builder()
                                        .id(call.id())
                                        .name(call.function().name())
                                        .arguments(defaultString(call.function().arguments(), "{}"))
                                        .build())
                                .toList();
                        converted.add(AiMessage.from(defaultString(message.content()), requests));
                    }
                }
                case "tool" -> converted.add(ToolExecutionResultMessage.from(
                        defaultString(message.toolCallId(), "tool-call"),
                        "tool",
                        defaultString(message.content())
                ));
                default -> converted.add(UserMessage.from(defaultString(message.content())));
            }
        }
        return converted;
    }

    private AgentRuntimeResult success(List<dev.langchain4j.data.message.ChatMessage> messages,
                                       List<String> toolTrace,
                                       List<String> fallbacks,
                                       int steps,
                                       TokenUsage usage,
                                       String assistantContent) {
        return new AgentRuntimeResult(
                true,
                assistantContent,
                toCoreMessages(messages),
                steps,
                null,
                toolTrace,
                fallbacks,
                usage == null ? null : usage.inputTokenCount(),
                usage == null ? null : usage.outputTokenCount(),
                usage == null ? null : usage.totalTokenCount()
        );
    }

    private AgentRuntimeResult failure(List<dev.langchain4j.data.message.ChatMessage> messages,
                                       List<String> toolTrace,
                                       List<String> fallbacks,
                                       int steps,
                                       TokenUsage usage,
                                       String errorMessage) {
        return new AgentRuntimeResult(
                false,
                null,
                toCoreMessages(messages),
                steps,
                errorMessage,
                toolTrace,
                fallbacks,
                usage == null ? null : usage.inputTokenCount(),
                usage == null ? null : usage.outputTokenCount(),
                usage == null ? null : usage.totalTokenCount()
        );
    }

    private List<ChatMessage> toCoreMessages(List<dev.langchain4j.data.message.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> converted = new ArrayList<>();
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                converted.add(ChatMessage.user(userMessage.singleText()));
            } else if (message instanceof SystemMessage systemMessage) {
                converted.add(ChatMessage.system(systemMessage.text()));
            } else if (message instanceof ToolExecutionResultMessage toolMessage) {
                converted.add(ChatMessage.tool(toolMessage.id(), toolMessage.text()));
            } else if (message instanceof AiMessage aiMessage) {
                List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
                converted.add(new ChatMessage(
                        "assistant",
                        aiMessage.text(),
                        requests == null || requests.isEmpty() ? null : requests.stream()
                                .map(request -> new com.yanban.core.model.ToolCall(
                                        request.id(),
                                        "function",
                                        new com.yanban.core.model.ToolCall.FunctionCall(request.name(), request.arguments())
                                ))
                                .toList(),
                        null
                ));
            }
        }
        return converted;
    }

    private void emitToken(AgentRuntimeRequest request, String assistantContent) {
        if (request.tokenConsumer() != null && StringUtils.hasText(assistantContent)) {
            request.tokenConsumer().accept(assistantContent);
        }
    }

    private void emitProcess(AgentRuntimeRequest request, String content) {
        if (request.processConsumer() != null && StringUtils.hasText(content)) {
            request.processConsumer().accept(content);
        }
    }

    private String normalizeArguments(String value) {
        return defaultString(value, "{}").replaceAll("\\s+", "");
    }

    private List<String> summarizeToolRequests(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(request -> request.name() + "(" + abbreviate(defaultString(request.arguments(), "{}")) + ")")
                .toList();
    }

    private String summarizeToolNames(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return "无";
        }
        return String.join("、", requests.stream()
                .map(ToolExecutionRequest::name)
                .filter(StringUtils::hasText)
                .toList());
    }

    private String abbreviate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    private String defaultString(String value) {
        return defaultString(value, "");
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static final class StreamAccumulator {

        private final AgentRuntimeRequest request;
        private final StringBuilder content = new StringBuilder();
        private final Map<Integer, ToolCallBuilder> toolCalls = new TreeMap<>();
        private StreamMode mode = StreamMode.UNDECIDED;

        private StreamAccumulator(AgentRuntimeRequest request) {
            this.request = request;
        }

        private void accept(ChatChunk chunk) {
            if (chunk == null) {
                return;
            }
            if (!chunk.toolCallDeltas().isEmpty()) {
                mode = StreamMode.TOOL_CALL;
                for (ChatChunk.ToolCallDelta delta : chunk.toolCallDeltas()) {
                    toolCalls.computeIfAbsent(delta.index(), ignored -> new ToolCallBuilder())
                            .append(delta);
                }
                return;
            }
            if (StringUtils.hasText(chunk.content()) && mode != StreamMode.TOOL_CALL) {
                mode = StreamMode.TEXT;
                content.append(chunk.content());
                if (request.tokenConsumer() != null) {
                    request.tokenConsumer().accept(chunk.content());
                }
            }
        }

        private AiMessage toAiMessage() {
            List<ToolExecutionRequest> requests = toolCalls.values().stream()
                    .map(ToolCallBuilder::build)
                    .filter(toolRequest -> StringUtils.hasText(toolRequest.name()))
                    .toList();
            if (!requests.isEmpty()) {
                return AiMessage.from(content.toString(), requests);
            }
            return AiMessage.from(content.toString());
        }
    }

    private static final class ToolCallBuilder {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private void append(ChatChunk.ToolCallDelta delta) {
            if (delta == null) {
                return;
            }
            if (StringUtils.hasText(delta.id())) {
                id = delta.id();
            }
            if (StringUtils.hasText(delta.functionName())) {
                name = delta.functionName();
            }
            if (delta.argumentsDelta() != null) {
                arguments.append(delta.argumentsDelta());
            }
        }

        private ToolExecutionRequest build() {
            return ToolExecutionRequest.builder()
                    .id(StringUtils.hasText(id) ? id : "tool-call-" + Math.abs(arguments.toString().hashCode()))
                    .name(name)
                    .arguments(arguments.isEmpty() ? "{}" : arguments.toString())
                    .build();
        }
    }

    private enum StreamMode {
        UNDECIDED,
        TEXT,
        TOOL_CALL
    }

    private record ToolExecutionOutcome(boolean success, String content, String errorMessage) {
    }
}
