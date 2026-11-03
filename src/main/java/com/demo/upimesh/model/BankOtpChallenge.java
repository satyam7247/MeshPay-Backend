package com.demo.upimesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_otp_challenges", indexes = {
        @Index(name = "idx_bank_otp_owner_created", columnList = "owner_user_id, created_at")
})
public class BankOtpChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_after", nullable = false)
    private Instant resendAfter;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "otp_hash", length = 64)
    private String otpHash;

    @Column(name = "send_count", nullable = false, columnDefinition = "integer default 1")
    private int sendCount = 1;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(nullable = false)
    private boolean consumed;

    @Version
    private long version;

    protected BankOtpChallenge() {}

    public BankOtpChallenge(UUID ownerUserId, Instant now, Instant expiresAt, Instant resendAfter) {
        this.ownerUserId = ownerUserId;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.resendAfter = resendAfter;
    }

    public void resend(Instant now, Instant newExpiresAt, Instant newResendAfter) {
        this.expiresAt = newExpiresAt;
        this.resendAfter = newResendAfter;
        this.verified = false;
        this.verifiedAt = null;
        this.sendCount++;
    }

    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public void recordFailedAttempt() {
        this.attempts++;
    }

    public void markVerified() { markVerified(Instant.now()); }

    public void markVerified(Instant at) {
        this.verified = true;
        this.verifiedAt = at;
        this.otpHash = null;
    }

    public void consume() {
        this.consumed = true;
    }

    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getResendAfter() { return resendAfter; }
    public int getAttempts() { return attempts; }
    public String getOtpHash() { return otpHash; }
    public int getSendCount() { return sendCount; }
    public boolean isVerified() { return verified; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public boolean isConsumed() { return consumed; }
}
