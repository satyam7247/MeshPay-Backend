package com.demo.upimesh.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserNotificationRepository extends JpaRepository<UserNotification,UUID> {
    Page<UserNotification> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId,Pageable pageable);
    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);
    Optional<UserNotification> findByIdAndRecipientUserId(UUID id,UUID recipientUserId);
    Optional<UserNotification> findByTransactionId(Long transactionId);
    @Modifying
    @Query("update UserNotification n set n.readAt=:readAt where n.recipientUserId=:owner and n.readAt is null")
    int markAllRead(@Param("owner") UUID owner,@Param("readAt") Instant readAt);
}