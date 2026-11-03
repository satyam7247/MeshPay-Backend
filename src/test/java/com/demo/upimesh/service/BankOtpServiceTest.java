package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.BankOtpChallenge;
import com.demo.upimesh.model.BankOtpChallengeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class BankOtpServiceTest {
    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    @Test void generatedCodeIsAlwaysSixDigitsIncludingLeadingZeroes() {
        for (int i = 0; i < 1000; i++) assertTrue(BankOtpService.generateOtp().matches("[0-9]{6}"));
    }

    @Test void brevoIsConfiguredOnlyWhenAllThreeSettingsExist() {
        BrevoEmailService email = new BrevoEmailService();
        ReflectionTestUtils.setField(email, "apiKey", "key");
        ReflectionTestUtils.setField(email, "senderEmail", "sender@example.test");
        assertFalse(email.isConfigured());
        ReflectionTestUtils.setField(email, "senderName", "MeshPay");
        assertTrue(email.isConfigured());
    }

    @Test void emailProviderRefusesProdProfileUnlessExplicitlyAllowed() {
        Fixtures f = new Fixtures();
        when(f.env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        assertThrows(IllegalStateException.class, f.service::validateProviderSafety);
    }

    @Test void hmacCodeVerifiesOnlyForMatchingCode() {
        String hash = BankOtpService.hmac("012345", SECRET);
        assertEquals(64, hash.length());
        assertNotEquals("012345", hash);
        assertTrue(BankOtpService.verifyEmailCode(hash, "012345", SECRET));
        assertFalse(BankOtpService.verifyEmailCode(hash, "012346", SECRET));
    }

    @Test void emailOtpIsHashedAndNeverReturnedOrLogged(CapturedOutput output) {
        Fixtures f = new Fixtures();
        Instant beforeSend = Instant.now();
        AtomicReference<String> delivered = new AtomicReference<>();
        doAnswer(call -> { delivered.set(call.getArgument(1)); return null; })
                .when(f.email).sendBankLinkOtp(eq("registered@example.com"), anyString());
        var response = f.service.send(f.user, "9876543210", null);
        BankOtpChallenge saved = f.saved.get();
        assertEquals(65, response.expiresInSeconds());
        assertEquals(65, response.resendAfterSeconds());
        assertEquals(65, saved.getExpiresAt().getEpochSecond() - saved.getCreatedAt().getEpochSecond());
        assertFalse(saved.getCreatedAt().isBefore(beforeSend));
        assertNotNull(delivered.get());
        assertTrue(delivered.get().matches("[0-9]{6}"));
        assertNotEquals(delivered.get(), saved.getOtpHash());
        assertFalse(response.toString().contains(delivered.get()));
        assertFalse(output.getAll().contains(delivered.get()));
        assertEquals("EMAIL", response.deliveryMode());
        assertEquals("r***@example.com", response.maskedDestination());
    }

    @Test void expiredAndWrongCodesDoNotVerify() {
        Fixtures f = new Fixtures();
        BankOtpChallenge challenge = f.challenge("012345");
        when(f.challenges.findByIdAndOwnerUserId(challenge.getId(), f.user.getId())).thenReturn(Optional.of(challenge));
        assertThrows(ResponseStatusException.class, () -> f.service.verify(f.user.getId(), f.user.getPhone(), challenge.getId(), "999999"));
        assertEquals(1, challenge.getAttempts());
        BankOtpChallenge expired = new BankOtpChallenge(f.user.getId(), Instant.now().minusSeconds(360),
                Instant.now().minusSeconds(1), Instant.now());
        UUID expiredId = UUID.randomUUID(); ReflectionTestUtils.setField(expired, "id", expiredId);
        when(f.challenges.findByIdAndOwnerUserId(expiredId, f.user.getId())).thenReturn(Optional.of(expired));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> f.service.verify(f.user.getId(), f.user.getPhone(), expiredId, "012345"));
        assertEquals(410, error.getStatusCode().value());
    }

    @Test void fiveWrongAttemptsConsumeTheChallenge() {
        Fixtures f = new Fixtures();
        BankOtpChallenge challenge = f.challenge("012345");
        when(f.challenges.findByIdAndOwnerUserId(challenge.getId(), f.user.getId())).thenReturn(Optional.of(challenge));
        for (int i=0; i<4; i++) assertThrows(ResponseStatusException.class,
                () -> f.service.verify(f.user.getId(), f.user.getPhone(), challenge.getId(), "999999"));
        ResponseStatusException last = assertThrows(ResponseStatusException.class,
                () -> f.service.verify(f.user.getId(), f.user.getPhone(), challenge.getId(), "999999"));
        assertEquals(429, last.getStatusCode().value());
        assertTrue(challenge.isConsumed());
        assertEquals(5, challenge.getAttempts());
    }

    @Test void verifiedOtpCanBeConsumedAfterItsCodeExpires() {
        Fixtures f = new Fixtures();
        BankOtpChallenge challenge = f.challenge("012345");
        challenge.markVerified(Instant.now().minusSeconds(90));
        ReflectionTestUtils.setField(challenge, "expiresAt", Instant.now().minusSeconds(1));
        when(f.challenges.findByIdAndOwnerUserId(challenge.getId(), f.user.getId()))
                .thenReturn(Optional.of(challenge));

        assertDoesNotThrow(() -> f.service.consumeVerified(f.user.getId(), challenge.getId()));
        assertTrue(challenge.isConsumed());
    }

    @Test void resendReplacesHashAndOldCodeStopsWorking() {
        Fixtures f = new Fixtures();
        AtomicReference<String> delivered = new AtomicReference<>();
        doAnswer(call -> { delivered.set(call.getArgument(1)); return null; })
                .when(f.email).sendBankLinkOtp(anyString(), anyString());
        var first = f.service.send(f.user, "9876543210", null);
        String oldCode = delivered.get();
        BankOtpChallenge challenge = f.saved.get();
        ReflectionTestUtils.setField(challenge, "resendAfter", Instant.now().minusSeconds(1));
        when(f.challenges.findOpenChallengesForUpdate(f.user.getId())).thenReturn(List.of(challenge));
        f.service.send(f.user, "9876543210", first.challengeId());
        String newCode = delivered.get();
        assertNotEquals(oldCode, newCode);
        assertFalse(BankOtpService.verifyEmailCode(challenge.getOtpHash(), oldCode, SECRET));
        assertTrue(BankOtpService.verifyEmailCode(challenge.getOtpHash(), newCode, SECRET));
        assertEquals(2, challenge.getSendCount());
        challenge.recordFailedAttempt();
        int attempts = challenge.getAttempts();
        challenge.resend(Instant.now(), Instant.now().plusSeconds(65), Instant.now().plusSeconds(65));
        assertEquals(attempts, challenge.getAttempts());
    }

    @Test void sendFailureReturns503WithoutSavingOrConsumingChallengeQuota() {
        Fixtures f = new Fixtures();
        doThrow(new RuntimeException("provider detail must not escape"))
                .when(f.email).sendBankLinkOtp(anyString(), anyString());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> f.service.send(f.user, "9876543210", null));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.getReason().contains("provider detail"));
        verify(f.challenges, never()).save(any(BankOtpChallenge.class));
    }

    @Test void limitsNewChallengesToFivePerHour() {
        Fixtures f = new Fixtures();
        when(f.challenges.countByOwnerUserIdAndCreatedAtAfter(eq(f.user.getId()), any())).thenReturn(5L);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> f.service.send(f.user, "9876543210", null));
        assertEquals(429, error.getStatusCode().value());
        verify(f.email, never()).sendBankLinkOtp(anyString(), anyString());
    }

    @Test void limitsChallengeToThreeTotalSends() {
        Fixtures f = new Fixtures();
        AtomicReference<String> delivered = new AtomicReference<>();
        doAnswer(call -> { delivered.set(call.getArgument(1)); return null; })
                .when(f.email).sendBankLinkOtp(anyString(), anyString());
        var response = f.service.send(f.user, "9876543210", null);
        BankOtpChallenge current = f.saved.get();
        when(f.challenges.findOpenChallengesForUpdate(f.user.getId())).thenReturn(List.of(current));
        ReflectionTestUtils.setField(current, "resendAfter", Instant.now().minusSeconds(1));
        f.service.send(f.user, "9876543210", response.challengeId());
        ReflectionTestUtils.setField(current, "resendAfter", Instant.now().minusSeconds(1));
        f.service.send(f.user, "9876543210", response.challengeId());
        ReflectionTestUtils.setField(current, "resendAfter", Instant.now().minusSeconds(1));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> f.service.send(f.user, "9876543210", response.challengeId()));
        assertEquals(429, error.getStatusCode().value());
        assertEquals(3, current.getSendCount());
    }

    private static class Fixtures {
        final BankOtpChallengeRepository challenges = mock(BankOtpChallengeRepository.class);
        final AppUserRepository users = mock(AppUserRepository.class);
        final SmsProviderService sms = mock(SmsProviderService.class);
        final BrevoEmailService email = mock(BrevoEmailService.class);
        final Environment env = mock(Environment.class);
        final AtomicReference<BankOtpChallenge> saved = new AtomicReference<>();
        final AppUser user = new AppUser("Test", "registered@example.com", "9876543210", "hash", "test@upi");
        final BankOtpService service;

        Fixtures() {
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
            when(challenges.findOpenChallengesForUpdate(user.getId())).thenReturn(List.of());
            when(challenges.countByOwnerUserIdAndCreatedAtAfter(eq(user.getId()), any())).thenReturn(0L);
            when(challenges.save(any(BankOtpChallenge.class))).thenAnswer(call -> {
                BankOtpChallenge challenge = call.getArgument(0);
                if (ReflectionTestUtils.getField(challenge, "id") == null)
                    ReflectionTestUtils.setField(challenge, "id", UUID.randomUUID());
                saved.set(challenge); return challenge;
            });
            when(email.isConfigured()).thenReturn(true);
            service = new BankOtpService(challenges, users, sms, email, env, "email", SECRET, false);
        }

        BankOtpChallenge challenge(String code) {
            Instant now = Instant.now();
            BankOtpChallenge challenge = new BankOtpChallenge(user.getId(), now.minusSeconds(1),
                    now.plusSeconds(300), now.minusSeconds(1));
            ReflectionTestUtils.setField(challenge, "id", UUID.randomUUID());
            challenge.setOtpHash(BankOtpService.hmac(code, SECRET));
            return challenge;
        }
    }
}
