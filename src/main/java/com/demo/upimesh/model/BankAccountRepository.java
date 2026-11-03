package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    long deleteAllByOwnerUserId(UUID ownerUserId);
    List<BankAccount> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<BankAccount> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    boolean existsByOwnerUserIdAndAccountFingerprint(UUID ownerUserId, String accountFingerprint);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BankAccount b where b.id=:id and b.ownerUserId=:owner")
    Optional<BankAccount> findByIdAndOwnerUserIdForUpdate(@Param("id") UUID id, @Param("owner") UUID ownerUserId);
    boolean existsByOwnerUserIdAndVerificationStatusAndVerificationModeNot(
            UUID ownerUserId, String verificationStatus, String verificationMode);
    long deleteByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}

