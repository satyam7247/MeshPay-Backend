package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.AccountDeletionRequest;
import com.demo.upimesh.model.AccountDeletionRequestRepository;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class OfflinePaymentPolicy {
    private final AppUserRepository users;
    private final TransactionRepository transactions;
    private final AccountDeletionRequestRepository deletionRequests;

    public OfflinePaymentPolicy(AppUserRepository users, TransactionRepository transactions,
                                AccountDeletionRequestRepository deletionRequests) {
        this.users = users;
        this.transactions = transactions;
        this.deletionRequests = deletionRequests;
    }

    /** Called from the settlement transaction while the account row is locked. */
    public String rejectionReason(String senderVpa, BigDecimal amount) {
        AppUser user = users.findByUpiIdForUpdate(senderVpa).orElse(null);
        if (user == null) return null; // Preserve the isolated simulator accounts.
        if (user.isAccountLocked()) return "account_locked";

        BigDecimal limit = user.getOfflinePaymentLimit();
        if (limit.signum() <= 0) return "offline_payments_disabled";
        if (amount.compareTo(limit) > 0) return "offline_payment_limit_exceeded";

        BigDecimal used = transactions.sumSettledAmountSince(
                senderVpa, Transaction.Status.SETTLED, Instant.now().minusSeconds(24 * 60 * 60));
        if (used == null) used = BigDecimal.ZERO;
        if (used.add(amount).compareTo(limit) > 0) return "offline_spending_allowance_exceeded";
        return null;
    }

    public String receiverRejectionReason(String receiverVpa) {
        AppUser receiver = users.findByUpiId(receiverVpa).orElse(null);
        if (receiver == null) return null; // Simulator-only accounts have no app-user record.
        boolean pendingDeletion = deletionRequests.existsByUserIdAndStatus(
                receiver.getId(), AccountDeletionRequest.Status.PENDING_DELETION);
        boolean deleted = deletionRequests.existsByUserIdAndStatus(
                receiver.getId(), AccountDeletionRequest.Status.DELETED);
        return receiver.isAccountLocked() || pendingDeletion || deleted ? "receiver_unavailable" : null;
    }

    public Allowance allowance(AppUser user) {
        BigDecimal limit = user.getOfflinePaymentLimit();
        BigDecimal used = transactions.sumSettledAmountSince(
                user.getUpiId(), Transaction.Status.SETTLED, Instant.now().minusSeconds(24 * 60 * 60));
        if (used == null) used = BigDecimal.ZERO;
        BigDecimal remaining = limit.subtract(used).max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
        return new Allowance(limit, used.setScale(2, java.math.RoundingMode.HALF_UP), remaining);
    }

    public record Allowance(BigDecimal limit, BigDecimal usedLast24Hours, BigDecimal remainingLast24Hours) {}
}
