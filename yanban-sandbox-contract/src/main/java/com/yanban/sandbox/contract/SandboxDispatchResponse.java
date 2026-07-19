package com.yanban.sandbox.contract;
public record SandboxDispatchResponse(String executionId,String idempotencyKey,String requestDigest,
                                      long fence,SandboxExecutionStatus status) { }
