package com.demo.upimesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_sessions")
public class AppSession {
    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    protected AppSession() {}

    public AppSession(String tokenHash, String userId, Instant expiresAt) {
        this(tokenHash, userId, expiresAt, null);
    }

    public AppSession(String tokenHash, String userId, Instant expiresAt, String deviceId) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.deviceId = deviceId;
    }

    public String getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getDeviceId() { return deviceId; }
}
