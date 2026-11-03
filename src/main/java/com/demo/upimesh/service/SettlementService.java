package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Where the actual ledger update happens. Wrapped in a DB transaction so either
 * BOTH the debit and credit happen, or neither does.
 *
 * The @Version column on Account gives us optimistic locking — if two threads
 * somehow get past idempotency and both try to debit the same account, the
 * second one will fail with OptimisticLockException rather than corrupting
 * the balance. (In a demo the idempotency layer should always catch this first,
 * but defense in depth.)
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);
    private static final int MAX_FAILED_PIN_ATTEMPTS = 5;
    private static final Duration PIN_LOCK_DURATION = Duration.ofMinutes(15);
    private static final Set<String> SEEDED_DEMO_VPAS = Set.of(
            "alice@demo", "bob@demo", "carol@demo", "dave@demo");

    @Value("${upi.mesh.demo.seed-accounts:false}")
    private boolean demoSeedEnabled;

    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;
    @Autowired private OfflinePaymentPolicy offlinePaymentPolicy;
    @Autowired private ApplicationEventPublisher events;

    @Transactional
    public Transaction settle(PaymentInstruction instruction, String packetHash,
                              String bridgeNodeId, int hopCount) {

        if (instruction.getSenderVpa() == null || instruction.getReceiverVpa() == null
                || instruction.getSenderVpa().isBlank() || instruction.getReceiverVpa().isBlank()) {
            throw new IllegalArgumentException("Sender and receiver VPAs are required");
        }
        if (instruction.getSenderVpa().equalsIgnoreCase(instruction.getReceiverVpa())) {
            log.warn("Rejected self-transfer for {}", instruction.getSenderVpa());
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount, "self_transfer");
        }

        if (instruction.getNonce() == null || instruction.getNonce().isBlank()
                || instruction.getNonce().length() > 64) {
            throw new IllegalArgumentException("A valid unique transaction ID is required.");
        }
        if (transactions.existsByTransactionNonce(instruction.getNonce())) {
            throw new DuplicateTransactionException();
        }

        Account sender = accounts.findByIdForUpdate(instruction.getSenderVpa())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown sender VPA: " + instruction.getSenderVpa()));

        BigDecimal amount = instruction.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
        }
        amount = amount.setScale(2);

        String policyRejection = offlinePaymentPolicy.rejectionReason(instruction.getSenderVpa(), amount);
        if (policyRejection != null) {
            log.warn("Offline payment rejected by account policy: {}", policyRejection);
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount, policyRejection);
        }

        String configuredPinHash = sender.getPinHash();
        boolean demoCredentialException = demoSeedEnabled
                && SEEDED_DEMO_VPAS.contains(sender.getVpa().toLowerCase(Locale.ROOT));
        if (configuredPinHash == null || configuredPinHash.isBlank()) {
            // TODO(sender-auth): evaluate device-bound signatures for offline sender authentication.
            if (!demoCredentialException) {
                return recordRejected(instruction, packetHash, bridgeNodeId, hopCount, "credential_not_configured");
            }
        } else {
            Instant now = Instant.now();
            if (sender.getPinLockedUntil() != null && sender.getPinLockedUntil().isAfter(now)) {
                // An active lock does not create another ledger row for each submitted guess.
                return rejectedWithoutPersistence(instruction, packetHash, bridgeNodeId, hopCount, "pin_attempts_locked");
            }
            boolean expiredLock = sender.getPinLockedUntil() != null;
            if (expiredLock) {
                sender.setPinLockedUntil(null);
                sender.setPinFailedAttempts(0);
            }
            if (!constantTimePinHashMatches(configuredPinHash, instruction.getPinHash())) {
                int failures = Math.min(sender.getPinFailedAttempts() + 1, MAX_FAILED_PIN_ATTEMPTS);
                sender.setPinFailedAttempts(failures);
                if (failures == MAX_FAILED_PIN_ATTEMPTS) {
                    sender.setPinLockedUntil(now.plus(PIN_LOCK_DURATION));
                }
                accounts.save(sender);
                log.warn("Offline payment credential verification failed for sender {}.", sender.getVpa());
                return recordRejected(instruction, packetHash, bridgeNodeId, hopCount, "invalid_credential");
            }
            if (sender.getPinFailedAttempts() != 0 || expiredLock) {
                sender.setPinFailedAttempts(0);
                sender.setPinLockedUntil(null);
                accounts.save(sender);
            }
        }

        String receiverRejection = offlinePaymentPolicy.receiverRejectionReason(instruction.getReceiverVpa());
        if (receiverRejection != null) {
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount, receiverRejection);
        }
        Account receiver = accounts.findById(instruction.getReceiverVpa()).orElse(null);
        if (receiver == null) {
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount, "receiver_unavailable");
        }

        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried to send ₹{}",
                    sender.getVpa(), sender.getBalance(), amount);
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount, "insufficient_balance");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setTransactionNonce(instruction.getNonce());
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(amount);
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.SETTLED);
        transactions.save(tx);
        events.publishEvent(new PaymentSettledEvent(tx.getId()));

        log.info("SETTLED ₹{} from {} to {} (packetHash={}, bridge={}, hops={})",
                amount, sender.getVpa(), receiver.getVpa(),
                packetHash.substring(0, 12) + "...", bridgeNodeId, hopCount);

        return tx;
    }

    private Transaction recordRejected(PaymentInstruction instruction, String packetHash,
                                       String bridgeNodeId, int hopCount, String reason) {
        return rejectedTransaction(instruction, packetHash, bridgeNodeId, hopCount, reason, true);
    }

    private Transaction rejectedWithoutPersistence(PaymentInstruction instruction, String packetHash,
                                                   String bridgeNodeId, int hopCount, String reason) {
        return rejectedTransaction(instruction, packetHash, bridgeNodeId, hopCount, reason, false);
    }

    private Transaction rejectedTransaction(PaymentInstruction instruction, String packetHash,
                                            String bridgeNodeId, int hopCount, String reason, boolean persist) {
        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setTransactionNonce(instruction.getNonce());
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(instruction.getAmount());
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.REJECTED);
        tx.setRejectionReason(reason);
        return persist ? transactions.save(tx) : tx;
    }

    private static boolean constantTimePinHashMatches(String expected, String candidate) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        byte[] candidateBytes = (candidate == null ? "" : candidate).getBytes(StandardCharsets.US_ASCII);
        boolean sameLength = expectedBytes.length == candidateBytes.length;
        boolean equal = MessageDigest.isEqual(expectedBytes, Arrays.copyOf(candidateBytes, expectedBytes.length));
        return sameLength && equal;
    }

    public static class DuplicateTransactionException extends RuntimeException {
        public DuplicateTransactionException() { super("transaction_replay"); }
    }
}
