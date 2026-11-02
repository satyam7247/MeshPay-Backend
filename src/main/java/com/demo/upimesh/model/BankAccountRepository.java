package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    long deleteAllByOwnerUserId(UUID ownerUserId);
    List<BankAccount> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    Optional<BankAccount> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
    boolean existsByOwnerUserIdAndVerificationStatusAndVerificationModeNot(
            UUID ownerUserId, String verificationStatus, String verificationMode);
    long deleteByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}

