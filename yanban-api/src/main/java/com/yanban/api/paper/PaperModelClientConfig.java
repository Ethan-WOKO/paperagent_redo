package com.yanban.api.paper;

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
import org.springframework.util.StringUtils;

@Configuration
public class PaperModelClientConfig {

    @Bean
    public PaperModelClient paperModelClient(LangChain4jChatModelAdapter chatModel) {
        return (systemPrompt, userPrompt, temperature, maxTokens) -> {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(defaultString(systemPrompt)),
                            UserMessage.from(defaultString(userPrompt))
                    ))
                    .parameters(ChatRequestParameters.builder()
                            .temperature(temperature)
                            .maxOutputTokens(maxTokens)
                            .build())
                    .build();
            ChatResponse response = chatModel.chat(request);
            return response == null || response.aiMessage() == null ? "" : defaultString(response.aiMessage().text());
        };
    }

    private static String defaultString(String value) {
        return StringUtils.hasText(value) ? value : "";
    }
}
