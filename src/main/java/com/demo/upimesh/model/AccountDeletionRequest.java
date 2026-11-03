package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_deletion_requests", indexes = {
        @Index(name = "idx_deletion_status_deadline", columnList = "status, scheduled_deletion_at"),
        @Index(name = "idx_deletion_user_created", columnList = "user_id, request_created_at")
})
public class AccountDeletionRequest {
    public enum Status { PENDING_DELETION, CANCELLED, DELETED }
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name="user_id", nullable=false) private UUID userId;
    @Column(name="request_created_at", nullable=false) private Instant requestCreatedAt;
    @Column(name="scheduled_deletion_at", nullable=false) private Instant scheduledDeletionAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24) private Status status;
    @Column(name="cancelled_at") private Instant cancelledAt;
    @Column(name="deleted_at") private Instant deletedAt;
    @Column(name="prior_account_locked", nullable=false) private boolean priorAccountLocked;
    @Column(name="deleted_email_key", length=64) private String deletedEmailKey;
    protected AccountDeletionRequest() {}
    public AccountDeletionRequest(UUID userId, Instant now, boolean priorAccountLocked, String emailKey) {
        this.userId=userId; this.requestCreatedAt=now; this.scheduledDeletionAt=now.plusSeconds(72*60*60L);
        this.status=Status.PENDING_DELETION; this.priorAccountLocked=priorAccountLocked; this.deletedEmailKey=emailKey;
    }
    public void cancel(Instant now) { status=Status.CANCELLED; cancelledAt=now; }
    public void markDeleted(Instant now) { status=Status.DELETED; deletedAt=now; }
    public UUID getId(){return id;} public UUID getUserId(){return userId;}
    public Instant getRequestCreatedAt(){return requestCreatedAt;} public Instant getScheduledDeletionAt(){return scheduledDeletionAt;}
    public Status getStatus(){return status;} public Instant getCancelledAt(){return cancelledAt;} public Instant getDeletedAt(){return deletedAt;}
    public boolean wasAccountLocked(){return priorAccountLocked;} public String getDeletedEmailKey(){return deletedEmailKey;}
    public void migrateDeletedEmailKey(String key){this.deletedEmailKey=key;}
}
