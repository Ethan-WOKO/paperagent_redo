package com.yanban.api.agent;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeService {

    private final List<RuntimeAdapter> adapters;

    public AgentRuntimeService(List<RuntimeAdapter> adapters) {
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }

    public AgentRuntimeResult run(AgentRuntimeRequest request) {
        RuntimeAdapter adapter = adapters.stream()
                .filter(candidate -> candidate.supports(request.strategy()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No runtime adapter available for strategy " + request.strategy()));
        return adapter.run(request);
    }
}
