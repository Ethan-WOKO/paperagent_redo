package com.yanban.api.agent;

public interface RuntimeAdapter {

    boolean supports(AgentStrategy strategy);

    AgentRuntimeResult run(AgentRuntimeRequest request);
}
