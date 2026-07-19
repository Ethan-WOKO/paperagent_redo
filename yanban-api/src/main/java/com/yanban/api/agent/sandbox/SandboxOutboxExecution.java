package com.yanban.api.agent.sandbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="sandbox_execution_outbox",uniqueConstraints={
        @UniqueConstraint(name="uk_sandbox_outbox_execution",columnNames="execution_id"),
        @UniqueConstraint(name="uk_sandbox_outbox_idempotency",columnNames={"plan_id","idempotency_key"}),
        @UniqueConstraint(name="uk_sandbox_outbox_step",columnNames={"plan_id","step_id"})})
class SandboxOutboxExecution {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="execution_id",nullable=false,length=64) private String executionId;
    @Column(name="plan_id",nullable=false) private Long planId;
    @Column(name="step_id",nullable=false) private Long stepId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="session_id",nullable=false) private Long sessionId;
    @Column(name="project_id",nullable=false) private Long projectId;
    @Column(name="lease_fence",nullable=false) private long leaseFence;
    @Column(name="idempotency_key",nullable=false,length=128) private String idempotencyKey;
    @Column(name="request_digest",nullable=false,length=64) private String requestDigest;
    @Column(name="project_version",nullable=false,length=64) private String projectVersion;
    @Column(name="policy_digest",nullable=false,length=64) private String policyDigest;
    @Column(name="request_json",columnDefinition="LONGTEXT") private String requestJson;
    @Column(nullable=false,length=32) private String status;
    @Column(name="broker_execution_id",length=64) private String brokerExecutionId;
    @Column(name="receipt_digest",length=64) private String receiptDigest;
    @Column(name="receipt_json",columnDefinition="LONGTEXT") private String receiptJson;
    @Column(name="error_code",length=64) private String errorCode;
    @Column(name="dispatch_attempts",nullable=false) private int dispatchAttempts;
    @Column(name="next_attempt_at") private LocalDateTime nextAttemptAt;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="claim_owner",length=128) private String claimOwner; @Column(name="claim_token",length=64) private String claimToken;
    @Column(name="claim_fence",nullable=false) private long claimFence; @Column(name="claim_expires_at") private LocalDateTime claimExpiresAt;
    @Column(name="retry_phase",nullable=false,length=16) private String retryPhase;
    protected SandboxOutboxExecution(){}
    SandboxOutboxExecution(String executionId,long userId,long projectId,long sessionId,long planId,long stepId,long fence,
                           String key,String digest,String version,String policyDigest,String requestJson){
        this.executionId=executionId;this.userId=userId;this.projectId=projectId;this.sessionId=sessionId;this.planId=planId;
        this.stepId=stepId;leaseFence=fence;idempotencyKey=key;requestDigest=digest;projectVersion=version;
        this.policyDigest=policyDigest;this.requestJson=requestJson;status="PENDING";retryPhase="DISPATCH";
    }
    String executionId(){return executionId;} Long planId(){return planId;} Long stepId(){return stepId;} Long userId(){return userId;}
    String idempotencyKey(){return idempotencyKey;} String requestDigest(){return requestDigest;} String requestJson(){return requestJson;}
    String status(){return status;} String brokerExecutionId(){return brokerExecutionId;} long leaseFence(){return leaseFence;}
    Long sessionId(){return sessionId;} Long projectId(){return projectId;} String projectVersion(){return projectVersion;} String policyDigest(){return policyDigest;}
    String receiptDigest(){return receiptDigest;}
    String receiptJson(){return receiptJson;}
    String retryPhase(){return retryPhase;} String claimToken(){return claimToken;} long claimFence(){return claimFence;} LocalDateTime claimExpiresAt(){return claimExpiresAt;}
    void claim(String owner,String token,LocalDateTime now){claimOwner=owner;claimToken=token;claimFence++;claimExpiresAt=now.plusSeconds(30);updatedAt=now;}
    boolean claimMatches(String token,long fence,LocalDateTime now){return java.util.Objects.equals(claimToken,token)&&claimFence==fence&&claimExpiresAt!=null&&claimExpiresAt.isAfter(now);}
    void releaseClaim(){claimOwner=null;claimToken=null;claimExpiresAt=null;}
    void dispatched(String brokerId,String brokerStatus,LocalDateTime now){brokerExecutionId=brokerId;status=brokerStatus;retryPhase="POLL";dispatchAttempts++;nextAttemptAt=null;updatedAt=now;releaseClaim();}
    void retry(String code,LocalDateTime next,LocalDateTime now){errorCode=code;status="RETRY";dispatchAttempts++;nextAttemptAt=next;updatedAt=now;releaseClaim();}
    void requestCancel(LocalDateTime now){if(!java.util.Set.of("SUCCEEDED","FAILED","CANCELLED","TIMED_OUT","CLEANUP_FAILED").contains(status)){status="API_CANCEL_REQUESTED";retryPhase="CANCEL";updatedAt=now;releaseClaim();}}
    void projectReceipt(String digest,String json,String terminal,LocalDateTime now){if(receiptDigest!=null&&!receiptDigest.equals(digest))throw new IllegalStateException("receipt is immutable");receiptDigest=digest;receiptJson=json;status=terminal;requestJson=null;nextAttemptAt=null;claimOwner=null;claimToken=null;claimExpiresAt=null;updatedAt=now;}
    void stageReceipt(String digest,String json,LocalDateTime now){if(receiptDigest!=null&&!java.util.Objects.equals(receiptDigest,digest))throw new IllegalStateException("receipt is immutable");receiptDigest=digest;receiptJson=json;status="RECEIPT_PENDING_PROJECTION";retryPhase="PROJECTION";nextAttemptAt=now;updatedAt=now;releaseClaim();}
    void finishProjection(String terminal,String code,LocalDateTime now){status=terminal;errorCode=code;requestJson=null;nextAttemptAt=null;updatedAt=now;releaseClaim();}
    void deferProjection(LocalDateTime now){deferProjection("SANDBOX_PROJECTION_DEFERRED",now,1);}
    void deferProjection(String code,LocalDateTime now,long seconds){status="RECEIPT_PENDING_PROJECTION";retryPhase="PROJECTION";errorCode=code;nextAttemptAt=now.plusSeconds(seconds);updatedAt=now;releaseClaim();}
}
