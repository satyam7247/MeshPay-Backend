package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SettlementServiceTest {
    private AccountRepository accounts;
    private TransactionRepository transactions;
    private OfflinePaymentPolicy policy;
    private SettlementService settlement;
    private Account sender;
    private Account receiver;

    @BeforeEach void setUp() {
        accounts = mock(AccountRepository.class);
        transactions = mock(TransactionRepository.class);
        policy = mock(OfflinePaymentPolicy.class);
        settlement = new SettlementService();
        ReflectionTestUtils.setField(settlement, "accounts", accounts);
        ReflectionTestUtils.setField(settlement, "transactions", transactions);
        ReflectionTestUtils.setField(settlement, "offlinePaymentPolicy", policy);
        ReflectionTestUtils.setField(settlement, "events", mock(ApplicationEventPublisher.class));
        sender = new Account("sender@meshpay", "Sender", new BigDecimal("100.00"), "a".repeat(64));
        receiver = new Account("receiver@meshpay", "Receiver", new BigDecimal("10.00"));
        when(accounts.findByIdForUpdate(sender.getVpa())).thenReturn(Optional.of(sender));
        when(accounts.findById(receiver.getVpa())).thenReturn(Optional.of(receiver));
        when(policy.rejectionReason(eq(sender.getVpa()), any(BigDecimal.class))).thenReturn(null);
        when(policy.receiverRejectionReason(receiver.getVpa())).thenReturn(null);
        when(transactions.existsByTransactionNonce(anyString())).thenReturn(false);
        when(transactions.save(any(Transaction.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test void missingCredentialIsRejectedWithExplicitReason() {
        sender.setPinHash(null);
        Transaction result = settle(sender.getVpa(), receiver.getVpa(), null);
        assertEquals(Transaction.Status.REJECTED, result.getStatus());
        assertEquals("credential_not_configured", result.getRejectionReason());
    }

    @Test void fifthBadCredentialLocksSenderAndLockedGuessesDoNotCreateRows() {
        for (int i = 0; i < 5; i++) {
            assertEquals("invalid_credential", settle(sender.getVpa(), receiver.getVpa(), "b".repeat(64)).getRejectionReason());
        }
        assertEquals(5, sender.getPinFailedAttempts());
        assertTrue(sender.getPinLockedUntil().isAfter(Instant.now()));
        assertEquals("pin_attempts_locked", settle(sender.getVpa(), receiver.getVpa(), "b".repeat(64)).getRejectionReason());
        verify(transactions, times(5)).save(any(Transaction.class));
    }

    @Test void correctCredentialAfterExpiredLockClearsFailureState() {
        sender.setPinFailedAttempts(4);
        sender.setPinLockedUntil(Instant.now().minusSeconds(1));
        Transaction result = settle(sender.getVpa(), receiver.getVpa(), "a".repeat(64));
        assertEquals(Transaction.Status.SETTLED, result.getStatus());
        assertEquals(0, sender.getPinFailedAttempts());
        assertNull(sender.getPinLockedUntil());
    }

    @Test void missingCredentialExceptionRequiresExplicitDemoModeAndSeededVpa() {
        Account demo = new Account("alice@demo", "Alice", new BigDecimal("100.00"));
        ReflectionTestUtils.setField(settlement, "demoSeedEnabled", true);
        when(accounts.findByIdForUpdate("alice@demo")).thenReturn(Optional.of(demo));
        when(policy.rejectionReason(eq("alice@demo"), any(BigDecimal.class))).thenReturn(null);
        assertEquals(Transaction.Status.SETTLED, settle("alice@demo", receiver.getVpa(), null).getStatus());
    }

    @Test void unavailableReceiverIsRejectedWithExplicitReason() {
        when(policy.receiverRejectionReason(receiver.getVpa())).thenReturn("receiver_unavailable");
        Transaction result = settle(sender.getVpa(), receiver.getVpa(), "a".repeat(64));
        assertEquals(Transaction.Status.REJECTED, result.getStatus());
        assertEquals("receiver_unavailable", result.getRejectionReason());
    }

    private Transaction settle(String senderVpa, String receiverVpa, String pinHash) {
        PaymentInstruction instruction = new PaymentInstruction(senderVpa, receiverVpa,
                new BigDecimal("10.00"), pinHash, UUID.randomUUID().toString(), Instant.now().toEpochMilli());
        return settlement.settle(instruction, UUID.randomUUID().toString().replace("-", ""), "test-bridge", 1);
    }
}
