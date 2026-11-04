package com.demo.upimesh.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.PasswordChallenge;
import com.demo.upimesh.model.PasswordChallengeRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class PasswordServiceTest {
    private AppUserRepository users;
    private PasswordChallengeRepository challenges;
    private SessionTokenService sessions;
    private BrevoEmailService email;
    private BCryptPasswordEncoder encoder;
    private PasswordService service;
    private AttemptRateLimiter attempts;

    @BeforeEach void setUp(){
        users=mock(AppUserRepository.class); challenges=mock(PasswordChallengeRepository.class);
        sessions=mock(SessionTokenService.class); email=mock(BrevoEmailService.class);
        encoder=new BCryptPasswordEncoder(4);
        attempts=new AttemptRateLimiter();
        service=new PasswordService(users,challenges,encoder,sessions,email,attempts);
    }

    @Test void changingPasswordUsesBcryptAndRevokesEverySession() throws Exception {
        AppUser user=new AppUser("A User","user@example.test","+919876543210",encoder.encode("OldPass1!"),"user@test");
        UUID id=UUID.randomUUID();Field idField=AppUser.class.getDeclaredField("id");idField.setAccessible(true);idField.set(user,id);

        service.changeWithCurrentPassword(user,"OldPass1!","NewPass2!");

        assertTrue(encoder.matches("NewPass2!",user.getPasswordHash()));
        assertFalse(encoder.matches("OldPass1!",user.getPasswordHash()));
        verify(sessions).revokeAllForUser(id.toString());
    }

    @Test void missingEmailConfigurationFailsBeforeClaimingRecoveryRequestWasAccepted(){
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.requestForgotOtp("user@example.test","127.0.0.1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatusCode());
        verifyNoInteractions(users,challenges);
        verify(email).isConfigured();
        verifyNoMoreInteractions(email);
    }

    @Test void incorrectCurrentPasswordDoesNotUpdateHashOrRevokeSessions(){
        AppUser user=new AppUser("A User","user@example.test","+919876543210",encoder.encode("OldPass1!"),"user@test");
        String original=user.getPasswordHash();

        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.changeWithCurrentPassword(user,"wrong","NewPass2!"));

        assertEquals(HttpStatus.UNAUTHORIZED,error.getStatusCode());
        assertEquals(original,user.getPasswordHash());
        verifyNoInteractions(sessions);
    }

    @Test void currentPasswordChangeIsLimitedByUserAndClientIp() throws Exception {
        AppUser user=newUserWithId();
        for(int i=0;i<5;i++) {
            ResponseStatusException wrong=assertThrows(ResponseStatusException.class,
                    ()->service.changeWithCurrentPassword(user,"wrong","NewPass2!","user-ip-"+System.nanoTime()));
            assertEquals(HttpStatus.UNAUTHORIZED,wrong.getStatusCode());
        }
        ResponseStatusException userLimited=assertThrows(ResponseStatusException.class,
                ()->service.changeWithCurrentPassword(user,"wrong","NewPass2!","another-ip"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,userLimited.getStatusCode());
        assertEquals("900",userLimited.getHeaders().getFirst("Retry-After"));

        for(int i=0;i<5;i++) {
            AppUser distinctUser=newUserWithId();
            ResponseStatusException wrong=assertThrows(ResponseStatusException.class,
                    ()->service.changeWithCurrentPassword(distinctUser,"wrong","NewPass2!","shared-ip"));
            assertEquals(HttpStatus.UNAUTHORIZED,wrong.getStatusCode());
        }
        ResponseStatusException ipLimited=assertThrows(ResponseStatusException.class,
                ()->service.changeWithCurrentPassword(newUserWithId(),"wrong","NewPass2!","shared-ip"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,ipLimited.getStatusCode());
    }

    @Test void appLockEnableAccountPasswordCheckUsesPerUserAndIpLimiter() throws Exception {
        AppUser user=newUserWithId();
        ResponseStatusException wrong=assertThrows(ResponseStatusException.class,
                ()->service.verifyAccountPasswordForAppLock(user,"wrong","enable-ip"));
        assertEquals(HttpStatus.UNAUTHORIZED,wrong.getStatusCode());
        assertDoesNotThrow(()->service.verifyAccountPasswordForAppLock(user,"OldPass1!","enable-ip"));
        for(int i=0;i<5;i++) {
            String ip="locked-user-ip-"+i;
            assertThrows(ResponseStatusException.class,()->service.verifyAccountPasswordForAppLock(user,"bad",ip));
        }
        ResponseStatusException userLimited=assertThrows(ResponseStatusException.class,
                ()->service.verifyAccountPasswordForAppLock(user,"OldPass1!","another-ip"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,userLimited.getStatusCode());
        assertEquals("900",userLimited.getHeaders().getFirst("Retry-After"));

        for(int i=0;i<5;i++) {
            AppUser distinctUser=newUserWithId();
            assertThrows(ResponseStatusException.class,
                    ()->service.verifyAccountPasswordForAppLock(distinctUser,"bad","shared-enable-ip"));
        }
        ResponseStatusException ipLimited=assertThrows(ResponseStatusException.class,
                ()->service.verifyAccountPasswordForAppLock(newUserWithId(),"OldPass1!","shared-enable-ip"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,ipLimited.getStatusCode());
    }

    @Test void forgotPasswordResponseDoesNotRevealWhetherAccountExistsOrReturnOtp() throws Exception {
        when(email.isConfigured()).thenReturn(true);
        when(users.findByEmailIgnoreCase("nobody@example.test")).thenReturn(Optional.empty());
        when(challenges.findFirstByEmailKeyAndPurposeOrderByCreatedAtDesc(anyString(),any())).thenReturn(Optional.empty());
        when(challenges.countByRequesterKeyAndCreatedAtAfter(anyString(),any())).thenReturn(0L);
        when(challenges.save(any(PasswordChallenge.class))).thenAnswer(invocation->{
            PasswordChallenge challenge=invocation.getArgument(0);
            Field idField=PasswordChallenge.class.getDeclaredField("id");idField.setAccessible(true);idField.set(challenge,UUID.randomUUID());
            return challenge;
        });

        PasswordService.ChallengeResponse response=service.requestForgotOtp("Nobody@example.test","127.0.0.1");

        assertNotNull(response.challengeId());
        assertTrue(response.message().contains("If an account"));
        assertFalse(response.toString().matches(".*\\b\\d{6}\\b.*"));
        verify(email).isConfigured();
        verify(email,never()).sendPasswordOtp(anyString(),anyString());
    }

    @Test void validOtpProducesSingleUseShortLivedResetToken() throws Exception {
        when(email.isConfigured()).thenReturn(true);
        AtomicReference<PasswordChallenge> latest=new AtomicReference<>();
        AtomicReference<String> deliveredCode=new AtomicReference<>();
        AppUser user=newUserWithId();
        when(challenges.countByRequesterKeyAndCreatedAtAfter(anyString(),any())).thenReturn(0L);
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(challenges.findFirstByEmailKeyAndPurposeOrderByCreatedAtDesc(anyString(),any()))
                .thenAnswer(call->Optional.ofNullable(latest.get()));
        when(challenges.save(any(PasswordChallenge.class))).thenAnswer(call->{
            PasswordChallenge challenge=call.getArgument(0);
            Field idField=PasswordChallenge.class.getDeclaredField("id");idField.setAccessible(true);idField.set(challenge,UUID.randomUUID());
            latest.set(challenge);return challenge;
        });
        when(challenges.findByIdForUpdate(any(UUID.class))).thenAnswer(call->Optional.ofNullable(latest.get()));
        org.mockito.Mockito.doAnswer(call->{deliveredCode.set(call.getArgument(1));return null;})
                .when(email).sendPasswordOtp(anyString(),anyString());

        PasswordService.ChallengeResponse challenge=service.requestForgotOtp("user@example.test","127.0.0.1");
        PasswordService.ResetAuthorization grant=service.verifyOtp(challenge.challengeId(),deliveredCode.get());

        assertNotNull(grant.resetToken());
        assertEquals(600,grant.expiresInSeconds());
        ResponseStatusException reused=assertThrows(ResponseStatusException.class,
                ()->service.verifyOtp(challenge.challengeId(),deliveredCode.get()));
        assertEquals(HttpStatus.UNAUTHORIZED,reused.getStatusCode());
    }

    @Test void screenLockOtpIsDeliveredToItsAccountAndCannotBeUsedForAccountPasswordReset() throws Exception {
        when(email.isConfigured()).thenReturn(true);
        AppUser user=newUserWithId();
        user.enableAppLock("numeric",encoder.encode("123456"));
        AtomicReference<PasswordChallenge> latest=new AtomicReference<>();
        AtomicReference<String> deliveredCode=new AtomicReference<>();
        when(challenges.countByRequesterKeyAndCreatedAtAfter(anyString(),any())).thenReturn(0L);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(challenges.findFirstByEmailKeyAndPurposeOrderByCreatedAtDesc(anyString(),any()))
                .thenAnswer(call->Optional.ofNullable(latest.get()));
        when(challenges.save(any(PasswordChallenge.class))).thenAnswer(call->{
            PasswordChallenge challenge=call.getArgument(0);
            Field id=PasswordChallenge.class.getDeclaredField("id");id.setAccessible(true);id.set(challenge,UUID.randomUUID());
            latest.set(challenge);return challenge;
        });
        when(challenges.findByIdForUpdate(any(UUID.class))).thenAnswer(call->Optional.ofNullable(latest.get()));
        doAnswer(call->{deliveredCode.set(call.getArgument(1));return null;})
                .when(email).sendAppLockResetOtp(anyString(),anyString());

        var challenge=service.requestScreenLockOtp(user,"127.0.0.1");
        assertEquals(PasswordChallenge.Purpose.APP_LOCK,latest.get().getPurpose());
        verify(email).sendAppLockResetOtp(eq(user.getEmail()),anyString());
        assertThrows(ResponseStatusException.class,()->service.verifyOtp(challenge.challengeId(),deliveredCode.get()));
        var grant=service.verifyScreenLockOtp(user,challenge.challengeId(),deliveredCode.get());
        assertNotNull(grant.resetToken());
        service.resetScreenLock(user,challenge.challengeId(),grant.resetToken(),"alphabetic","AbCdEf");
        assertEquals("alphabetic",user.getAppLockType());
        assertTrue(encoder.matches("AbCdEf",user.getAppLockPasswordHash()));
        assertTrue(latest.get().isResetConsumed());
        verify(email).sendAppLockChanged(user.getEmail(),true);
    }

    @Test void fiveIncorrectOtpAttemptsBlockFurtherVerification() throws Exception {
        when(email.isConfigured()).thenReturn(true);
        AtomicReference<PasswordChallenge> latest=new AtomicReference<>();
        AtomicReference<String> deliveredCode=new AtomicReference<>();
        AppUser user=newUserWithId();
        when(challenges.countByRequesterKeyAndCreatedAtAfter(anyString(),any())).thenReturn(0L);
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
        when(challenges.findFirstByEmailKeyAndPurposeOrderByCreatedAtDesc(anyString(),any()))
                .thenAnswer(call->Optional.ofNullable(latest.get()));
        when(challenges.save(any(PasswordChallenge.class))).thenAnswer(call->{
            PasswordChallenge challenge=call.getArgument(0);
            Field idField=PasswordChallenge.class.getDeclaredField("id");idField.setAccessible(true);idField.set(challenge,UUID.randomUUID());
            latest.set(challenge);return challenge;
        });
        when(challenges.findByIdForUpdate(any(UUID.class))).thenAnswer(call->Optional.ofNullable(latest.get()));
        org.mockito.Mockito.doAnswer(call->{deliveredCode.set(call.getArgument(1));return null;})
                .when(email).sendPasswordOtp(anyString(),anyString());
        PasswordService.ChallengeResponse challenge=service.requestForgotOtp("user@example.test","127.0.0.1");
        String incorrectCode="000000".equals(deliveredCode.get())?"000001":"000000";

        for(int attempt=0;attempt<5;attempt++) assertThrows(ResponseStatusException.class,
                ()->service.verifyOtp(challenge.challengeId(),incorrectCode));
        ResponseStatusException blocked=assertThrows(ResponseStatusException.class,
                ()->service.verifyOtp(challenge.challengeId(),deliveredCode.get()));
        assertEquals(HttpStatus.UNAUTHORIZED,blocked.getStatusCode());
    }

    private AppUser newUserWithId() throws Exception {
        AppUser user=new AppUser("A User","user@example.test","+919876543210",encoder.encode("OldPass1!"),"user@test");
        Field idField=AppUser.class.getDeclaredField("id");idField.setAccessible(true);idField.set(user,UUID.randomUUID());
        return user;
    }
}
