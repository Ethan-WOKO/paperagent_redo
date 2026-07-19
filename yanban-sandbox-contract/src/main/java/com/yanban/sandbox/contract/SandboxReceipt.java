package com.yanban.sandbox.contract;
import java.time.Instant; import java.util.Map;
public record SandboxReceipt(String executionId,String idempotencyKey,String requestDigest,long userId,long projectId,
 long sessionId,long planId,long stepId,long fence,String projectVersion,String policyDigest,String provider,
 SandboxExecutionStatus status,Integer exitCode,String stdout,String stderr,boolean outputTruncated,
 Map<String,Artifact> artifacts,Instant startedAt,Instant finishedAt,SandboxErrorCode errorCode) {
 public SandboxReceipt { artifacts=artifacts==null?Map.of():Map.copyOf(artifacts); }
 public record Artifact(String sha256,long sizeBytes){}
}
