package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPushTokenRepository extends JpaRepository<NotificationPushToken,UUID> {
    Optional<NotificationPushToken> findByExpoPushToken(String expoPushToken);
    List<NotificationPushToken> findAllByOwnerUserIdAndEnabledTrue(UUID ownerUserId);
    long deleteAllByOwnerUserId(UUID ownerUserId);
}