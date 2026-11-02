package com.demo.upimesh.service;

import com.demo.upimesh.dto.BankAccountDtos.LinkBankAccountRequest;
import com.demo.upimesh.dto.BankAccountDtos.VerifyBankAccountRequest;
import com.demo.upimesh.model.BankAccount;
import com.demo.upimesh.model.BankAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void hidesLegacySimulatedAccountsFromUserResponses() {
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

        assertEquals(1, result.size());
        assertEquals("PROVIDER_PENDING", result.get(0).verificationMode());
        assertEquals("XXXXXXXX9876", result.get(0).maskedAccountNumber());
        assertEquals("Example User", result.get(0).accountHolderName());
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
