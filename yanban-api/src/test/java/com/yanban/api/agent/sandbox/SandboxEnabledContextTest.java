package com.yanban.api.agent.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanban.api.agent.AgentPlanCheckpointService;
import com.yanban.api.agent.AgentToolPolicyEngine;
import com.yanban.api.agent.ResolvedToolPolicy;
import com.yanban.api.agent.SandboxPlanAuthorityResolver;
import com.yanban.api.agent.PlanAgentService;
import com.yanban.api.project.ProjectFileEntry;
import com.yanban.api.project.ProjectManifestResponse;
import com.yanban.api.project.ProjectService;
import com.yanban.core.agent.AgentPlan;
import com.yanban.core.agent.AgentPlanEventRepository;
import com.yanban.core.agent.AgentPlanExecutionLease;
import com.yanban.core.agent.AgentPlanRepository;
import com.yanban.core.agent.AgentPlanRunLeaseService;
import com.yanban.core.agent.AgentPlanStep;
import com.yanban.core.agent.AgentPlanStepRepository;
import com.yanban.core.agent.AgentSession;
import com.yanban.core.agent.AgentSessionRepository;
import com.yanban.core.agent.AgentSessionScope;
import com.yanban.core.agent.sandbox.SandboxFileSnapshot;
import com.yanban.core.agent.sandbox.SandboxWorkspaceRef;
import com.yanban.core.agent.sandbox.SandboxWorkspaceSnapshot;
import com.yanban.core.research.FileHash;
import com.yanban.core.research.ProjectManifestIdentity;
import com.yanban.core.research.ProjectRelativePath;
import com.yanban.sandbox.contract.SandboxCanonicalDigest;
import com.yanban.sandbox.contract.SandboxDispatch;
import com.yanban.sandbox.contract.SandboxErrorCode;
import com.yanban.sandbox.contract.SandboxExecutionStatus;
import com.yanban.sandbox.contract.SandboxReceipt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = {
        "yanban.sandbox.enabled=true",
        "yanban.sandbox.required-at-startup=false",
        "yanban.sandbox.broker-url=https://127.0.0.1:9443",
        "yanban.sandbox.broker-token=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        "spring.datasource.url=jdbc:h2:mem:sandbox_enabled_context;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "yanban.jwt.secret=test_secret_123456789012345678901234567890"
})
class SandboxEnabledContextTest {
    @Autowired SandboxReceiptProjectionService projection;
    @Autowired PlanAgentService planAgentService;
    @Autowired AgentPlanRepository plans;
    @Autowired AgentPlanStepRepository steps;
    @Autowired AgentPlanEventRepository events;
    @Autowired AgentSessionRepository sessions;
    @Autowired AgentPlanRunLeaseService leases;
    @Autowired AgentPlanCheckpointService checkpoints;
    @Autowired SandboxCapabilityPolicyResolver sandboxPolicies;
    @Autowired SandboxOutboxRepository outbox;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean ProjectService projects;
    @MockBean AgentToolPolicyEngine toolPolicies;

    private static final long USER = 7301L;
    private static final long PROJECT = 7302L;
    private static final String PATH = "src/Main.java";
    private static final String CONTENT = "class Main {}";
    private String fileHash;
    private String version;
    private SandboxWorkspaceSnapshot snapshot;

    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO sys_users (id,username,password_hash) SELECT ?,?,? WHERE NOT EXISTS "
                        + "(SELECT 1 FROM sys_users WHERE id=?)",
                USER, "sandbox-projection-user", "not-a-real-password", USER);
        jdbc.update("INSERT INTO projects (id,user_id,name,root_type,root_path,canonical_root_path,access_mode,"
                        + "include_rules,ignore_rules,index_version) SELECT ?,?,'sandbox-test','LOCAL','sandbox-test',"
                        + "'sandbox-test','READ_ONLY','[]','[]','test' WHERE NOT EXISTS (SELECT 1 FROM projects WHERE id=?)",
                PROJECT, USER, PROJECT);
        fileHash = sha256(CONTENT);
        ProjectRelativePath path = ProjectRelativePath.of(PATH);
        FileHash hash = new FileHash(fileHash);
        version = ProjectManifestIdentity.derive(List.of(
                new ProjectManifestIdentity.Entry(path, hash, CONTENT.getBytes(StandardCharsets.UTF_8).length))).value();
        snapshot = new SandboxWorkspaceSnapshot(
                new SandboxWorkspaceRef(PROJECT, new com.yanban.core.research.ProjectVersionRef(version)),
                List.of(new SandboxFileSnapshot(path, hash, CONTENT.getBytes(StandardCharsets.UTF_8).length)));
        when(projects.manifest(eq(USER), eq(PROJECT))).thenReturn(new ProjectManifestResponse(PROJECT, version,
                List.of(new ProjectFileEntry(PATH, CONTENT.getBytes(StandardCharsets.UTF_8).length,
                        Instant.EPOCH, fileHash))));
        when(projects.materializeSandbox(eq(USER), eq(PROJECT), any())).thenReturn(
                new ProjectService.SandboxWorkspaceMaterialization(snapshot, Map.of(PATH, CONTENT)));
        when(toolPolicies.decideProject(any(), any())).thenReturn(
                new AgentToolPolicyEngine.Decision(List.of(), 2, 1, "test-project-policy"));
    }

    @Test void enabledProjectionServiceIsARealTransactionalAopProxy() {
        assertThat(AopUtils.isAopProxy(projection)).isTrue();
        assertThat(AopUtils.isCglibProxy(projection)).isTrue();
    }

    @Test
    void receiptDefersBehindDispatchLeaseThenProjectsExactlyOnceOnAdjacentFence() throws Exception {
        Fixture fixture=createFixture(SandboxExecutionStatus.SUCCEEDED);
        AgentPlan plan=fixture.plan(); AgentPlanStep step=fixture.step(); AgentPlanExecutionLease dispatchLease=fixture.lease();
        String executionId=fixture.executionId();

        assertThat(projection.project(executionId)).isEqualTo(SandboxReceiptProjectionService.Result.DEFERRED);
        assertThat(outbox.findByExecutionId(executionId).orElseThrow().status())
                .isEqualTo("RECEIPT_PENDING_PROJECTION");
        assertThat(events.findByPlanIdOrderByCreatedAtAsc(plan.getId())).isEmpty();

        leases.release(dispatchLease, "SANDBOX_DISPATCHED");
        jdbc.execute("ALTER TABLE agent_plan_events ADD CONSTRAINT sandbox_projection_event_failure "
                + "CHECK (event_type <> 'step_project_evidence')");
        assertThatThrownBy(() -> projection.project(executionId)).isInstanceOf(RuntimeException.class);
        assertThat(steps.findById(step.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(outbox.findByExecutionId(executionId).orElseThrow().status())
                .isEqualTo("RECEIPT_PENDING_PROJECTION");
        assertThat(events.findByPlanIdOrderByCreatedAtAsc(plan.getId())).isEmpty();
        jdbc.execute("ALTER TABLE agent_plan_events DROP CONSTRAINT sandbox_projection_event_failure");

        assertThat(projection.project(executionId)).isEqualTo(SandboxReceiptProjectionService.Result.PROJECTED);
        assertThat(steps.findById(step.getId()).orElseThrow().getStatus()).isEqualTo("COMPLETED");
        assertThat(events.findByPlanIdOrderByCreatedAtAsc(plan.getId()))
                .extracting(event -> event.getEventType()).containsExactly("step_project_evidence");
        assertThat(outbox.findByExecutionId(executionId).orElseThrow().requestJson()).isNull();
        assertThat(events.findByPlanIdOrderByCreatedAtAsc(plan.getId()).get(0).getPayloadJson())
                .doesNotContain("BUILD SUCCESS", "top-secret");

        assertThat(projection.project(executionId)).isEqualTo(SandboxReceiptProjectionService.Result.PROJECTED);
        assertThat(events.findByPlanIdOrderByCreatedAtAsc(plan.getId())).hasSize(1);

        ReflectionTestUtils.invokeMethod(planAgentService,"recoverExpiredDurablePlansSynchronously");
        AgentPlan finished=plans.findById(plan.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo("COMPLETED");
        assertThat(finished.getCanonicalAnswer()).isNotBlank();
        assertThat(finished.getCanonicalAnswerHash()).isEqualTo(sha256(finished.getCanonicalAnswer()));
        ReflectionTestUtils.invokeMethod(planAgentService,"recoverExpiredDurablePlansSynchronously");
        assertThat(plans.findById(plan.getId()).orElseThrow().getCanonicalAnswer()).isEqualTo(finished.getCanonicalAnswer());
        assertThat(events.findByPlanIdOrderByCreatedAtAsc(plan.getId()))
                .extracting(event->event.getEventType())
                .containsOnlyOnce("step_project_evidence","plan_completed")
                .containsOnlyOnce("plan_restart_recovery_queued");
    }

    @Test
    void deterministicAuthorityFailuresRetainReceiptButNeverPublishEvidence() throws Exception {
        Fixture policy=createFixture(SandboxExecutionStatus.SUCCEEDED); leases.release(policy.lease(),"DISPATCHED");
        jdbc.update("UPDATE sandbox_execution_outbox SET policy_digest=? WHERE execution_id=?","f".repeat(64),policy.executionId());
        assertRejected(policy);

        Fixture stale=createFixture(SandboxExecutionStatus.SUCCEEDED); leases.release(stale.lease(),"DISPATCHED");
        when(projects.materializeSandbox(eq(USER),eq(PROJECT),any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,"STALE"));
        assertRejected(stale);
    }

    @Test
    void cancellationAndIntermediateRecoveryFenceRejectVerifiedSuccess() throws Exception {
        Fixture cancelled=createFixture(SandboxExecutionStatus.SUCCEEDED);
        leases.cancel(cancelled.plan().getId(),USER,"cancelled");
        assertRejected(cancelled);

        Fixture fenced=createFixture(SandboxExecutionStatus.SUCCEEDED); leases.release(fenced.lease(),"DISPATCHED");
        AgentPlanExecutionLease middle=leases.claim(fenced.plan().getId(),USER,"middle-owner",Duration.ofSeconds(30)).orElseThrow();
        leases.release(middle,"RECOVERED");
        assertRejected(fenced);
    }

    @Test
    void nonSuccessReceiptsFailStepWithoutVerifiedEvidence() throws Exception {
        for(SandboxExecutionStatus status:List.of(SandboxExecutionStatus.FAILED,SandboxExecutionStatus.TIMED_OUT,
                SandboxExecutionStatus.CANCELLED,SandboxExecutionStatus.CLEANUP_FAILED)){
            Fixture fixture=createFixture(status); leases.release(fixture.lease(),"DISPATCHED");
            assertThat(projection.project(fixture.executionId())).isEqualTo(SandboxReceiptProjectionService.Result.PROJECTED);
            assertThat(steps.findById(fixture.step().getId()).orElseThrow().getStatus()).isEqualTo("FAILED");
            assertThat(events.findByPlanIdOrderByCreatedAtAsc(fixture.plan().getId()))
                    .extracting(event->event.getEventType()).containsExactly("sandbox_execution_failed");
        }
    }

    private void assertRejected(Fixture fixture){
        assertThat(projection.project(fixture.executionId())).isEqualTo(SandboxReceiptProjectionService.Result.REJECTED);
        SandboxOutboxExecution stored=outbox.findByExecutionId(fixture.executionId()).orElseThrow();
        assertThat(stored.status()).isEqualTo("CANCELLED");
        assertThat(stored.receiptJson()).isNotBlank(); assertThat(stored.receiptDigest()).hasSize(64);
        assertThat(stored.requestJson()).isNull();
        assertThat(steps.findById(fixture.step().getId()).orElseThrow().getStatus()).isNotEqualTo("COMPLETED");
        assertThat(events.findByPlanIdOrderByCreatedAtAsc(fixture.plan().getId()))
                .noneMatch(event->"step_project_evidence".equals(event.getEventType()));
        assertThat(projection.project(fixture.executionId())).isEqualTo(SandboxReceiptProjectionService.Result.PROJECTED);
    }

    private Fixture createFixture(SandboxExecutionStatus status) throws Exception {
        AgentSession session=sessions.saveAndFlush(new AgentSession(USER,"sandbox","deepseek","deepseek-chat",8,true,AgentSessionScope.PROJECT,PROJECT));
        String envelope="{\"schemaVersion\":\"project_plan_envelope_v1\",\"plannerRawJson\":\"{}\",\"serverAttestedProjectContext\":{\"projectId\":"+PROJECT+",\"capability\":\"PROJECT_READ\"}}";
        AgentPlan plan=new AgentPlan(session.getId(),USER,"sandbox","sandbox",true,null,envelope);plan.enableDurableExecution();plan=plans.saveAndFlush(plan);
        AgentPlanStep step=steps.saveAndFlush(new AgentPlanStep(plan.getId(),"sandbox",1,"sandbox","governed check","SANDBOX_EXECUTE","[]","[\"sandbox_execute\"]","receipt"));
        AgentPlanExecutionLease lease=leases.claim(plan.getId(),USER,"dispatch-owner",Duration.ofSeconds(30)).orElseThrow();
        ResolvedToolPolicy policy=sandboxPolicies.resolve(toolPolicies.decideProject(null,null).resolved(),null);
        var validation=checkpoints.initializeOrValidate(lease,policy,new AgentPlanCheckpointService.BudgetCeiling(240,2,1,2));
        String policyDigest=SandboxPlanAuthorityResolver.policyDigest(policy,validation),executionId="api-projection-"+plan.getId(),key="sandbox:"+plan.getId()+":"+step.getId();
        SandboxDispatch unsigned=new SandboxDispatch(key,"",USER,PROJECT,session.getId(),plan.getId(),step.getId(),lease.fence(),version,policyDigest,Map.of(PATH,CONTENT),List.of("javac","src/Main.java"),1,1024*1024,30_000,1024,false);
        String digest=SandboxCanonicalDigest.compute(unsigned);
        SandboxDispatch request=new SandboxDispatch(key,digest,USER,PROJECT,session.getId(),plan.getId(),step.getId(),lease.fence(),version,policyDigest,Map.of(PATH,CONTENT),unsigned.argv(),unsigned.cpus(),unsigned.memoryBytes(),unsigned.timeoutMillis(),unsigned.maxOutputBytes(),false);
        Instant now=Instant.now(); SandboxErrorCode error=status==SandboxExecutionStatus.SUCCEEDED?null:SandboxErrorCode.PROVIDER_REJECTED;
        SandboxReceipt receipt=new SandboxReceipt("broker-"+executionId,key,digest,USER,PROJECT,session.getId(),plan.getId(),step.getId(),lease.fence(),version,policyDigest,"docker-sbx",status,status==SandboxExecutionStatus.SUCCEEDED?0:1,"BUILD SUCCESS top-secret","",false,Map.of(),now,now,error);
        String requestJson=json.writeValueAsString(request),receiptJson=json.writeValueAsString(receipt);
        jdbc.update("INSERT INTO sandbox_execution_outbox (execution_id,plan_id,step_id,user_id,session_id,project_id,lease_fence,idempotency_key,request_digest,project_version,policy_digest,request_json,status,broker_execution_id,receipt_digest,receipt_json,dispatch_attempts,created_at,updated_at,claim_fence,retry_phase) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,current_timestamp,current_timestamp,0,'PROJECTION')",executionId,plan.getId(),step.getId(),USER,session.getId(),PROJECT,lease.fence(),key,digest,version,policyDigest,requestJson,"RECEIPT_PENDING_PROJECTION",receipt.executionId(),sha256(receiptJson),receiptJson);
        return new Fixture(plan,step,lease,executionId);
    }

    private record Fixture(AgentPlan plan,AgentPlanStep step,AgentPlanExecutionLease lease,String executionId){}

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
