package com.yanban.api.agent.sandbox;
import jakarta.persistence.LockModeType; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
interface SandboxOutboxRepository extends JpaRepository<SandboxOutboxExecution,Long>{
 Optional<SandboxOutboxExecution> findByPlanIdAndIdempotencyKey(Long planId,String key);
 Optional<SandboxOutboxExecution> findByPlanIdAndStepId(Long planId,Long stepId);
 Optional<SandboxOutboxExecution> findByExecutionId(String executionId);
 List<SandboxOutboxExecution> findByPlanId(Long planId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from SandboxOutboxExecution o where o.planId=:planId") List<SandboxOutboxExecution> lockByPlanId(@Param("planId")Long planId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from SandboxOutboxExecution o where o.executionId=:id") Optional<SandboxOutboxExecution> lockByExecutionId(@Param("id")String id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from SandboxOutboxExecution o where o.planId=:planId and o.idempotencyKey=:key") Optional<SandboxOutboxExecution> lockByPlanIdAndIdempotencyKey(@Param("planId")Long planId,@Param("key")String key);
 @Query("select o from SandboxOutboxExecution o where o.status in ('PENDING','RETRY','API_CANCEL_REQUESTED','ACCEPTED','CLAIMED','MATERIALIZING','CREATED','POLICY_APPLIED','RUNNING','SUCCEEDED_PENDING_CLEANUP','FAILED_PENDING_CLEANUP','CANCEL_REQUESTED','TIMED_OUT_PENDING_CLEANUP','CLEANING','RECEIPT_PENDING_PROJECTION') and (o.nextAttemptAt is null or o.nextAttemptAt<=current_timestamp)") List<SandboxOutboxExecution> findReconcileable();
}
