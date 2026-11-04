package com.demo.upimesh.service;

import com.demo.upimesh.dto.BankAccountDtos.LinkBankAccountRequest;
import com.demo.upimesh.dto.BankAccountDtos.VerifyBankAccountRequest;
import com.demo.upimesh.model.BankAccount;
import com.demo.upimesh.model.BankAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankAccountServiceTest {
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void doesNotPersistAccountDetailsWhenNoAuthorizedVerificationProviderIsConfigured() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankOtpService otp = mock(BankOtpService.class);
        BankDataEncryptionService encryption = encryption();
        BankAccountService service = service(repository, otp, encryption);
        UUID ownerId = UUID.randomUUID();

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.link(ownerId,
                new LinkBankAccountRequest(UUID.randomUUID(), "HDFC Bank", "Example User",
                        "1234567890123456", "1234567890123456")));

        assertEquals(503, error.getStatusCode().value());
        verify(otp, never()).consumeVerified(any(), any());
        verify(repository, never()).save(any(BankAccount.class));
    }

    @Test
    void doesNotProduceAProviderVerificationStatusWithoutAProvider() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankDataEncryptionService encryption = encryption();
        BankAccountService service = service(repository, mock(BankOtpService.class), encryption);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.verify(
                UUID.randomUUID(),
                new VerifyBankAccountRequest(UUID.randomUUID(), "1234567890123456", "HDFC0000240")));

        assertEquals(503, error.getStatusCode().value());
        verify(repository, never()).save(any(BankAccount.class));
    }

    @Test
    void listsLegacySimulatedAccountsAlongsideEncryptedAccounts() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankDataEncryptionService encryption = encryption();
        BankAccountService service = service(repository, mock(BankOtpService.class), encryption);
        UUID ownerId = UUID.randomUUID();
        BankAccount mockAccount = new BankAccount(ownerId, "HDFC Bank", "Example User", "XXXXXXXX3456");
        ReflectionTestUtils.setField(mockAccount, "verificationMode", "MOCK_SIMULATED");
        BankAccount pendingAccount = new BankAccount(ownerId, "State Bank of India",
                encryption.encrypt("Example User"), "XXXXXXXX9876");
        when(repository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerId))
                .thenReturn(List.of(mockAccount, pendingAccount));

        var result = service.list(ownerId);

        assertEquals(2, result.size());
        assertEquals("MOCK_SIMULATED", result.get(0).verificationMode());
        assertEquals("Example User", result.get(0).accountHolderName());
        assertEquals("PROVIDER_PENDING", result.get(1).verificationMode());
        assertEquals("XXXXXXXX9876", result.get(1).maskedAccountNumber());
        assertEquals("Example User", result.get(1).accountHolderName());
    }

    @Test
    void rejectsUnsupportedBankNames() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankOtpService otp = mock(BankOtpService.class);
        BankDataEncryptionService encryption = encryption();
        BankVerificationService verification = mock(BankVerificationService.class);
        BankAccountService service = new BankAccountService(repository, otp, verification, encryption);
        UUID ownerId = UUID.randomUUID();

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.link(ownerId,
                new LinkBankAccountRequest(UUID.randomUUID(), "Not A Real Bank", "Example User",
                        "1234567890123456", "1234567890123456")));

        assertEquals(400, error.getStatusCode().value());
        verify(repository, never()).save(any(BankAccount.class));
    }

    @Test
    void removesAccountsOnlyForTheOwningUser() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankAccountService service = service(repository, mock(BankOtpService.class), encryption());
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(repository.deleteByIdAndOwnerUserId(accountId, ownerId)).thenReturn(1L);
        service.remove(ownerId, accountId);

        verify(repository).deleteByIdAndOwnerUserId(accountId, ownerId);
    }

    @Test
    void rejectsAFullAccountNumberThatDoesNotMatchItsFingerprint() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankDataEncryptionService encryption = encryption();
        BankVerificationService verification = mock(BankVerificationService.class);
        BankAccountService service = new BankAccountService(repository, mock(BankOtpService.class), verification, encryption);
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BankAccount account = new BankAccount(ownerId, "HDFC Bank", encryption.encrypt("Example User"),
                "XXXXXXXX3456", encryption.fingerprint("1234567890123456"));
        when(repository.findByIdAndOwnerUserIdForUpdate(accountId, ownerId)).thenReturn(Optional.of(account));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.verify(ownerId,
                new VerifyBankAccountRequest(accountId, "9876543210987654", "HDFC0000240")));

        assertEquals(400, error.getStatusCode().value());
        assertEquals(1, account.getVerifyAttempts());
        verify(repository).saveAndFlush(account);
        verify(verification, never()).verify(anyString(), anyString(), anyString());
    }

    @Test
    void persistsTheAttemptWhenTheProviderCallFails() throws Exception {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankDataEncryptionService encryption = encryption();
        BankVerificationService verification = mock(BankVerificationService.class);
        BankAccountService service = new BankAccountService(repository, mock(BankOtpService.class), verification, encryption);
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BankAccount account = new BankAccount(ownerId, "HDFC Bank", encryption.encrypt("Example User"),
                "XXXXXXXX3456", encryption.fingerprint("1234567890123456"));
        when(repository.findByIdAndOwnerUserIdForUpdate(accountId, ownerId)).thenReturn(Optional.of(account));
        when(verification.verify(anyString(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Provider unavailable."));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.verify(ownerId,
                new VerifyBankAccountRequest(accountId, "1234567890123456", "HDFC0000240")));

        assertEquals(503, error.getStatusCode().value());
        assertEquals(1, account.getVerifyAttempts());
        verify(repository).saveAndFlush(account);
        Transactional transaction = BankAccountService.class
                .getMethod("verify", UUID.class, VerifyBankAccountRequest.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertTrue(List.of(transaction.noRollbackFor()).contains(ResponseStatusException.class));
    }

    @Test
    void blocksTheSixthVerificationAttempt() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankDataEncryptionService encryption = encryption();
        BankVerificationService verification = mock(BankVerificationService.class);
        BankAccountService service = new BankAccountService(repository, mock(BankOtpService.class), verification, encryption);
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BankAccount account = new BankAccount(ownerId, "HDFC Bank", encryption.encrypt("Example User"),
                "XXXXXXXX3456", encryption.fingerprint("1234567890123456"));
        when(repository.findByIdAndOwnerUserIdForUpdate(accountId, ownerId)).thenReturn(Optional.of(account));
        VerifyBankAccountRequest wrong = new VerifyBankAccountRequest(accountId, "9876543210987654", "HDFC0000240");
        for (int i = 0; i < 5; i++) assertThrows(ResponseStatusException.class, () -> service.verify(ownerId, wrong));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.verify(ownerId, wrong));

        assertEquals(429, error.getStatusCode().value());
        assertEquals(5, account.getVerifyAttempts());
        verify(verification, never()).verify(anyString(), anyString(), anyString());
    }

    @Test
    void requiresLegacyFingerprintAccountsToBeRemovedAndLinkedAgain() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankDataEncryptionService encryption = encryption();
        BankVerificationService verification = mock(BankVerificationService.class);
        BankAccountService service = new BankAccountService(repository, mock(BankOtpService.class), verification, encryption);
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BankAccount account = new BankAccount(ownerId, "HDFC Bank", "Example User", "XXXXXXXX3456");
        when(repository.findByIdAndOwnerUserIdForUpdate(accountId, ownerId)).thenReturn(Optional.of(account));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.verify(ownerId,
                new VerifyBankAccountRequest(accountId, "1234567890123456", "HDFC0000240")));

        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().toLowerCase().contains("remove and link this legacy bank account again"));
        assertEquals(0, account.getVerifyAttempts());
        verify(verification, never()).verify(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsLinkingTheSameFingerprintTwiceForOneUser() {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankOtpService otp = mock(BankOtpService.class);
        BankDataEncryptionService encryption = encryption();
        BankVerificationService verification = mock(BankVerificationService.class);
        BankAccountService service = new BankAccountService(repository, otp, verification, encryption);
        UUID ownerId = UUID.randomUUID();
        when(repository.existsByOwnerUserIdAndAccountFingerprint(eq(ownerId), anyString())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.link(ownerId,
                new LinkBankAccountRequest(UUID.randomUUID(), "HDFC Bank", "Example User",
                        "1234567890123456", "1234567890123456")));

        assertEquals(409, error.getStatusCode().value());
        verify(otp, never()).consumeVerified(any(), any());
        verify(repository, never()).save(any(BankAccount.class));
    }

    @Test
    void concurrentVerificationCallsForOneAccountReachTheProviderOnlyOnce() throws Exception {
        BankAccountRepository repository = mock(BankAccountRepository.class);
        BankDataEncryptionService encryption = encryption();
        BankVerificationService verification = mock(BankVerificationService.class);
        BankOtpService otp = mock(BankOtpService.class);
        BankAccount account = spy(new BankAccount(UUID.randomUUID(), "HDFC Bank", encryption.encrypt("Example User"),
                "XXXXXXXX3456", encryption.fingerprint("1234567890123456")));
        UUID ownerId = account.getOwnerUserId();
        UUID accountId = UUID.randomUUID();
        ReentrantLock rowLock = new ReentrantLock();
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch allowProviderToFinish = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();
        BankAccountService service = new BankAccountService(repository, otp, verification, encryption);
        when(repository.findByIdAndOwnerUserIdForUpdate(accountId, ownerId)).thenAnswer(call -> {
            rowLock.lock();
            return Optional.of(account);
        });
        doAnswer(call -> {
            String status = (String) call.callRealMethod();
            if (!"PENDING".equals(status) && rowLock.isHeldByCurrentThread()) rowLock.unlock();
            return status;
        }).when(account).getVerificationStatus();
        doAnswer(call -> {
            call.callRealMethod();
            if (rowLock.isHeldByCurrentThread()) rowLock.unlock();
            return null;
        }).when(account).updateVerificationResult(anyString(), anyString(), any(), any(), any(), any());
        when(verification.verify(anyString(), anyString(), anyString())).thenAnswer(call -> {
            providerCalls.incrementAndGet();
            providerEntered.countDown();
            assertTrue(allowProviderToFinish.await(3, TimeUnit.SECONDS));
            return new BankVerificationService.VerificationResult("VERIFIED", "Example User", "ref-1",
                    "HDFC Bank", "CASHFREE_PRODUCTION", "Verified");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var request = new VerifyBankAccountRequest(accountId, "1234567890123456", "HDFC0000240");
            var first = executor.submit(() -> service.verify(ownerId, request));
            assertTrue(providerEntered.await(3, TimeUnit.SECONDS));
            var second = executor.submit(() -> service.verify(ownerId, request));
            allowProviderToFinish.countDown();
            assertNotNull(first.get(3, TimeUnit.SECONDS));
            ExecutionException conflict = assertThrows(ExecutionException.class, () -> second.get(3, TimeUnit.SECONDS));
            assertTrue(conflict.getCause() instanceof ResponseStatusException);
            assertEquals(409, ((ResponseStatusException) conflict.getCause()).getStatusCode().value());
            assertEquals(1, providerCalls.get());
        } finally {
            allowProviderToFinish.countDown();
            executor.shutdownNow();
        }
    }

    private static BankAccountService service(BankAccountRepository repository, BankOtpService otp,
                                              BankDataEncryptionService encryption) {
        BankVerificationService verification = new BankVerificationService(
                new RestTemplate(), encryption, "none", "", "", "");
        return new BankAccountService(repository, otp, verification, encryption);
    }

    private static BankDataEncryptionService encryption() {
        return new BankDataEncryptionService(ENCRYPTION_KEY);
    }
}
