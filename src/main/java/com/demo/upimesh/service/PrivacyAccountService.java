package com.demo.upimesh.service;

import com.demo.upimesh.model.AccountDeletionRequest;
import com.demo.upimesh.model.AccountDeletionRequestRepository;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PrivacyAccountService {
    private static final BigDecimal MAX_OFFLINE_LIMIT = new BigDecimal("50000.00");
    private final AppUserRepository users;
    private final TransactionRepository transactions;
    private final PasswordEncoder passwords;
    private final OfflinePaymentPolicy offlinePaymentPolicy;
    private final AccountDeletionRequestRepository deletionRequests;
    private final AttemptRateLimiter attempts;

    public PrivacyAccountService(AppUserRepository users, TransactionRepository transactions,
                                 PasswordEncoder passwords, OfflinePaymentPolicy offlinePaymentPolicy,
                                 AccountDeletionRequestRepository deletionRequests) {
        this(users, transactions, passwords, offlinePaymentPolicy, deletionRequests, new AttemptRateLimiter());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PrivacyAccountService(AppUserRepository users, TransactionRepository transactions,
                                 PasswordEncoder passwords, OfflinePaymentPolicy offlinePaymentPolicy,
                                 AccountDeletionRequestRepository deletionRequests, AttemptRateLimiter attempts) {
        this.users = users;
        this.transactions = transactions;
        this.passwords = passwords;
        this.offlinePaymentPolicy = offlinePaymentPolicy;
        this.deletionRequests = deletionRequests;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public PrivacyResponse get(AppUser user) {
        OfflinePaymentPolicy.Allowance allowance = offlinePaymentPolicy.allowance(user);
        return new PrivacyResponse(user.getOfflinePaymentLimit(), allowance.usedLast24Hours(),
                allowance.remainingLast24Hours(), user.isHideBalance(), user.isHideTransactionDetails(),
                user.isAccountLocked(), user.getAccountLockedAt());
    }

    @Transactional
    public PrivacyResponse update(AppUser principal, UpdateRequest request) {
        return update(principal, request, "unknown");
    }

    @Transactional
    public PrivacyResponse update(AppUser principal, UpdateRequest request, String remoteAddress) {
        BigDecimal limit = request.offlinePaymentLimit();
        if (limit == null || limit.scale() > 2 || limit.signum() < 0 || limit.compareTo(MAX_OFFLINE_LIMIT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Offline limit must be between INR 0 and INR 50,000 with at most two decimal places.");
        }
        AppUser user = users.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account was not found."));
        if (limit.compareTo(user.getOfflinePaymentLimit()) > 0) {
            String userKey = "privacy-limit:user:" + user.getId();
            String ipKey = "privacy-limit:ip:" + (remoteAddress == null ? "unknown" : remoteAddress);
            attempts.check(userKey, 5, 900);
            attempts.check(ipKey, 5, 900);
            if (request.password()==null || !passwords.matches(request.password(), user.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password verification failed.");
            }
            attempts.clear(userKey);
            attempts.clear(ipKey);
        }
        user.updatePrivacySettings(limit.setScale(2), request.hideBalance(), request.hideTransactionDetails());
        return get(user);
    }

    @Transactional
    public PrivacyResponse lock(AppUser principal, boolean confirmed) {
        if (!confirmed) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirm the emergency lock first.");
        AppUser user = users.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account was not found."));
        user.lockAccount();
        return get(user);
    }

    @Transactional
    public PrivacyResponse unlock(AppUser principal, String password) { return unlock(principal,password,"unknown"); }

    @Transactional
    public PrivacyResponse unlock(AppUser principal, String password, String remoteAddress) {
        if (deletionRequests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(
                principal.getId(), AccountDeletionRequest.Status.PENDING_DELETION).isPresent()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "Cancel the pending deletion by signing in again before unlocking payments.");
        }
        AppUser user = users.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account was not found."));
        attempts.check("unlock:user:"+user.getId(),5,900);
        attempts.check("unlock:ip:"+(remoteAddress==null?"unknown":remoteAddress),5,900);
        if (password == null || !passwords.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password verification failed.");
        }
        attempts.clear("unlock:user:"+user.getId());
        attempts.clear("unlock:ip:"+(remoteAddress==null?"unknown":remoteAddress));
        user.unlockAccount();
        return get(user);
    }

    @Transactional(readOnly = true)
    public void requireUnlocked(UUID userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account was not found."));
        if (user.isAccountLocked()) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "This account is locked. Unlock it before making payments.");
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionView> transactions(AppUser user) {
        boolean hideDetails = user.isHideTransactionDetails();
        return transactions.findTop20ByStatusAndSenderVpaOrStatusAndReceiverVpaOrderByIdDesc(
                        Transaction.Status.SETTLED, user.getUpiId(),
                        Transaction.Status.SETTLED, user.getUpiId())
                .stream()
                .map(transaction -> toView(transaction, user.getUpiId(), hideDetails))
                .toList();
    }

    private TransactionView toView(Transaction transaction, String upiId, boolean hideDetails) {
        boolean sent = upiId.equals(transaction.getSenderVpa());
        return new TransactionView(transaction.getId(),
                hideDetails ? null : transaction.getSenderVpa(),
                hideDetails ? null : transaction.getReceiverVpa(),
                hideDetails ? null : transaction.getAmount(),
                transaction.getSignedAt(), transaction.getSettledAt(),
                hideDetails ? null : transaction.getBridgeNodeId(),
                transaction.getStatus().name(), sent ? "SENT" : "RECEIVED", hideDetails);
    }

    @Transactional(readOnly = true)
    public SyncAvailability syncAvailability() {
        return new SyncAvailability(false,
                "Native Bluetooth mesh and approved payment settlement are not configured in this build.");
    }

    public record UpdateRequest(
            @NotNull @Digits(integer = 5, fraction = 2) @DecimalMax("50000.00") BigDecimal offlinePaymentLimit,
            @NotNull Boolean hideBalance,
            @NotNull Boolean hideTransactionDetails, String password) {
        public UpdateRequest(BigDecimal offlinePaymentLimit, Boolean hideBalance, Boolean hideTransactionDetails) {
            this(offlinePaymentLimit, hideBalance, hideTransactionDetails, null);
        }
    }

    public record PrivacyResponse(BigDecimal offlinePaymentLimit, BigDecimal offlineSpentLast24Hours,
                                  BigDecimal remainingOfflineAllowance, boolean hideBalance,
                                  boolean hideTransactionDetails, boolean accountLocked,
                                  Instant accountLockedAt) {}

    public record TransactionView(Long id, String senderVpa, String receiverVpa, BigDecimal amount,
                                  Instant signedAt, Instant settledAt, String bridgeNodeId, String status,
                                  String direction, boolean detailsHidden) {}

    public record SyncAvailability(boolean available, String reason) {}
}
