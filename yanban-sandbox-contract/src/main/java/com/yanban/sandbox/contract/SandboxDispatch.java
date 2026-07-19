package com.yanban.sandbox.contract;
import java.util.List; import java.util.Map;
public record SandboxDispatch(String idempotencyKey,String requestDigest,long userId,long projectId,long sessionId,
 long planId,long stepId,long fence,String projectVersion,String policyDigest,Map<String,String> files,List<String> argv,
 int cpus,long memoryBytes,long timeoutMillis,long maxOutputBytes,boolean networkEnabled) {
 public SandboxDispatch { files=files==null?Map.of():Map.copyOf(files);argv=argv==null?List.of():List.copyOf(argv); }
 public SandboxDispatch withoutDigest(){return new SandboxDispatch(idempotencyKey,"",userId,projectId,sessionId,planId,stepId,fence,projectVersion,policyDigest,files,argv,cpus,memoryBytes,timeoutMillis,maxOutputBytes,networkEnabled);}
}
