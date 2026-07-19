package com.yanban.sandboxbroker;
import jakarta.persistence.LockModeType; import java.util.Optional; import java.util.List; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
interface SandboxExecutionRepository extends JpaRepository<SandboxExecutionEntity,Long> {
 Optional<SandboxExecutionEntity> findByIdempotencyKey(String key);
 Optional<SandboxExecutionEntity> findByExecutionId(String executionId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from SandboxExecutionEntity e where e.executionId=:id")
 Optional<SandboxExecutionEntity> lockByExecutionId(@Param("id") String id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from SandboxExecutionEntity e where e.idempotencyKey=:key")
 Optional<SandboxExecutionEntity> lockByIdempotencyKey(@Param("key") String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from SandboxExecutionEntity e where e.status in ('ACCEPTED','CLAIMED','MATERIALIZING','CREATED','POLICY_APPLIED','RUNNING','SUCCEEDED_PENDING_CLEANUP','FAILED_PENDING_CLEANUP','CANCEL_REQUESTED','TIMED_OUT_PENDING_CLEANUP','CLEANING') and (e.leaseExpiresAt is null or e.leaseExpiresAt<=current_timestamp) order by e.createdAt")
 List<SandboxExecutionEntity> lockClaimable(org.springframework.data.domain.Pageable page);
 @Query("select current_timestamp from SandboxExecutionEntity e where e.executionId=:id") java.time.LocalDateTime databaseNow(@Param("id")String id);
}
