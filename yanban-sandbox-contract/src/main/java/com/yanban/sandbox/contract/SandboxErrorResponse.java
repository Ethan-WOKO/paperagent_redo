package com.yanban.sandbox.contract;

public record SandboxErrorResponse(SandboxErrorCode code, String message) {
    public SandboxErrorResponse {
        if (code == null) throw new IllegalArgumentException("sandbox error code is required");
        message = message == null ? "sandbox request failed" : message;
    }
}
