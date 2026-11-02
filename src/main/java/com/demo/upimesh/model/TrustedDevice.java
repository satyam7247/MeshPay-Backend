package com.demo.upimesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trusted_devices",
        uniqueConstraints = @UniqueConstraint(name = "uk_trusted_device_owner_id", columnNames = {"owner_user_id", "device_id"}),
        indexes = @Index(name = "idx_trusted_device_owner_active", columnList = "owner_user_id,revoked_at"))
public class TrustedDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false, length = 36)
    private UUID ownerUserId;

    @Column(name = "device_id", nullable = false, length = 36)
    private String deviceId;

    @Column(name = "device_name", nullable = false, length = 80)
    private String deviceName;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(name = "verification_method", nullable = false, length = 32)
    private String verificationMethod = "PASSWORD_LOGIN";

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TrustedDevice() {}

    public TrustedDevice(UUID ownerUserId, String deviceId, String deviceName, String platform) {
        this.ownerUserId = ownerUserId;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.platform = platform;
        this.registeredAt = Instant.now();
        this.lastActiveAt = this.registeredAt;
    }

    public void updateLogin(String deviceName, String platform) {
        this.deviceName = deviceName;
        this.platform = platform;
        this.lastActiveAt = Instant.now();
        this.revokedAt = null;
        this.verificationMethod = "PASSWORD_LOGIN";
    }

    public void touch(Instant now) { this.lastActiveAt = now; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getPlatform() { return platform; }
    public String getVerificationMethod() { return verificationMethod; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isRevoked() { return revokedAt != null; }
}
