package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="account_deletion_otp_challenges", indexes={
        @Index(name="idx_delete_otp_user_open", columnList="user_id, consumed, created_at")
})
public class AccountDeletionOtpChallenge {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="user_id", nullable=false) private UUID userId;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="resend_after", nullable=false) private Instant resendAfter;
    @Column(nullable=false) private int attempts;
    @Column(nullable=false) private boolean consumed;
    @Column(nullable=false) private boolean verified;
    @Column(name="otp_hash", length=64) private String otpHash;
    @Version private long version;
    protected AccountDeletionOtpChallenge(){}
    public AccountDeletionOtpChallenge(UUID userId, Instant now) { this.userId=userId; reset(now); }
    public void reset(Instant now){createdAt=now; expiresAt=now.plusSeconds(60); resendAfter=now.plusSeconds(60); attempts=0; consumed=false; verified=false; otpHash=null;}
    public void setOtpHash(String otpHash){this.otpHash=otpHash;}
    public void failed(){attempts++;}
    public void verify(){verified=true; otpHash=null;}
    public void consume(){consumed=true; otpHash=null;}
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;} public Instant getResendAfter(){return resendAfter;}
    public int getAttempts(){return attempts;} public boolean isConsumed(){return consumed;} public boolean isVerified(){return verified;} public String getOtpHash(){return otpHash;}
}
