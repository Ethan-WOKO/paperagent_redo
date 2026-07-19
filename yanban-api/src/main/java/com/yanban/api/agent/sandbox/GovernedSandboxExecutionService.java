package com.yanban.api.agent.sandbox;

import com.yanban.api.agent.SandboxPlanAuthorityResolver;
import com.yanban.api.project.ProjectService;
import com.yanban.sandbox.contract.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Transitional internal boundary; durable outbox service is the only intended caller. */
@Service
@ConditionalOnProperty(prefix="yanban.sandbox",name="enabled",havingValue="true")
final class GovernedSandboxExecutionService {
    private final SandboxExecutionProperties properties;
    private final SandboxCommandPolicy commands;
    private final ProjectService projects;
    GovernedSandboxExecutionService(SandboxExecutionProperties properties,
                                    SandboxCommandPolicy commands,ProjectService projects){this.properties=properties;this.commands=commands;this.projects=projects;}

    SandboxDispatch prepare(SandboxPlanAuthorityResolver.Resolution authority,Request request){
        if(!properties.isEnabled())fail(SandboxFailureCode.SANDBOX_DISABLED,"sandbox disabled");
        if(authority==null||request==null||authority.remainingExecutions()<1)fail(SandboxFailureCode.AUTHORITY_REJECTED,"authority rejected");
        commands.validate(request.argv(),Map.of());
        final ProjectService.SandboxWorkspaceMaterialization workspace;
        try { workspace=projects.materializeSandbox(authority.userId(),authority.projectId(),request.relativePaths()); }
        catch(org.springframework.web.server.ResponseStatusException ex){
            if(ex.getStatusCode().value()==409)fail(SandboxFailureCode.STALE_PROJECT_VERSION,"Project changed during sandbox materialization");
            if(ex.getStatusCode().value()==404)fail(SandboxFailureCode.INVALID_PATH,"requested Project file was not found");
            fail(SandboxFailureCode.AUTHORITY_REJECTED,"Project materialization was rejected");
            throw ex;
        }
        if(workspace.textFiles().size()!=request.relativePaths().size())fail(SandboxFailureCode.INVALID_PATH,"requested file missing");
        String version=workspace.snapshot().workspace().projectVersion().value();
        if(!version.equals(authority.projectVersion()))fail(SandboxFailureCode.STALE_PROJECT_VERSION,"ProjectVersion stale");
        SandboxDispatch unsigned=new SandboxDispatch(request.idempotencyKey(),"",authority.userId(),authority.projectId(),
                authority.sessionId(),authority.planId(),authority.stepId(),authority.lease().fence(),version,
                authority.policyDigest(),workspace.textFiles(),request.argv(),properties.getCpus(),properties.getMemoryLimit().toBytes(),
                properties.getExecutionTimeout().toMillis(),properties.getMaxOutputSize().toBytes(),false);
        SandboxDispatch dispatch=new SandboxDispatch(unsigned.idempotencyKey(),SandboxCanonicalDigest.compute(unsigned),unsigned.userId(),
                unsigned.projectId(),unsigned.sessionId(),unsigned.planId(),unsigned.stepId(),unsigned.fence(),unsigned.projectVersion(),
                unsigned.policyDigest(),unsigned.files(),unsigned.argv(),unsigned.cpus(),unsigned.memoryBytes(),unsigned.timeoutMillis(),unsigned.maxOutputBytes(),false);
        return dispatch;
    }
    private static void fail(SandboxFailureCode code,String message){throw new SandboxExecutionException(code,message);}
    record Request(String idempotencyKey,Set<String> relativePaths,List<String> argv){Request{relativePaths=relativePaths==null?Set.of():Set.copyOf(relativePaths);argv=argv==null?List.of():List.copyOf(argv);}}
}
