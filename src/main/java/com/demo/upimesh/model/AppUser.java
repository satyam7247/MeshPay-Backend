package com.demo.upimesh.model;

import com.demo.upimesh.service.MobileNumberNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "app_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_app_users_phone", columnNames = "phone"),
        @UniqueConstraint(name = "uk_app_users_upi_id", columnNames = "upi_id")
}, indexes = @Index(name = "idx_app_users_phone_normalized", columnList = "phone_normalized"))
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 18)
    private String phone;

    @Column(name = "phone_normalized", length = 18)
    private String phoneNormalized;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "upi_id", nullable = false, length = 80)
    private String upiId;

    @Column(name = "recipient_discoverable", nullable = false, columnDefinition = "boolean default false")
    private Boolean recipientDiscoverable = Boolean.FALSE;

    @Column(name = "recipient_version", nullable = false, columnDefinition = "integer default 1")
    private Integer recipientVersion = 1;

    @Column(name = "recipient_updated_at")
    private Instant recipientUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "offline_payment_limit", nullable = false, precision = 12, scale = 2,
            columnDefinition = "decimal(12,2) default 0.00")
    private BigDecimal offlinePaymentLimit = BigDecimal.ZERO;

    @Column(name = "hide_balance", nullable = false, columnDefinition = "boolean default false")
    private Boolean hideBalance = Boolean.FALSE;

    @Column(name = "hide_transaction_details", nullable = false, columnDefinition = "boolean default false")
    private Boolean hideTransactionDetails = Boolean.FALSE;

    @Column(name = "account_locked", nullable = false, columnDefinition = "boolean default false")
    private Boolean accountLocked = Boolean.FALSE;

    @Column(name = "account_locked_at")
    private Instant accountLockedAt;

    protected AppUser() {}

    public AppUser(String name, String email, String phone, String passwordHash, String upiId) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.phoneNormalized = MobileNumberNormalizer.normalizeStored(phone);
        this.passwordHash = passwordHash;
        this.upiId = upiId;
        this.recipientDiscoverable = Boolean.FALSE;
        this.recipientVersion = 1;
        this.recipientUpdatedAt = Instant.now();
        this.createdAt = Instant.now();
        this.offlinePaymentLimit = BigDecimal.ZERO.setScale(2);
        this.hideBalance = Boolean.FALSE;
        this.hideTransactionDetails = Boolean.FALSE;
        this.accountLocked = Boolean.FALSE;
    }

    public void setRecipientDiscoverable(boolean discoverable) {
        if (Boolean.TRUE.equals(this.recipientDiscoverable) != discoverable) {
            this.recipientDiscoverable = discoverable;
            this.recipientVersion = (this.recipientVersion == null ? 0 : this.recipientVersion) + 1;
            this.recipientUpdatedAt = Instant.now();
        }
    }

    public void setPhoneNormalized(String phoneNormalized) { this.phoneNormalized = phoneNormalized; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPhoneNormalized() { return phoneNormalized; }
    public String getPasswordHash() { return passwordHash; }
    public void changePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getUpiId() { return upiId; }
    public boolean isRecipientDiscoverable() { return Boolean.TRUE.equals(recipientDiscoverable); }
    public int getRecipientVersion() { return recipientVersion == null ? 1 : recipientVersion; }
    public Instant getRecipientUpdatedAt() { return recipientUpdatedAt == null ? createdAt : recipientUpdatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getOfflinePaymentLimit() { return offlinePaymentLimit == null ? BigDecimal.ZERO.setScale(2) : offlinePaymentLimit; }
    public boolean isHideBalance() { return Boolean.TRUE.equals(hideBalance); }
    public boolean isHideTransactionDetails() { return Boolean.TRUE.equals(hideTransactionDetails); }
    public boolean isAccountLocked() { return Boolean.TRUE.equals(accountLocked); }
    public Instant getAccountLockedAt() { return accountLockedAt; }

    public void updatePrivacySettings(BigDecimal offlinePaymentLimit, boolean hideBalance,
                                      boolean hideTransactionDetails) {
        this.offlinePaymentLimit = offlinePaymentLimit;
        this.hideBalance = hideBalance;
        this.hideTransactionDetails = hideTransactionDetails;
    }

    public void lockAccount() {
        if (!isAccountLocked()) this.accountLockedAt = Instant.now();
        this.accountLocked = Boolean.TRUE;
    }

    public void unlockAccount() {
        this.accountLocked = Boolean.FALSE;
        this.accountLockedAt = null;
    }

    public void anonymize(String deletedName, String replacementEmail, String replacementPhone) {
        this.name = deletedName; this.email = replacementEmail; this.phone = replacementPhone;
        this.phoneNormalized = MobileNumberNormalizer.normalizeStored(replacementPhone);
        this.passwordHash = "!deleted!" + UUID.randomUUID();
        this.recipientDiscoverable = Boolean.FALSE; this.recipientVersion = (this.recipientVersion == null ? 0 : this.recipientVersion) + 1;
        this.recipientUpdatedAt = Instant.now(); this.offlinePaymentLimit = BigDecimal.ZERO.setScale(2);
        this.hideBalance = Boolean.FALSE; this.hideTransactionDetails = Boolean.FALSE; lockAccount();
    }
}

