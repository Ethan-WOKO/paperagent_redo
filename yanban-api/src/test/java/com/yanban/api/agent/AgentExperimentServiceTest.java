package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yanban.knowledge.service.KnowledgeSearchResult;
import com.yanban.knowledge.service.KnowledgeSearchService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentExperimentServiceTest {

    @Test
    void prepareUsesDefaultsWhenExperimentDisabled() {
        AgentExperimentService service = new AgentExperimentService(
                mock(KnowledgeSearchService.class),
                mock(LangChain4jChatModelAdapter.class)
        );

        AgentExperimentContext context = service.prepare(7L, "hello", null);

        assertThat(context.enabled()).isFalse();
        assertThat(context.selectedModes().runtimeMode()).isEqualTo(AgentRuntimeMode.LANGCHAIN4J);
        assertThat(context.selectedModes().ragMode()).isEqualTo(AgentRagMode.LANGCHAIN4J_AUGMENTOR);
        assertThat(context.selectedModes().memoryMode()).isEqualTo(AgentMemoryMode.CONTEXT_PACKER);
        assertThat(context.selectedModes().toolCallingMode()).isEqualTo(AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING);
        assertThat(context.ragResult()).isNull();
    }

    @Test
    void prepareAlwaysUsesLangChainDefaults() {
        AgentExperimentService service = new AgentExperimentService(
                mock(KnowledgeSearchService.class),
                mock(LangChain4jChatModelAdapter.class)
        );

        AgentExperimentContext context = service.prepare(7L, "hello", null);

        assertThat(context.enabled()).isFalse();
        assertThat(context.selectedModes().runtimeMode()).isEqualTo(AgentRuntimeMode.LANGCHAIN4J);
        assertThat(context.selectedModes().toolCallingMode()).isEqualTo(AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING);
    }

    @Test
    void prepareBuildsAugmentorRagResultAndDebugPayload() {
        KnowledgeSearchService knowledgeSearchService = mock(KnowledgeSearchService.class);
        when(knowledgeSearchService.search(eq("polarimetric fda mimo"), eq(5L), eq(6)))
                .thenReturn(List.of(new KnowledgeSearchResult(
                        11L,
                        "fda-note.md",
                        2,
                        "Polarimetric FDA-MIMO improves angle-range-polarization estimation.",
                        1.42,
                        false
                )));
        AgentExperimentService service = new AgentExperimentService(
                knowledgeSearchService,
                mock(LangChain4jChatModelAdapter.class)
        );
        AgentExperimentRequest request = new AgentExperimentRequest(
                true,
                AgentRuntimeMode.LANGCHAIN4J,
                AgentRagMode.LANGCHAIN4J_AUGMENTOR,
                AgentMemoryMode.CONTEXT_PACKER,
                AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING,
                List.of(AgentDebugFlag.SHOW_RETRIEVED_CHUNKS, AgentDebugFlag.SHOW_INJECTED_CONTEXT),
                true
        );

        AgentExperimentContext context = service.prepare(5L, "polarimetric fda mimo", request);
        AgentDebugPayload debug = service.toDebugPayload(context);

        assertThat(context.enabled()).isTrue();
        assertThat(context.ragResult()).isNotNull();
        assertThat(context.ragResult().retrievedChunks()).hasSize(1);
        assertThat(context.ragResult().ragContext()).contains("Answer using the following information");
        assertThat(debug.retrievedChunks()).hasSize(1);
        assertThat(debug.injectedContext()).contains("Polarimetric FDA-MIMO improves angle-range-polarization estimation.");
    }

    @Test
    void prepareBuildsAugmentorRagResult() {
        KnowledgeSearchService knowledgeSearchService = mock(KnowledgeSearchService.class);
        when(knowledgeSearchService.search(any(String.class), eq(9L), eq(6)))
                .thenReturn(List.of(new KnowledgeSearchResult(
                        21L,
                        "augmentor.md",
                        1,
                        "Augmentor mode injects retrieval context with metadata.",
                        1.91,
                        true
                )));
        LangChain4jChatModelAdapter chatModelAdapter = mock(LangChain4jChatModelAdapter.class);
        when(chatModelAdapter.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("polarimetric fda mimo recent literature"))
                        .build());
        AgentExperimentService service = new AgentExperimentService(knowledgeSearchService, chatModelAdapter);
        AgentExperimentRequest request = new AgentExperimentRequest(
                true,
                AgentRuntimeMode.LANGCHAIN4J,
                AgentRagMode.LANGCHAIN4J_AUGMENTOR,
                AgentMemoryMode.CONTEXT_PACKER,
                AgentToolCallingMode.LANGCHAIN4J_TOOL_BINDING,
                List.of(AgentDebugFlag.SHOW_RETRIEVED_CHUNKS, AgentDebugFlag.SHOW_INJECTED_CONTEXT),
                false
        );

        AgentExperimentContext context = service.prepare(9L, "find recent literature", request);

        assertThat(context.ragResult()).isNotNull();
        assertThat(context.ragResult().retrievedChunks()).hasSize(1);
        assertThat(context.ragResult().retrievedChunks().get(0).filename()).isEqualTo("augmentor.md");
        assertThat(context.ragResult().ragContext()).contains("Answer using the following information");
    }
}
