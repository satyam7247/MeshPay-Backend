package com.demo.upimesh.model;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordChallengeRepository extends JpaRepository<PasswordChallenge, UUID> {
    long deleteAllByUserId(String userId);
    Optional<PasswordChallenge> findFirstByEmailKeyAndPurposeOrderByCreatedAtDesc(String emailKey, PasswordChallenge.Purpose purpose);
    long countByRequesterKeyAndCreatedAtAfter(String requesterKey, Instant after);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordChallenge c where c.id = :id")
    Optional<PasswordChallenge> findByIdForUpdate(@Param("id") UUID id);
    long deleteAllByExpiresAtBefore(Instant now);
}




