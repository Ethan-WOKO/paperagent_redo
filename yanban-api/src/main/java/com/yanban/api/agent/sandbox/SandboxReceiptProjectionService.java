package com.yanban.api.agent.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.agent.*;
import com.yanban.api.project.ProjectService;
import com.yanban.api.skills.ResolvedSkill;
import com.yanban.api.skills.SkillsService;
import com.yanban.core.agent.*;
import com.yanban.sandbox.contract.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Plan->outbox locked, fenced, exactly-once receipt projection boundary. */
@Service
@ConditionalOnProperty(prefix="yanban.sandbox", name="enabled", havingValue="true")
public class SandboxReceiptProjectionService {
    public enum Result { PROJECTED, DEFERRED, REJECTED }
    private final SandboxOutboxRepository outbox; private final AgentPlanRepository plans; private final AgentPlanStepRepository steps;
    private final AgentPlanRunLeaseService leases; private final AgentSessionRepository sessions; private final AgentPlanCheckpointService checkpoints;
    private final AgentToolPolicyEngine toolPolicies; private final SandboxCapabilityPolicyResolver sandboxPolicies; private final SkillsService skills;
    private final ProjectService projects; private final ObjectMapper json; private final JdbcTemplate jdbc;
    private final String owner="sandbox-projector-"+UUID.randomUUID();

    public SandboxReceiptProjectionService(SandboxOutboxRepository outbox,AgentPlanRepository plans,AgentPlanStepRepository steps,
            AgentPlanRunLeaseService leases,AgentSessionRepository sessions,AgentPlanCheckpointService checkpoints,
            AgentToolPolicyEngine toolPolicies,SandboxCapabilityPolicyResolver sandboxPolicies,SkillsService skills,
            ProjectService projects,ObjectMapper json,JdbcTemplate jdbc){this.outbox=outbox;this.plans=plans;this.steps=steps;this.leases=leases;this.sessions=sessions;this.checkpoints=checkpoints;this.toolPolicies=toolPolicies;this.sandboxPolicies=sandboxPolicies;this.skills=skills;this.projects=projects;this.json=json;this.jdbc=jdbc;}

    @Transactional
    public Result project(String executionId){
        SandboxOutboxExecution snapshot=outbox.findByExecutionId(executionId).orElseThrow();
        if(!"RECEIPT_PENDING_PROJECTION".equals(snapshot.status()))return Result.PROJECTED;
        AgentPlanExecutionLease lease=leases.claim(snapshot.planId(),snapshot.userId(),owner,Duration.ofSeconds(30)).orElse(null);
        if(lease==null){
            AgentPlan plan=plans.findLockedByIdAndUserId(snapshot.planId(),snapshot.userId()).orElse(null);
            SandboxOutboxExecution locked=outbox.lockByExecutionId(executionId).orElseThrow();
            LocalDateTime now=dbNow();
            if(plan!=null&&"RUNNING".equals(plan.getStatus())&&Objects.equals(plan.getLeaseFence(),snapshot.leaseFence())
                    && plan.getLeaseExpiresAt()!=null&&plan.getLeaseExpiresAt().isAfter(now)){
                locked.deferProjection(now);return Result.DEFERRED;
            }
            locked.finishProjection("CANCELLED","SANDBOX_PROJECTION_AUTHORITY_REJECTED",now);return Result.REJECTED;
        }
        SandboxOutboxExecution locked=null;
        try{
            // claim() locked Plan first; outbox is always locked second to match cancelPlan's ordering.
            AgentPlan plan=plans.findLockedByIdAndUserId(snapshot.planId(),snapshot.userId()).orElseThrow();
            locked=outbox.lockByExecutionId(executionId).orElseThrow();
            LocalDateTime now=dbNow();
            if(!"RECEIPT_PENDING_PROJECTION".equals(locked.status()))return Result.PROJECTED;
            if(lease.fence()!=Math.addExact(locked.leaseFence(),1L)||!"RUNNING".equals(plan.getStatus())
                    ||!locked.sessionId().equals(plan.getSessionId()))return reject(locked,now);
            AgentPlanStep step=steps.findById(locked.stepId()).orElse(null);
            AgentSession session=sessions.findById(locked.sessionId()).orElse(null);
            if(step==null||!locked.planId().equals(step.getPlanId())||!"SANDBOX_EXECUTE".equals(step.getType())
                    ||!readTools(step.getAllowedToolsJson()).contains(SandboxPlanAuthorityResolver.TOOL_NAME)
                    ||session==null||!locked.userId().equals(session.getUserId())||!locked.projectId().equals(session.getProjectId()))return reject(locked,now);
            ResolvedSkill skill=plan.getSkillId()==null?null:skills.resolveEnabledSkill(locked.userId(),plan.getSkillId());
            ResolvedToolPolicy current=sandboxPolicies.resolve(toolPolicies.decideProject(skill==null?null:skill.allowedTools(),null).resolved(),skill);
            int count=Math.max(1,steps.findByPlanIdOrderBySortOrderAsc(plan.getId()).size());
            AgentPlanCheckpointService.BudgetCeiling ceiling=new AgentPlanCheckpointService.BudgetCeiling(240,2,1,Math.multiplyExact(current.maxToolCalls(),count));
            AgentPlanCheckpointService.Validation validation=checkpoints.initializeOrValidate(lease,current,ceiling);
            if(!locked.policyDigest().equals(SandboxPlanAuthorityResolver.policyDigest(current,validation)))return reject(locked,now);
            SandboxDispatch request=readDispatch(locked.requestJson());
            if(!projects.materializeSandbox(locked.userId(),locked.projectId(),request.files().keySet()).snapshot().workspace().projectVersion().value().equals(locked.projectVersion()))return reject(locked,now);
            SandboxReceipt receipt=readReceipt(locked.receiptJson());
            projectExactlyOnce(locked,step,lease,request,receipt);
            locked.finishProjection(receipt.status().name(),null,now);
            return Result.PROJECTED;
        }catch(RuntimeException exception){
            if(locked!=null&&deterministicAuthorityRejection(exception))return reject(locked,dbNow());
            throw exception;
        }finally{leases.release(lease,"SANDBOX_RECEIPT_PROJECTED");}
    }

    private boolean deterministicAuthorityRejection(RuntimeException exception){
        if(exception instanceof ResponseStatusException response){
            int status=response.getStatusCode().value();
            return status==400||status==401||status==403||status==404||status==409||status==410||status==422;
        }
        if(exception instanceof org.springframework.dao.TransientDataAccessException
                ||exception instanceof org.springframework.dao.RecoverableDataAccessException
                ||exception instanceof org.springframework.dao.QueryTimeoutException)return false;
        return exception instanceof IllegalArgumentException||exception instanceof IllegalStateException;
    }

    private Result reject(SandboxOutboxExecution value,LocalDateTime now){value.finishProjection("CANCELLED","SANDBOX_PROJECTION_AUTHORITY_REJECTED",now);return Result.REJECTED;}
    private void projectExactlyOnce(SandboxOutboxExecution value,AgentPlanStep step,AgentPlanExecutionLease lease,SandboxDispatch request,SandboxReceipt receipt){
        String result="Sandbox receipt "+value.receiptDigest()+"; provider="+receipt.provider()+"; status="+receipt.status()+"; exitCode="+receipt.exitCode()+"; stdoutSha256="+sha256(receipt.stdout())+"; stderrSha256="+sha256(receipt.stderr())+"; candidate=NOT_APPLIED";
        if(receipt.status()==SandboxExecutionStatus.SUCCEEDED)step.markCompleted(result);else step.markFailed("SANDBOX_"+receipt.status(),result);
        leases.saveOwnedStep(lease,step);
        String key="sandbox-receipt:"+value.executionId();
        AgentPlanEvent event=new AgentPlanEvent(value.planId(),value.stepId(),receipt.status()==SandboxExecutionStatus.SUCCEEDED?"step_project_evidence":"sandbox_execution_failed",
                write(receipt.status()==SandboxExecutionStatus.SUCCEEDED?sandboxEvidence(value,receipt,request):Map.of("executionId",value.executionId(),"status",receipt.status().name(),"candidateApplicationStatus","NOT_APPLIED")),key);
        leases.saveOwnedEvent(lease,event);
    }
    private Map<String,Object> sandboxEvidence(SandboxOutboxExecution value,SandboxReceipt receipt,SandboxDispatch request){
        List<Map<String,Object>> evidence=new ArrayList<>();
        request.files().forEach((path,content)->evidence.add(Map.ofEntries(Map.entry("id","trusted-tool:"+value.projectId()+":sandbox:"+value.executionId()+":"+sha256(path)),Map.entry("sourceType","PROJECT"),Map.entry("source","PROJECT"),Map.entry("file",path),Map.entry("chunk","status="+receipt.status()+" exitCode="+receipt.exitCode()+" stdoutSha256="+sha256(receipt.stdout())+" stderrSha256="+sha256(receipt.stderr())),Map.entry("citation","sandbox:"+value.executionId()),Map.entry("version",sha256(content)),Map.entry("selectionReason","server-verified governed sandbox receipt; candidate NOT_APPLIED"),Map.entry("projectVersion",value.projectVersion()),Map.entry("fileHash",sha256(content)),Map.entry("startLine",1),Map.entry("endLine",1),Map.entry("parserVersion","sandbox-receipt-v1"),Map.entry("versionStatus","VERIFIED"))));
        return Map.of("executionId",value.executionId(),"requestDigest",value.requestDigest(),"receiptDigest",value.receiptDigest(),"status",receipt.status().name(),"commandProfile",request.argv().isEmpty()?"":request.argv().get(0),"artifacts",receipt.artifacts(),"candidateApplicationStatus","NOT_APPLIED","evidence",evidence);
    }
    private List<String> readTools(String value){try{var node=json.readTree(value);if(node==null||!node.isArray())return List.of();List<String> out=new ArrayList<>();node.forEach(v->{if(v.isTextual())out.add(v.asText());});return out;}catch(Exception ex){return List.of();}}
    private SandboxDispatch readDispatch(String value){try{return json.readValue(value,SandboxDispatch.class);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private SandboxReceipt readReceipt(String value){try{return json.readValue(value,SandboxReceipt.class);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private LocalDateTime dbNow(){return jdbc.queryForObject("select current_timestamp",LocalDateTime.class);}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
