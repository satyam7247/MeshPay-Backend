package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_transaction", columnNames = "transaction_id"),
        indexes = @Index(name = "idx_notification_owner_created", columnList = "recipient_user_id, created_at"))
public class UserNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;
    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;
    @Column(name = "sender_name", nullable = false, length = 80)
    private String senderName;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "payment_status", nullable = false, length = 16)
    private String paymentStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;

    protected UserNotification() {}
    public UserNotification(UUID recipientUserId, Long transactionId, String senderName,
                            BigDecimal amount, String paymentStatus, Instant createdAt) {
        this.recipientUserId=recipientUserId; this.transactionId=transactionId; this.senderName=senderName;
        this.amount=amount; this.paymentStatus=paymentStatus; this.createdAt=createdAt;
    }
    public UUID getId(){return id;} public UUID getRecipientUserId(){return recipientUserId;}
    public Long getTransactionId(){return transactionId;} public String getSenderName(){return senderName;}
    public BigDecimal getAmount(){return amount;} public String getPaymentStatus(){return paymentStatus;}
    public Instant getCreatedAt(){return createdAt;} public Instant getReadAt(){return readAt;}
    public boolean isRead(){return readAt!=null;} public void markRead(Instant when){if(readAt==null)readAt=when;}
}