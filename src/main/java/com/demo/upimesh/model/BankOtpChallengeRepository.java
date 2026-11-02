package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface BankOtpChallengeRepository extends JpaRepository<BankOtpChallenge, UUID> {
    long deleteAllByOwnerUserId(UUID ownerUserId);
    long countByOwnerUserIdAndCreatedAtAfter(UUID ownerUserId, Instant after);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select challenge from BankOtpChallenge challenge
            where challenge.ownerUserId = :ownerUserId and challenge.consumed = false
            order by challenge.createdAt desc
            """)
    java.util.List<BankOtpChallenge> findOpenChallengesForUpdate(
            @Param("ownerUserId") UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BankOtpChallenge> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);
}




