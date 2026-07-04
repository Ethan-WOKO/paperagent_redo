package com.yanban.api.agent;

import com.yanban.core.harness.HarnessEngine;
import com.yanban.core.harness.HarnessRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentHarnessAdapter implements RuntimeAdapter {

    private final HarnessEngine harnessEngine;

    public CurrentHarnessAdapter(HarnessEngine harnessEngine) {
        this.harnessEngine = harnessEngine;
    }

    @Override
    public boolean supports(AgentStrategy strategy) {
        return strategy == AgentStrategy.DIRECT || strategy == AgentStrategy.SINGLE_STEP_REACT;
    }

    @Override
    public AgentRuntimeResult run(AgentRuntimeRequest request) {
        return AgentRuntimeResult.fromHarnessResult(harnessEngine.run(new HarnessRequest(
                request.history(),
                request.userId(),
                request.userMessage(),
                request.provider(),
                request.model(),
                request.temperature(),
                request.maxTokens(),
                request.maxSteps(),
                request.ragDisabled(),
                request.apiKey(),
                request.apiUrl(),
                request.skillPrompt(),
                request.allowedToolNames(),
                request.maxToolCalls(),
                request.maxDuplicateToolCalls(),
                null,
                request.traceId()
        ), request.tokenConsumer()));
    }
}
