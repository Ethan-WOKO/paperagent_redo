package com.yanban.api.paper;

import com.yanban.api.agent.AgentRuntimeMode;
import com.yanban.api.agent.AgentRuntimeRequest;
import com.yanban.api.agent.AgentStrategy;
import com.yanban.api.agent.AgentToolCallingMode;
import com.yanban.api.agent.LangChain4jChatModelAdapter;
import com.yanban.paper.service.PaperModelClient;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(PaperModelProperties.class)
public class PaperModelClientConfig {

    @Bean
    public PaperModelClient paperModelClient(LangChain4jChatModelAdapter chatModel, PaperModelProperties properties) {
        return (systemPrompt, userPrompt, temperature, maxTokens) -> {
            ChatRequest.Builder builder = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(defaultString(systemPrompt)),
                            UserMessage.from(defaultString(userPrompt))
                    ))
                    .parameters(ChatRequestParameters.builder()
                            .temperature(temperature)
                            .maxOutputTokens(maxTokens)
                            .build())
                    .modelName(defaultString(properties.getModel()));
            ChatRequest request = builder.build();
            ChatResponse response = StringUtils.hasText(properties.getProvider())
                    ? chatModel.chat(request, runtimeRequest(properties, temperature, maxTokens))
                    : chatModel.chat(request);
            return response == null || response.aiMessage() == null ? "" : defaultString(response.aiMessage().text());
        };
    }

    private AgentRuntimeRequest runtimeRequest(PaperModelProperties properties, Double temperature, Integer maxTokens) {
        return new AgentRuntimeRequest(
                AgentStrategy.DIRECT,
                null,
                List.of(),
                null,
                "paper-model-call",
                properties.getProvider(),
                properties.getModel(),
                temperature,
                maxTokens,
                1,
                true,
                null,
                properties.getApiKey(),
                properties.getApiUrl(),
                null,
                AgentRuntimeMode.LANGCHAIN4J,
                AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static String defaultString(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
