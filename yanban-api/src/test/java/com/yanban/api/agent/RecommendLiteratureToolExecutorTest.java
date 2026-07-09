package com.yanban.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yanban.core.tool.ToolCall;
import com.yanban.core.tool.ToolResult;
import com.yanban.paper.literature.LiteratureRecommendationService;
import com.yanban.paper.literature.LiteratureRecommendationService.RecommendationRequest;
import com.yanban.paper.literature.LiteratureRecommendationService.RecommendationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecommendLiteratureToolExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executeAcceptsStringBooleanArgumentsFromLlm() {
        LiteratureRecommendationService recommendationService = mock(LiteratureRecommendationService.class);
        when(recommendationService.recommend(any())).thenReturn(RecommendationResult.empty("ok"));
        RecommendLiteratureToolExecutor executor = new RecommendLiteratureToolExecutor(recommendationService, objectMapper);

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", "embodied intelligence");
        args.put("includeBibtex", "true");

        ToolResult result = executor.execute(new ToolCall("call-1", "recommend_literature", args));

        ArgumentCaptor<RecommendationRequest> captor = ArgumentCaptor.forClass(RecommendationRequest.class);
        verify(recommendationService).recommend(captor.capture());
        assertThat(result.success()).isTrue();
        assertThat(captor.getValue().includeBibtex()).isTrue();
    }
}
