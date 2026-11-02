package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_push_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_push_token", columnNames = "expo_push_token"),
        indexes = @Index(name = "idx_push_token_owner", columnList = "owner_user_id, enabled"))
public class NotificationPushToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "owner_user_id", nullable = false) private UUID ownerUserId;
    @Column(name = "expo_push_token", nullable = false, unique = true, length = 255) private String expoPushToken;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "sound_enabled", nullable = false) private boolean soundEnabled;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected NotificationPushToken() {}
    public NotificationPushToken(UUID ownerUserId,String token,boolean enabled,boolean soundEnabled) {
        this.ownerUserId=ownerUserId; this.expoPushToken=token; this.enabled=enabled;
        this.soundEnabled=soundEnabled; this.updatedAt=Instant.now();
    }
    public void update(UUID ownerUserId,boolean enabled,boolean soundEnabled) {
        this.ownerUserId=ownerUserId; this.enabled=enabled; this.soundEnabled=soundEnabled; this.updatedAt=Instant.now();
    }
    public UUID getId(){return id;} public UUID getOwnerUserId(){return ownerUserId;}
    public String getExpoPushToken(){return expoPushToken;} public boolean isEnabled(){return enabled;}
    public boolean isSoundEnabled(){return soundEnabled;}
}