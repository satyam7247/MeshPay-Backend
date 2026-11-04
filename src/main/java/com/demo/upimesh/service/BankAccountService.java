package com.demo.upimesh.service;

import com.demo.upimesh.dto.BankAccountDtos.BankAccountResponse;
import com.demo.upimesh.dto.BankAccountDtos.LinkBankAccountRequest;
import com.demo.upimesh.dto.BankAccountDtos.VerifyBankAccountRequest;
import com.demo.upimesh.model.BankAccount;
import com.demo.upimesh.model.BankAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class BankAccountService {
    private static final Set<String> SUPPORTED_BANKS = Set.of(
            "State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank",
            "Punjab National Bank", "Bank of Baroda", "Kotak Mahindra Bank",
            "Canara Bank", "Union Bank of India", "Bank of India",
            "Indian Bank", "Indian Overseas Bank", "Central Bank of India",
            "UCO Bank", "IDBI Bank", "IDFC FIRST Bank", "Federal Bank",
            "IndusInd Bank", "Yes Bank", "RBL Bank", "South Indian Bank",
            "Bandhan Bank", "AU Small Finance Bank");

    private final BankAccountRepository accounts;
    private final BankOtpService otp;
    private final BankVerificationService verification;
    private final BankDataEncryptionService encryption;

    public BankAccountService(BankAccountRepository accounts, BankOtpService otp,
                              BankVerificationService verification,
                              BankDataEncryptionService encryption) {
        this.accounts = accounts;
        this.otp = otp;
        this.verification = verification;
        this.encryption = encryption;
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list(UUID userId) {
        return accounts.findAllByOwnerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BankAccountResponse link(UUID userId, LinkBankAccountRequest request) {
        verification.requireConfigured();
        String holder = request.accountHolderName().trim();
        String bank = supportedBank(request.bankName());
        if (!request.accountNumber().matches("[0-9]{9,18}")
                || !request.confirmAccountNumber().matches("[0-9]{9,18}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account numbers must contain 9 to 18 digits.");
        }
        if (!request.accountNumber().equals(request.confirmAccountNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account numbers do not match.");
        }
        if (holder.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account holder name is required.");
        }
        String fingerprint = encryption.fingerprint(request.accountNumber());
        if (accounts.existsByOwnerUserIdAndAccountFingerprint(userId, fingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This bank account is already linked.");
        }
        otp.consumeVerified(userId, request.challengeId());
        BankAccount account = accounts.save(new BankAccount(userId, bank, encryption.encrypt(holder),
                mask(request.accountNumber()), fingerprint));
        return toResponse(account);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public BankAccountResponse verify(UUID userId, VerifyBankAccountRequest request) {
        verification.requireConfigured();
        if (!request.accountNumber().matches("[0-9]{9,18}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account number must contain 9 to 18 digits.");
        }
        BankAccount account = accounts.findByIdAndOwnerUserIdForUpdate(request.accountId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bank account was not found."));
        if (!account.getVerificationStatus().equals("PENDING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This bank account has already been checked.");
        }
        if (account.getAccountFingerprint() == null || account.getAccountFingerprint().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Remove and link this legacy bank account again before verifying it.");
        }
        if (!account.recordVerifyAttempt(5)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification attempts. Remove and link the account again.");
        }
        String submittedFingerprint = encryption.fingerprint(request.accountNumber());
        if (!MessageDigest.isEqual(account.getAccountFingerprint().getBytes(StandardCharsets.US_ASCII),
                submittedFingerprint.getBytes(StandardCharsets.US_ASCII))) {
            accounts.saveAndFlush(account);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account number does not match the linked account.");
        }
        BankVerificationService.VerificationResult result;
        try {
            result = verification.verify(
                encryption.decrypt(account.getAccountHolderName()),
                request.accountNumber(), request.ifsc());
        } catch (ResponseStatusException failure) {
            accounts.saveAndFlush(account);
            throw failure;
        } catch (RuntimeException failure) {
            accounts.saveAndFlush(account);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Bank account verification is temporarily unavailable.");
        }
        account.updateVerificationResult(result.status(), result.verificationMode(),
                result.message(), result.providerReference(),
                result.verifiedAccountHolderName() == null ? null
                        : encryption.encrypt(result.verifiedAccountHolderName()),
                result.verifiedBankName());
        return toResponse(account);
    }

    @Transactional
    public void remove(UUID userId, UUID accountId) {
        if (accounts.deleteByIdAndOwnerUserId(accountId, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account was not found.");
        }
    }

    private static String mask(String accountNumber) {
        return "X".repeat(Math.max(0, accountNumber.length() - 4))
                + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String supportedBank(String requestedName) {
        String normalized = requestedName.trim();
        if (!normalized.matches("[A-Za-z0-9 .&()/-]{2,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter a valid supported bank name.");
        }
        return SUPPORTED_BANKS.stream()
                .filter(name -> name.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Enter a valid supported bank name."));
    }

    private BankAccountResponse toResponse(BankAccount account) {
        String holder = account.getAccountHolderName();
        if (encryption.isEncrypted(holder)) {
            holder = encryption.isConfigured() ? encryption.decrypt(holder) : "Linked account";
        }
        return new BankAccountResponse(account.getId(), account.getBankName(),
                holder, account.getMaskedAccountNumber(),
                account.getVerificationStatus(), account.getVerificationMode(),
                account.getVerificationMessage(), account.getProviderReference(),
                account.getCreatedAt(), account.getUpdatedAt());
    }
}
