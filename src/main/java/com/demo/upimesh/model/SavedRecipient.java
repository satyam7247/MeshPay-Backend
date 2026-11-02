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
@Table(name = "saved_recipients",
        uniqueConstraints = @UniqueConstraint(name = "uk_saved_recipient_owner_target", columnNames = {"owner_user_id", "recipient_user_id"}),
        indexes = @Index(name = "idx_saved_recipients_owner", columnList = "owner_user_id"))
public class SavedRecipient {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected SavedRecipient() {}

    public SavedRecipient(UUID ownerUserId, UUID recipientUserId) {
        this.ownerUserId = ownerUserId;
        this.recipientUserId = recipientUserId;
        this.savedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public Instant getSavedAt() { return savedAt; }
}
