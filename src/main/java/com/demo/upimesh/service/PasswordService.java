package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.PasswordChallenge;
import com.demo.upimesh.model.PasswordChallengeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordService {
    private static final int OTP_TTL_SECONDS=300;
    private static final int RESET_TTL_SECONDS=600;
    private static final int MAX_ATTEMPTS=5;
    private static final int RESEND_SECONDS=60;
    private static final int IP_WINDOW_SECONDS=900;
    private static final int MAX_REQUESTS_PER_IP=5;
    private final AppUserRepository users;
    private final PasswordChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final SessionTokenService sessions;
    private final BrevoEmailService email;
    private final SecureRandom random=new SecureRandom();
    private final AttemptRateLimiter attempts;

    @Autowired
    public PasswordService(AppUserRepository users, PasswordChallengeRepository challenges,
                           PasswordEncoder encoder, SessionTokenService sessions, BrevoEmailService email,
                           AttemptRateLimiter attempts) {
        this.users=users; this.challenges=challenges; this.encoder=encoder; this.sessions=sessions; this.email=email;
        this.attempts=attempts;
    }

    public PasswordService(AppUserRepository users, PasswordChallengeRepository challenges,
                           PasswordEncoder encoder, SessionTokenService sessions, BrevoEmailService email) {
        this(users, challenges, encoder, sessions, email, new AttemptRateLimiter());
    }

    @Transactional
    public void changeWithCurrentPassword(AppUser user, String currentPassword, String newPassword) {
        changeWithCurrentPassword(user,currentPassword,newPassword,"unknown");
    }

    @Transactional
    public void changeWithCurrentPassword(AppUser user, String currentPassword, String newPassword, String ip) {
        PasswordPolicy.requireValid(newPassword);
        attempts.check("password-change:user:"+user.getId(),5,900);
        attempts.check("password-change:ip:"+(ip==null?"unknown":ip),5,900);
        if (!encoder.matches(currentPassword == null ? "" : currentPassword,user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Current password is incorrect.");
        }
        attempts.clear("password-change:user:"+user.getId());
        attempts.clear("password-change:ip:"+(ip==null?"unknown":ip));
        if (encoder.matches(newPassword,user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"New password must differ from your current password.");
        }
        user.changePasswordHash(encoder.encode(newPassword));
        sessions.revokeAllForUser(user.getId().toString());
    }

    @Transactional
    public void verifyAccountPasswordForAppLock(AppUser user, String password, String ip) {
        String userKey="app-lock-enable:user:"+user.getId();
        String ipKey="app-lock-enable:ip:"+(ip==null?"unknown":ip);
        attempts.check(userKey,5,900);
        attempts.check(ipKey,5,900);
        if (!encoder.matches(password==null?"":password,user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account password is incorrect.");
        }
        attempts.clear(userKey);
        attempts.clear(ipKey);
    }

    @Transactional
    public ChallengeResponse requestForgotOtp(String emailAddress, String requesterAddress) {
        return issueOtp(emailAddress,requesterAddress,PasswordChallenge.Purpose.FORGOT,null,true);
    }

    @Transactional
    public ChallengeResponse requestChangeOtp(AppUser user, String requesterAddress) {
        return issueOtp(user.getEmail(),requesterAddress,PasswordChallenge.Purpose.CHANGE,user,false);
    }

    @Transactional
    public ChallengeResponse requestScreenLockOtp(AppUser user, String requesterAddress) {
        if (!user.isAppLockEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"App Lock is not enabled for this account.");
        return issueOtp(user.getEmail(),requesterAddress,PasswordChallenge.Purpose.APP_LOCK,user,false);
    }

    private ChallengeResponse issueOtp(String emailAddress, String requesterAddress, PasswordChallenge.Purpose purpose, AppUser requiredUser,
                                       boolean hideAccountExistence) {
        // A global configuration check returns the same result for known and unknown addresses.
        if(!email.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Password email delivery is not configured on the backend.");
        }
        String normalized=emailAddress.trim().toLowerCase(Locale.ROOT);
        String emailKey=sha256(normalized);
        Instant now=Instant.now();
        String requesterKey=sha256(requesterAddress==null?"unknown":requesterAddress);
        if (challenges.countByRequesterKeyAndCreatedAtAfter(requesterKey,now.minusSeconds(IP_WINDOW_SECONDS))>=MAX_REQUESTS_PER_IP) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many verification requests. Try again later.");
        }
        PasswordChallenge latest=challenges.findFirstByEmailKeyAndPurposeOrderByCreatedAtDesc(emailKey,purpose).orElse(null);
        if (latest!=null && latest.getCreatedAt().isAfter(now.minusSeconds(RESEND_SECONDS))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait before requesting another code.");
        }
        AppUser user=requiredUser;
        if (user==null) user=users.findByEmailIgnoreCase(normalized).orElse(null);
        if (requiredUser!=null && !requiredUser.getEmail().equalsIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password recovery could not be started.");
        }
        String otp=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        PasswordChallenge challenge=challenges.save(new PasswordChallenge(
                user==null?null:user.getId().toString(),emailKey,requesterKey,purpose,encoder.encode(otp),
                now.plusSeconds(OTP_TTL_SECONDS),now));
        if (user!=null) {
            try {
                if (purpose == PasswordChallenge.Purpose.APP_LOCK) email.sendAppLockResetOtp(user.getEmail(),otp);
                else email.sendPasswordOtp(user.getEmail(),otp);
            }
            catch (BrevoEmailService.EmailDeliveryUnavailableException failure) {
                challenge.invalidate();
                if (!hideAccountExistence) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery is unavailable. Try again later.");
            }
        }
        return new ChallengeResponse(challenge.getId().toString(),
                hideAccountExistence ? "If an account exists, recovery email may be sent. This response does not confirm account existence or delivery." : "A verification request was accepted by the configured email provider.",
                OTP_TTL_SECONDS,RESEND_SECONDS);
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public ResetAuthorization verifyOtp(String challengeId, String code) {
        return verifyOtpFor(challengeId,code,null,null);
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public ResetAuthorization verifyScreenLockOtp(AppUser user, String challengeId, String code) {
        try { return verifyOtpFor(challengeId,code,user.getId().toString(),PasswordChallenge.Purpose.APP_LOCK); }
        catch (ResponseStatusException invalid) {
            if (invalid.getStatusCode()==HttpStatus.UNAUTHORIZED)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Verification code is invalid or expired.");
            throw invalid;
        }
    }

    private ResetAuthorization verifyOtpFor(String challengeId, String code, String expectedUserId, PasswordChallenge.Purpose expectedPurpose) {
        PasswordChallenge challenge=lockChallenge(challengeId);
        if (expectedPurpose==null && challenge.getPurpose()==PasswordChallenge.Purpose.APP_LOCK) throw invalidOtp();
        if ((expectedUserId!=null && !expectedUserId.equals(challenge.getUserId()))
                || (expectedPurpose!=null && challenge.getPurpose()!=expectedPurpose)) throw invalidOtp();
        ensureLatest(challenge);
        if (challenge.getUserId()==null || challenge.isOtpConsumed() || challenge.getOtpExpiresAt()==null
                || challenge.getOtpExpiresAt().isBefore(Instant.now()) || challenge.getAttempts()>=MAX_ATTEMPTS) {
            throw invalidOtp();
        }
        if (code==null || !encoder.matches(code,challenge.getOtpHash())) {
            challenge.recordWrongAttempt();
            throw invalidOtp();
        }
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenge.consumeOtp(sha256(token),Instant.now().plusSeconds(RESET_TTL_SECONDS));
        return new ResetAuthorization(token,RESET_TTL_SECONDS);
    }

    @Transactional
    public void resetScreenLock(AppUser principal, String challengeId, String resetToken, String type, String newPassword) {
        if (!validScreenLockPassword(type,newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Screen lock password must match the selected six-character password type.");
        }
        PasswordChallenge challenge;
        try {
            challenge=lockChallenge(challengeId);
            ensureLatest(challenge);
        } catch (ResponseStatusException invalid) {
            if (invalid.getStatusCode()==HttpStatus.UNAUTHORIZED)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Screen lock reset authorization is invalid or expired.");
            throw invalid;
        }
        if (challenge.getPurpose()!=PasswordChallenge.Purpose.APP_LOCK || !principal.getId().toString().equals(challenge.getUserId())
                || challenge.isResetConsumed() || challenge.getResetTokenHash()==null
                || challenge.getResetExpiresAt()==null || challenge.getResetExpiresAt().isBefore(Instant.now())
                || resetToken==null || !MessageDigest.isEqual(challenge.getResetTokenHash().getBytes(StandardCharsets.US_ASCII),sha256(resetToken).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Screen lock reset authorization is invalid or expired.");
        }
        AppUser user=users.findByIdForUpdate(principal.getId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Screen lock reset authorization is invalid or expired."));
        if (!user.isAppLockEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"App Lock is not enabled for this account.");
        user.enableAppLock(type,encoder.encode(newPassword));
        challenge.consumeReset();
        email.sendAppLockChanged(user.getEmail(),true);
    }

    private boolean validScreenLockPassword(String type,String password) {
        return password!=null && password.length()==6
                && ("numeric".equals(type)?password.matches("[0-9]{6}"):"alphabetic".equals(type)&&password.matches("[A-Za-z]{6}"));
    }

    @Transactional
    public void resetPassword(String challengeId, String resetToken, String newPassword) {
        PasswordPolicy.requireValid(newPassword);
        PasswordChallenge challenge=lockChallenge(challengeId);
        if (challenge.getPurpose()==PasswordChallenge.Purpose.APP_LOCK) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Password reset authorization is invalid or expired.");
        }
        ensureLatest(challenge);
        if (challenge.getUserId()==null || challenge.isResetConsumed() || challenge.getResetTokenHash()==null
                || challenge.getResetExpiresAt()==null || challenge.getResetExpiresAt().isBefore(Instant.now())
                || resetToken==null || !MessageDigest.isEqual(challenge.getResetTokenHash().getBytes(StandardCharsets.US_ASCII),sha256(resetToken).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Password reset authorization is invalid or expired.");
        }
        AppUser user=users.findById(UUID.fromString(challenge.getUserId()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Password reset authorization is invalid or expired."));
        if (encoder.matches(newPassword,user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"New password must differ from your current password.");
        }
        user.changePasswordHash(encoder.encode(newPassword));
        challenge.consumeReset();
        sessions.revokeAllForUser(user.getId().toString());
    }

    private PasswordChallenge lockChallenge(String rawId) {
        try {
            return challenges.findByIdForUpdate(UUID.fromString(rawId))
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verification code is invalid or expired."));
        } catch (IllegalArgumentException invalidUuid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verification code is invalid or expired.");
        }
    }

    private void ensureLatest(PasswordChallenge challenge) {
        PasswordChallenge latest=challenges.findFirstByEmailKeyAndPurposeOrderByCreatedAtDesc(challenge.getEmailKey(),challenge.getPurpose()).orElse(null);
        if (latest==null || !latest.getId().equals(challenge.getId())) throw invalidOtp();
    }
    private ResponseStatusException invalidOtp(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verification code is invalid or expired.");}

    private String sha256(String value) {
        try {return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch (Exception error) {throw new IllegalStateException("Secure hashing is unavailable.");}
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000)
    @Transactional
    public void removeExpiredChallenges(){challenges.deleteAllByExpiresAtBefore(Instant.now());}
    public record ChallengeResponse(String challengeId,String message,int expiresInSeconds,int resendAfterSeconds){}
    public record ResetAuthorization(String resetToken,int expiresInSeconds){}
}
