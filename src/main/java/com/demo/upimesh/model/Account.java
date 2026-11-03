package com.demo.upimesh.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Simulated bank account. In a real system this would live in the bank's core,
 * not in our service. For the demo, we own the ledger.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private String vpa; // Virtual Payment Address, e.g. "alice@demo"

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /** SHA-256 hex of the simulator UPI PIN. Null means PIN is not checked. */
    @JsonIgnore
    @Column(length = 64)
    private String pinHash;

    @Column(name = "pin_failed_attempts", nullable = false, columnDefinition = "integer default 0")
    private int pinFailedAttempts;

    @Column(name = "pin_locked_until")
    private Instant pinLockedUntil;

    @Version  // Optimistic locking — prevents lost updates on concurrent transfers
    private Long version;

    public Account() {}

    public Account(String vpa, String holderName, BigDecimal balance) {
        this(vpa, holderName, balance, null);
    }

    public Account(String vpa, String holderName, BigDecimal balance, String pinHash) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.balance = balance;
        this.pinHash = pinHash;
    }

    public String getVpa() { return vpa; }
    public void setVpa(String vpa) { this.vpa = vpa; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public int getPinFailedAttempts() { return pinFailedAttempts; }
    public void setPinFailedAttempts(int attempts) { this.pinFailedAttempts = Math.max(0, attempts); }
    public Instant getPinLockedUntil() { return pinLockedUntil; }
    public void setPinLockedUntil(Instant lockedUntil) { this.pinLockedUntil = lockedUntil; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
