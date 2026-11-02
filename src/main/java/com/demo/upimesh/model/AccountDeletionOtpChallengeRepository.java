package com.demo.upimesh.model;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface AccountDeletionOtpChallengeRepository extends JpaRepository<AccountDeletionOtpChallenge,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from AccountDeletionOtpChallenge c where c.id=:id and c.userId=:userId")
    Optional<AccountDeletionOtpChallenge> findOwnedForUpdate(@Param("id") UUID id,@Param("userId") UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from AccountDeletionOtpChallenge c where c.userId=:userId and c.consumed=false order by c.createdAt desc")
    List<AccountDeletionOtpChallenge> findOpenForUpdate(@Param("userId") UUID userId);
    long deleteAllByUserId(UUID userId);
}
