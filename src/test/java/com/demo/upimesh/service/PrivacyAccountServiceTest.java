package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrivacyAccountServiceTest {
    private AppUserRepository users;
    private TransactionRepository transactions;
    private PasswordEncoder passwords;
    private com.demo.upimesh.model.AccountDeletionRequestRepository deletionRequests;
    private PrivacyAccountService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        transactions = mock(TransactionRepository.class);
        passwords = mock(PasswordEncoder.class);
        deletionRequests = mock(com.demo.upimesh.model.AccountDeletionRequestRepository.class);
        OfflinePaymentPolicy policy = new OfflinePaymentPolicy(users, transactions);
        service = new PrivacyAccountService(users, transactions, passwords, policy, deletionRequests);
        user = new AppUser("Test User", "test@example.com", "+919876543210", "hashed-password", "test@meshpay");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(deletionRequests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(eq(user.getId()), any())).thenReturn(Optional.empty());
        when(deletionRequests.findByUserIdAndStatusForUpdate(eq(user.getId()), any())).thenReturn(Optional.empty());
        when(transactions.sumSettledAmountSince(eq(user.getUpiId()), eq(Transaction.Status.SETTLED), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
    }

    @Test
    void persistsConfiguredLimitAndPrivacyFlagsOnTheAccountEntity() {
        var response = service.update(user, new PrivacyAccountService.UpdateRequest(
                new BigDecimal("750.50"), true, true));
        assertEquals(new BigDecimal("750.50"), response.offlinePaymentLimit());
        assertTrue(response.hideBalance());
        assertTrue(response.hideTransactionDetails());
        verify(users).findById(user.getId());
    }

    @Test
    void emergencyLockBlocksPaymentAndUnlockRequiresPasswordVerification() {
        assertTrue(service.lock(user, true).accountLocked());
        ResponseStatusException locked = assertThrows(ResponseStatusException.class,
                () -> service.requireUnlocked(user.getId()));
        assertEquals(423, locked.getStatusCode().value());

        when(passwords.matches("correct-password", "hashed-password")).thenReturn(true);
        assertFalse(service.unlock(user, "correct-password").accountLocked());
        service.requireUnlocked(user.getId());
    }

    @Test
    void hiddenTransactionDetailsAreRedactedBeforeLeavingTheServer() {
        user.updatePrivacySettings(BigDecimal.ZERO, false, true);
        Transaction tx = new Transaction();
        tx.setId(5L);tx.setPacketHash("packet-hash");tx.setSenderVpa(user.getUpiId());
        tx.setReceiverVpa("receiver@meshpay");tx.setAmount(new BigDecimal("45.00"));
        tx.setSignedAt(Instant.now());tx.setSettledAt(Instant.now());tx.setBridgeNodeId("bridge");
        tx.setStatus(Transaction.Status.SETTLED);
        when(transactions.findTop20ByStatusAndSenderVpaOrStatusAndReceiverVpaOrderByIdDesc(
                Transaction.Status.SETTLED, user.getUpiId(), Transaction.Status.SETTLED, user.getUpiId()))
                .thenReturn(List.of(tx));

        var result = service.transactions(user).get(0);
        assertTrue(result.detailsHidden());
        assertNull(result.senderVpa());
        assertNull(result.receiverVpa());
        assertNull(result.amount());
        assertNull(result.bridgeNodeId());
    }
}




