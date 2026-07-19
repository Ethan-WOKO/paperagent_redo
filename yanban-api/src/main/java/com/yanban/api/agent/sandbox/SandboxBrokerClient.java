package com.yanban.api.agent.sandbox;
import com.yanban.sandbox.contract.SandboxDispatch;
import com.yanban.sandbox.contract.SandboxDispatchResponse;
import com.yanban.sandbox.contract.SandboxExecutionView;

public interface SandboxBrokerClient {
    void requireHealthy();
    SandboxDispatchResponse dispatch(SandboxDispatch request);
    SandboxExecutionView status(String executionId);
    void cancel(String executionId, long fence);
}
