package com.demo.upimesh.model;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest,UUID> {
    Optional<AccountDeletionRequest> findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(UUID userId, AccountDeletionRequest.Status status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from AccountDeletionRequest r where r.userId=:userId and r.status=:status")
    Optional<AccountDeletionRequest> findByUserIdAndStatusForUpdate(@Param("userId") UUID userId,@Param("status") AccountDeletionRequest.Status status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccountDeletionRequest r where r.status=:status and r.scheduledDeletionAt<=:now")
    List<AccountDeletionRequest> findDueForUpdate(@Param("status") AccountDeletionRequest.Status status,@Param("now") Instant now);
    List<AccountDeletionRequest> findAllByStatusAndScheduledDeletionAtLessThanEqual(AccountDeletionRequest.Status status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from AccountDeletionRequest r where r.id=:id")
    Optional<AccountDeletionRequest> findByIdForUpdate(@Param("id") UUID id);
    boolean existsByDeletedEmailKey(String emailKey);
    List<AccountDeletionRequest> findAllByDeletedEmailKey(String emailKey);
    boolean existsByUserIdAndStatus(UUID userId, AccountDeletionRequest.Status status);
}


