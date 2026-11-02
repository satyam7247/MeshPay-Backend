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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** A synchronized client log only; it is never treated as a settled payment. */
@Entity
@Table(name = "offline_transaction_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_offline_bluetooth_txn_id", columnNames = "bluetooth_txn_id"),
        indexes = @Index(name = "idx_offline_logs_owner_time", columnList = "owner_user_id, local_timestamp"))
public class OfflineTransactionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "customer_upi_id", nullable = false, length = 80)
    private String customerUpiId;

    @Column(name = "merchant_upi_id", nullable = false, length = 255)
    private String merchantUpiId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "local_timestamp", nullable = false)
    private LocalDateTime localTimestamp;

    @Column(name = "bluetooth_txn_id", nullable = false, length = 128)
    private String bluetoothTxnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    private SyncStatus syncStatus;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected OfflineTransactionLog() {}

    public OfflineTransactionLog(UUID ownerUserId, String customerUpiId, String merchantUpiId,
                                 BigDecimal amount, LocalDateTime localTimestamp, String bluetoothTxnId) {
        this.ownerUserId = ownerUserId;
        this.customerUpiId = customerUpiId;
        this.merchantUpiId = merchantUpiId;
        this.amount = amount;
        this.localTimestamp = localTimestamp;
        this.bluetoothTxnId = bluetoothTxnId;
        this.syncStatus = SyncStatus.SYNCED;
        this.receivedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getCustomerUpiId() { return customerUpiId; }
    public String getMerchantUpiId() { return merchantUpiId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getLocalTimestamp() { return localTimestamp; }
    public String getBluetoothTxnId() { return bluetoothTxnId; }
    public SyncStatus getSyncStatus() { return syncStatus; }

    public enum SyncStatus { PENDING, SYNCED }
}