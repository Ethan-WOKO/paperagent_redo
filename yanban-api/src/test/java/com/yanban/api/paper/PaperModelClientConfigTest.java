package com.yanban.api.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yanban.api.agent.LangChain4jChatModelAdapter;
import com.yanban.paper.service.PaperModelClient;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaperModelClientConfigTest {

    @Test
    void paperModelClientUsesLangChain4jChatRequest() {
        LangChain4jChatModelAdapter chatModel = mock(LangChain4jChatModelAdapter.class);
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("polished"))
                .build());
        PaperModelClient client = new PaperModelClientConfig().paperModelClient(chatModel);

        String result = client.complete("system prompt", "user prompt", 0.2, 1024);

        assertThat(result).isEqualTo("polished");
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertThat(request.messages()).hasSize(2);
        assertThat(((SystemMessage) request.messages().get(0)).text()).isEqualTo("system prompt");
        assertThat(((UserMessage) request.messages().get(1)).singleText()).isEqualTo("user prompt");
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.maxOutputTokens()).isEqualTo(1024);
    }
}
