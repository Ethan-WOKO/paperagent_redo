package com.yanban.sandbox.contract;
public record SandboxExecutionView(String executionId,String idempotencyKey,String requestDigest,long fence,
                                   SandboxExecutionStatus status,SandboxReceipt receipt,SandboxErrorCode errorCode) { }
