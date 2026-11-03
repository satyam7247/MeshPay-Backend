package com.demo.upimesh.service;

import com.demo.upimesh.dto.BankAccountDtos.SendOtpResponse;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.BankOtpChallenge;
import com.demo.upimesh.model.BankOtpChallengeRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BankOtpService {
    private static final Logger log = LoggerFactory.getLogger(BankOtpService.class);
    private static final int OTP_LIFETIME_SECONDS = 65;
    private static final int RESEND_WAIT_SECONDS = 65;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS_PER_CHALLENGE = 3;
    private static final int MAX_CHALLENGES_PER_HOUR = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BankOtpChallengeRepository challenges;
    private final AppUserRepository users;
    private final SmsProviderService smsProvider;
    private final BrevoEmailService emailService;
    private final Environment environment;
    private final String provider;
    private final String hmacSecret;
    private final boolean allowEmailInProduction;

    public BankOtpService(BankOtpChallengeRepository challenges, AppUserRepository users,
                          SmsProviderService smsProvider, BrevoEmailService emailService,
                          Environment environment,
                          @Value("${app.bank-otp.provider:${app.sms.provider:none}}") String provider,
                          @Value("${app.otp.hmac-secret:}") String hmacSecret,
                          @Value("${app.otp.allow-email-for-bank-link:false}") boolean allowEmailInProduction) {
        this.challenges = challenges;
        this.users = users;
        this.smsProvider = smsProvider;
        this.emailService = emailService;
        this.environment = environment;
        this.provider = provider == null ? "none" : provider.trim().toLowerCase(Locale.ROOT);
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret;
        this.allowEmailInProduction = allowEmailInProduction;
    }

    @PostConstruct
    void validateProviderSafety() {
        if (!provider.equals("twilio") && !provider.equals("email") && !provider.equals("none")) {
            throw new IllegalStateException("Unsupported app.sms.provider; use twilio, email, or none.");
        }
        if (!provider.equals("email")) return;
        log.warn("Development bank-link OTP email provider is active. Do not use this provider for production OTP delivery.");
        boolean productionProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
        boolean productionEnvironment = "production".equalsIgnoreCase(
                environment.getProperty("app.environment", ""));
        if ((productionProfile || productionEnvironment) && !allowEmailInProduction) {
            throw new IllegalStateException("Bank-link email OTP is disabled in production. Set app.otp.allow-email-for-bank-link=true only after explicit approval.");
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public SendOtpResponse send(AppUser authenticatedUser, String mobile, UUID previousChallengeId) {
        AppUser user = users.findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String normalizedMobile = MobileNumberNormalizer.normalize(mobile);
        if (!normalizedMobile.equals(MobileNumberNormalizer.normalizeStored(user.getPhone()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enter the mobile number registered on your account.");
        }
        ensureProviderConfigured();

        Instant now = Instant.now();
        List<BankOtpChallenge> openChallenges = challenges.findOpenChallengesForUpdate(user.getId());
        BankOtpChallenge current = openChallenges.isEmpty() ? null : openChallenges.get(0);
        if (previousChallengeId != null
                && (current == null || !current.getId().equals(previousChallengeId))) {
            throw new ResponseStatusException(HttpStatus.GONE, "OTP request expired. Request a new code.");
        }
        if (current != null) {
            if (!current.getExpiresAt().isAfter(now)) {
                current.consume();
                challenges.save(current);
                if (previousChallengeId != null) {
                    throw new ResponseStatusException(HttpStatus.GONE,
                            "OTP request expired. Request a new code.");
                }
                current = null;
            } else if (now.isBefore(current.getResendAfter())) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Wait 65 seconds before requesting another code.");
            } else if (current.getSendCount() >= MAX_SENDS_PER_CHALLENGE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "This OTP request reached its resend limit. Request a new code later.");
            }
        }
        if (current == null && challenges.countByOwnerUserIdAndCreatedAtAfter(
                user.getId(), now.minus(1, ChronoUnit.HOURS)) >= MAX_CHALLENGES_PER_HOUR) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many OTP requests. Try again later.");
        }

        String mode = provider.equals("email") ? "EMAIL" : "SMS";
        String code = null;
        if (provider.equals("email")) {
            code = generateOtp();
            try {
                emailService.sendBankLinkOtp(user.getEmail(), code);
            } catch (RuntimeException failure) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Email delivery is unavailable. Try again later.");
            }
        } else {
            try {
                smsProvider.sendVerification("+91" + normalizedMobile);
            } catch (RuntimeException failure) {
                throw failure;
            }
        }

        Instant expiresAt = now.plusSeconds(OTP_LIFETIME_SECONDS);
        Instant resendAfter = now.plusSeconds(RESEND_WAIT_SECONDS);
        if (current == null) current = new BankOtpChallenge(user.getId(), now, expiresAt, resendAfter);
        else current.resend(now, expiresAt, resendAfter);
        if (code != null) current.setOtpHash(hmac(code, hmacSecret));
        BankOtpChallenge saved = challenges.save(current);
        String destination = mode.equals("EMAIL") ? maskEmail(user.getEmail()) : maskPhone(user.getPhone());
        return new SendOtpResponse(saved.getId(), OTP_LIFETIME_SECONDS, RESEND_WAIT_SECONDS, mode, destination);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verify(UUID userId, String registeredMobile, UUID challengeId, String otp) {
        BankOtpChallenge challenge = getCurrentChallenge(userId, challengeId);
        Instant now = Instant.now();
        if (!challenge.getExpiresAt().isAfter(now)) {
            challenge.consume(); challenges.save(challenge);
            throw new ResponseStatusException(HttpStatus.GONE, "OTP expired. Request a new code.");
        }
        if (challenge.getAttempts() >= MAX_ATTEMPTS) {
            challenge.consume(); challenges.save(challenge); throw tooManyAttempts();
        }
        if (challenge.isVerified()) return;
        boolean approved = provider.equals("email")
                ? verifyEmailCode(challenge.getOtpHash(), otp)
                : smsProvider.verifyCode("+91" + MobileNumberNormalizer.normalize(registeredMobile), otp);
        if (!approved) {
            challenge.recordFailedAttempt();
            if (challenge.getAttempts() >= MAX_ATTEMPTS) {
                challenge.consume(); challenges.save(challenge); throw tooManyAttempts();
            }
            challenges.save(challenge);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "OTP is incorrect. " + (MAX_ATTEMPTS - challenge.getAttempts()) + " attempts remain.");
        }
        challenge.markVerified(); challenges.save(challenge);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void consumeVerified(UUID userId, UUID challengeId) {
        BankOtpChallenge challenge = getCurrentChallenge(userId, challengeId);
        Instant verifiedAt = challenge.getVerifiedAt();
        if (!challenge.isVerified() || verifiedAt == null || verifiedAt.isBefore(Instant.now().minusSeconds(600)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Verify the registered mobile number within the last 10 minutes before linking an account.");
        challenge.consume(); challenges.save(challenge);
    }

    public boolean isProviderConfigured() {
        return switch (provider) {
            case "twilio" -> smsProvider.isConfigured();
            case "email" -> emailService.isConfigured() && hmacSecret.getBytes(StandardCharsets.UTF_8).length >= 32;
            default -> false;
        };
    }

    public String providerName() { return provider.equals("email") ? "Brevo email (development only)" : provider.equals("twilio") ? "Twilio Verify" : "OTP provider"; }
    public String verificationMessage() { return provider.equals("email")
            ? "Registered email verified." : "Mobile number verified by the SMS provider."; }
    public String configurationDetail() { return provider.equals("email")
            ? "Development email OTP uses the registered account email and expires after 65 seconds."
            : provider.equals("twilio") ? "Requires an approved Verify service and production credentials."
            : "Requires a configured OTP delivery provider."; }

    static String generateOtp() { return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000)); }
    static String hmac(String otp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(otp.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception error) { throw new IllegalStateException("OTP verification is unavailable."); }
    }
    static boolean verifyEmailCode(String expectedHash, String otp, String secret) {
        if (expectedHash == null || otp == null || !otp.matches("[0-9]{6}")) return false;
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                hmac(otp, secret).getBytes(StandardCharsets.US_ASCII));
    }
    private boolean verifyEmailCode(String hash, String otp) { return verifyEmailCode(hash, otp, hmacSecret); }
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "registered email";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return (local.isEmpty() ? "*" : local.substring(0, 1) + "***") + email.substring(at);
    }
    private static String maskPhone(String phone) {
        String digits = MobileNumberNormalizer.normalizeStored(phone);
        return digits.length() < 4 ? "registered mobile" : "••••••" + digits.substring(digits.length() - 4);
    }

    private BankOtpChallenge getCurrentChallenge(UUID userId, UUID challengeId) {
        BankOtpChallenge challenge = challenges.findByIdAndOwnerUserId(challengeId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "OTP request expired. Request a new code."));
        if (challenge.isConsumed()) throw new ResponseStatusException(HttpStatus.GONE,
                "OTP request expired. Request a new code.");
        return challenge;
    }
    private void ensureProviderConfigured() {
        if (!isProviderConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Mobile verification is pending provider configuration.");
    }
    private static ResponseStatusException tooManyAttempts() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect OTP attempts. Request a new code.");
    }
}
