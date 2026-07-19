package com.yanban.api.agent.sandbox;

public final class SandboxExecutionException extends RuntimeException {
    private final SandboxFailureCode code;

    public SandboxExecutionException(SandboxFailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public SandboxExecutionException(SandboxFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public SandboxFailureCode code() { return code; }
}
