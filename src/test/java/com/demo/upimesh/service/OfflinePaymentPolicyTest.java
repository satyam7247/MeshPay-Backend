package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfflinePaymentPolicyTest {
    private AppUserRepository users;
    private TransactionRepository transactions;
    private OfflinePaymentPolicy policy;
    private AppUser user;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        transactions = mock(TransactionRepository.class);
        policy = new OfflinePaymentPolicy(users, transactions);
        user = new AppUser("Test User", "test@example.com", "+919876543210", "hash", "test@meshpay");
        user.updatePrivacySettings(new BigDecimal("500.00"), false, false);
        when(users.findByUpiIdForUpdate("test@meshpay")).thenReturn(Optional.of(user));
        when(transactions.sumSettledAmountSince(eq("test@meshpay"), eq(Transaction.Status.SETTLED), any(Instant.class)))
                .thenReturn(new BigDecimal("400.00"));
    }

    @Test
    void enforcesPerTransactionAndRollingTwentyFourHourAllowance() {
        assertNull(policy.rejectionReason("test@meshpay", new BigDecimal("100.00")));
        assertEquals("offline_spending_allowance_exceeded",
                policy.rejectionReason("test@meshpay", new BigDecimal("100.01")));
        assertEquals("offline_payment_limit_exceeded",
                policy.rejectionReason("test@meshpay", new BigDecimal("500.01")));
    }

    @Test
    void accountLockOverridesOfflineAllowance() {
        user.lockAccount();
        assertEquals("account_locked", policy.rejectionReason("test@meshpay", new BigDecimal("1.00")));
    }

    @Test
    void simulatorAccountsWithoutAnAppUserKeepTheirExistingDemoPolicy() {
        when(users.findByUpiIdForUpdate("alice@demo")).thenReturn(Optional.empty());
        assertNull(policy.rejectionReason("alice@demo", new BigDecimal("100000.00")));
    }
}
