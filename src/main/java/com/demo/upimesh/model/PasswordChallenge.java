package com.demo.upimesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="password_challenges", indexes={
        @Index(name="idx_password_challenge_lookup", columnList="email_key, purpose, created_at"),
        @Index(name="idx_password_challenge_expiry", columnList="expires_at")})
public class PasswordChallenge {
    public enum Purpose { FORGOT, CHANGE, APP_LOCK }
    @Id @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;
    @Column(name="user_id", length=36)
    private String userId;
    @Column(name="email_key", nullable=false, length=64)
    private String emailKey;
    @Column(name="requester_key", length=64)
    private String requesterKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=12, columnDefinition="varchar(12)")
    private Purpose purpose;
    @Column(name="otp_hash", length=100)
    private String otpHash;
    @Column(name="otp_expires_at")
    private Instant otpExpiresAt;
    @Column(name="reset_token_hash", length=64)
    private String resetTokenHash;
    @Column(name="reset_expires_at")
    private Instant resetExpiresAt;
    @Column(nullable=false)
    private int attempts;
    @Column(name="otp_consumed", nullable=false)
    private boolean otpConsumed;
    @Column(name="reset_consumed", nullable=false)
    private boolean resetConsumed;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    @Column(name="expires_at", nullable=false)
    private Instant expiresAt;

    protected PasswordChallenge() {}
    public PasswordChallenge(String userId, String emailKey, String requesterKey, Purpose purpose, String otpHash,
                             Instant otpExpiresAt, Instant createdAt) {
        this.userId=userId; this.emailKey=emailKey; this.requesterKey=requesterKey; this.purpose=purpose; this.otpHash=otpHash;
        this.otpExpiresAt=otpExpiresAt; this.createdAt=createdAt; this.expiresAt=otpExpiresAt;
    }
    public UUID getId(){return id;}
    public String getUserId(){return userId;}
    public String getEmailKey(){return emailKey;}
    public Purpose getPurpose(){return purpose;}
    public String getOtpHash(){return otpHash;}
    public Instant getOtpExpiresAt(){return otpExpiresAt;}
    public String getResetTokenHash(){return resetTokenHash;}
    public Instant getResetExpiresAt(){return resetExpiresAt;}
    public int getAttempts(){return attempts;}
    public boolean isOtpConsumed(){return otpConsumed;}
    public boolean isResetConsumed(){return resetConsumed;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;}
    public void recordWrongAttempt(){attempts++;}
    public void consumeOtp(String tokenHash, Instant until){otpConsumed=true; otpHash=null; resetTokenHash=tokenHash; resetExpiresAt=until; expiresAt=until;}
    public void consumeReset(){resetConsumed=true; resetTokenHash=null; expiresAt=Instant.now();}
    public void invalidate(){otpConsumed=true; resetConsumed=true; otpHash=null; resetTokenHash=null; expiresAt=Instant.now();}
}
