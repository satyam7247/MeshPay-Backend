package com.demo.upimesh.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "linked_bank_accounts", indexes = {
        @Index(name = "idx_linked_bank_accounts_owner", columnList = "owner_user_id")
})
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_holder_name", nullable = false, length = 512)
    private String accountHolderName;

    @Column(name = "masked_account_number", nullable = false, length = 20)
    private String maskedAccountNumber;

    @Column(name = "verification_status", nullable = false, length = 16)
    private String verificationStatus;

    @Column(name = "verification_mode", nullable = false, length = 24)
    private String verificationMode;

    @Column(name = "verification_message", length = 300)
    private String verificationMessage;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankAccount() {}

    public BankAccount(UUID ownerUserId, String bankName, String accountHolderName,
                       String maskedAccountNumber) {
        this.ownerUserId = ownerUserId;
        this.bankName = bankName;
        this.accountHolderName = accountHolderName;
        this.maskedAccountNumber = maskedAccountNumber;
        this.verificationStatus = "PENDING";
        this.verificationMode = "PROVIDER_PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateVerificationResult(String status, String mode, String message,
                                         String reference, String verifiedHolderName,
                                         String verifiedBankName) {
        this.verificationStatus = status;
        this.verificationMode = mode;
        this.verificationMessage = message;
        this.providerReference = reference;
        if ("VERIFIED".equals(status) && verifiedHolderName != null && !verifiedHolderName.isBlank()) {
            this.accountHolderName = verifiedHolderName;
        }
        if ("VERIFIED".equals(status) && verifiedBankName != null && !verifiedBankName.isBlank()) {
            this.bankName = verifiedBankName;
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getBankName() { return bankName; }
    public String getAccountHolderName() { return accountHolderName; }
    public String getMaskedAccountNumber() { return maskedAccountNumber; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getVerificationMode() { return verificationMode; }
    public String getVerificationMessage() { return verificationMessage; }
    public String getProviderReference() { return providerReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
