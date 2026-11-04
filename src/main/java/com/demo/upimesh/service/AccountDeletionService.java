package com.demo.upimesh.service;

import com.demo.upimesh.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class AccountDeletionService {
    private static final Logger log=LoggerFactory.getLogger(AccountDeletionService.class);
    private static final int MAX_ATTEMPTS=5;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String PENDING_MESSAGE="Your account cannot be deleted while its simulated payment balance is non-zero. Resolve it and try again.";
    private final AccountDeletionRequestRepository requests;
    private final AccountDeletionOtpChallengeRepository challenges;
    private final AppUserRepository users;
    private final SmsProviderService sms;
    private final SessionTokenService sessions;
    private final BankAccountRepository bankAccounts;
    private final TrustedDeviceRepository devices;
    private final NotificationPushTokenRepository notificationPushTokens;
    private final SavedRecipientRepository recipients;
    private final BankOtpChallengeRepository bankChallenges;
    private final PasswordChallengeRepository passwordChallenges;
    private final AccountRepository ledger;
    private final TransactionRepository transactions;
    private final BrevoEmailService email;
    private final Environment environment;
    private final String otpProvider;
    private final String hmacSecret;
    private final boolean allowEmailInProduction;

    public AccountDeletionService(AccountDeletionRequestRepository requests, AccountDeletionOtpChallengeRepository challenges,
            AppUserRepository users, SmsProviderService sms, SessionTokenService sessions,
            BankAccountRepository bankAccounts, TrustedDeviceRepository devices, NotificationPushTokenRepository notificationPushTokens, SavedRecipientRepository recipients,
            BankOtpChallengeRepository bankChallenges, PasswordChallengeRepository passwordChallenges,
            AccountRepository ledger, TransactionRepository transactions, BrevoEmailService email,
            Environment environment,
            @Value("${app.account-deletion-otp.provider:${app.sms.provider:none}}") String otpProvider,
            @Value("${app.otp.hmac-secret:}") String hmacSecret,
            @Value("${app.otp.allow-email-for-account-deletion:false}") boolean allowEmailInProduction) {
        this.requests=requests; this.challenges=challenges; this.users=users; this.sms=sms; this.sessions=sessions;
        this.bankAccounts=bankAccounts; this.devices=devices; this.notificationPushTokens=notificationPushTokens; this.recipients=recipients;
        this.bankChallenges=bankChallenges; this.passwordChallenges=passwordChallenges; this.ledger=ledger; this.transactions=transactions;
        this.email=email; this.environment=environment;
        this.otpProvider=otpProvider==null?"none":otpProvider.trim().toLowerCase(Locale.ROOT);
        this.hmacSecret=hmacSecret==null?"":hmacSecret;
        this.allowEmailInProduction=allowEmailInProduction;
    }

    @PostConstruct
    void validateOtpProviderSafety() {
        if(!otpProvider.equals("twilio")&&!otpProvider.equals("email")&&!otpProvider.equals("none"))
            throw new IllegalStateException("Unsupported account deletion OTP provider; use twilio, email, or none.");
        if(!otpProvider.equals("email")) return;
        log.warn("Development account-deletion OTP email provider is active. Do not use this provider for production OTP delivery.");
        boolean productionProfile=Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile->profile.equalsIgnoreCase("prod")||profile.equalsIgnoreCase("production"));
        boolean productionEnvironment="production".equalsIgnoreCase(environment.getProperty("app.environment",""));
        if((productionProfile||productionEnvironment)&&!allowEmailInProduction)
            throw new IllegalStateException("Account-deletion email OTP is disabled in production unless app.otp.allow-email-for-account-deletion=true.");
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public OtpResponse sendOtp(AppUser user, UUID previousId) {
        ensureOtpProviderConfigured();
        AppUser account=users.findByIdForUpdate(user.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account is unavailable."));
        if(requests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(account.getId(),AccountDeletionRequest.Status.PENDING_DELETION).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A deletion request is already pending for this account.");
        Instant now=Instant.now();
        List<AccountDeletionOtpChallenge> open=challenges.findOpenForUpdate(account.getId());
        AccountDeletionOtpChallenge current=open.isEmpty()?null:open.get(0);
        if(previousId!=null&&(current==null||!current.getId().equals(previousId))) throw new ResponseStatusException(HttpStatus.GONE,"OTP request expired. Request a new code.");
        if(current!=null&&!current.isConsumed()&&current.getExpiresAt().isAfter(now)&&now.isBefore(current.getResendAfter()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Wait 60 seconds before requesting another code.");
        String code=null;
        String mode=otpProvider.equals("email")?"EMAIL":"SMS";
        if(otpProvider.equals("email")) {
            code=generateOtp();
            try { email.sendAccountDeletionOtp(account.getEmail(),code); }
            catch(RuntimeException failure) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email delivery is unavailable. Try again later.");
            }
        } else {
            String normalized=MobileNumberNormalizer.normalize(account.getPhone());
            sms.sendVerification("+91"+normalized);
        }
        if(current!=null) { current.consume(); challenges.save(current); }
        AccountDeletionOtpChallenge saved=new AccountDeletionOtpChallenge(account.getId(),now);
        if(code!=null) saved.setOtpHash(hmac(code,hmacSecret));
        saved=challenges.save(saved);
        String destination=mode.equals("EMAIL")?maskEmail(account.getEmail()):mask(MobileNumberNormalizer.normalize(account.getPhone()));
        return new OtpResponse(saved.getId(),mode,destination,60,60);
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public DeletionResponse verifyAndRequest(AppUser user, UUID challengeId, String code) {
        AppUser account=users.findByIdForUpdate(user.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account is unavailable."));
        AccountDeletionOtpChallenge challenge=challenges.findOwnedForUpdate(challengeId,account.getId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.GONE,"OTP request expired. Request a new code."));
        Instant now=Instant.now();
        if(challenge.isConsumed()||!challenge.getExpiresAt().isAfter(now)) {
            challenge.consume(); challenges.save(challenge);
            throw new ResponseStatusException(HttpStatus.GONE,"OTP expired. Request a new code.");
        }
        if(challenge.getAttempts()>=MAX_ATTEMPTS) { challenge.consume(); challenges.save(challenge); throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many incorrect OTP attempts. Request a new code."); }
        boolean approved=otpProvider.equals("email")
                ?verifyEmailOtp(challenge.getOtpHash(),code,hmacSecret)
                :sms.verifyCode("+91"+MobileNumberNormalizer.normalize(account.getPhone()),code);
        if(!approved) {
            challenge.failed();
            if(challenge.getAttempts()>=MAX_ATTEMPTS) {
                challenge.consume(); challenges.save(challenge);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many incorrect OTP attempts. Request a new code.");
            }
            challenges.save(challenge);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"OTP is incorrect. Please check the code and try again.");
        }
        challenge.consume();
        Optional<AccountDeletionRequest> existing=requests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(account.getId(),AccountDeletionRequest.Status.PENDING_DELETION);
        if(existing.isPresent()) return response(existing.get());
        if(hasUnresolvedPayments(account)) throw new ResponseStatusException(HttpStatus.CONFLICT,PENDING_MESSAGE);
        AccountDeletionRequest request=requests.save(new AccountDeletionRequest(account.getId(),now,account.isAccountLocked(),emailKey(account.getEmail())));
        sessions.revokeAllForUser(account.getId().toString());
        account.lockAccount();
        challenges.save(challenge);
        return response(request);
    }

    @Transactional(readOnly=true)
    public DeletionResponse status(AppUser user) {
        return requests.findFirstByUserIdAndStatusOrderByRequestCreatedAtDesc(user.getId(),AccountDeletionRequest.Status.PENDING_DELETION)
                .map(this::response).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelForLogin(UUID userId) {
        AppUser user=users.findByIdForUpdate(userId).orElse(null);
        if(user==null) return false;
        AccountDeletionRequest req=requests.findByUserIdAndStatusForUpdate(userId,AccountDeletionRequest.Status.PENDING_DELETION).orElse(null);
        if(req==null) return false;
        if(!req.getScheduledDeletionAt().isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.GONE,"Account deletion is being processed. Sign-in is no longer available.");
        req.cancel(Instant.now());
        if(req.wasAccountLocked()) user.lockAccount(); else user.unlockAccount();
        return true;
    }

    @Scheduled(fixedDelay=30000)
    @Transactional
    public void processDueDeletions() {
        Instant now=Instant.now();
        List<AccountDeletionRequest> due=requests.findAllByStatusAndScheduledDeletionAtLessThanEqual(AccountDeletionRequest.Status.PENDING_DELETION,now);
        for(AccountDeletionRequest candidate:due) {
            AppUser user=users.findByIdForUpdate(candidate.getUserId()).orElse(null);
            if(user==null) continue;
            AccountDeletionRequest req=requests.findByIdForUpdate(candidate.getId()).orElse(null);
            if(req==null||req.getStatus()!=AccountDeletionRequest.Status.PENDING_DELETION||req.getScheduledDeletionAt().isAfter(now)) continue;
            if(hasUnresolvedPayments(user)) continue;
            String id=user.getId().toString();
            UUID userId=user.getId();
            sessions.revokeAllForUser(id);
            notificationPushTokens.deleteAllByOwnerUserId(userId);
            bankAccounts.deleteAllByOwnerUserId(userId);
            devices.deleteAllByOwnerUserId(userId);
            recipients.deleteAllByOwnerUserId(userId);
            recipients.deleteAllByRecipientUserId(userId);
            bankChallenges.deleteAllByOwnerUserId(userId);
            challenges.deleteAllByUserId(userId);
            passwordChallenges.deleteAllByUserId(id);
            String deletedName="Deleted account";
            String replacementVpa="deleted."+id+"@deleted.invalid";
            transactions.anonymizeSenderVpa(user.getUpiId(),replacementVpa);
            transactions.anonymizeReceiverVpa(user.getUpiId(),replacementVpa);
            user.anonymize(deletedName, "deleted+"+id+"@deleted.invalid", "+1000000"+user.getId().toString().replace("-","").substring(0,8));
            Account account=ledger.findById(user.getUpiId()).orElse(null);
            if(account!=null){account.setHolderName(deletedName);account.setPinHash(null);}
            req.markDeleted(now);
        }
    }

    @Transactional
    public boolean isDeletedEmail(String email) {
        String normalized=email.trim().toLowerCase(Locale.ROOT);
        String keyed=emailKey(normalized);
        if(requests.existsByDeletedEmailKey(keyed)) return true;
        // Migrate legacy plain-SHA rows in place on first lookup.
        String legacy=legacyEmailKey(normalized);
        List<AccountDeletionRequest> old=requests.findAllByDeletedEmailKey(legacy);
        for(AccountDeletionRequest row:old) row.migrateDeletedEmailKey(keyed);
        return !old.isEmpty();
    }
    public String messageForDeletedEmail(String email) {
        return isDeletedEmail(email)?"Your account has been permanently deleted. You cannot log in using this account.":null;
    }
    private void ensureOtpProviderConfigured() {
        boolean configured=otpProvider.equals("email")
                ?email.isConfigured()&&hmacSecret.getBytes(StandardCharsets.UTF_8).length>=32
                :otpProvider.equals("twilio")&&sms.isConfigured();
        if(!configured) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Account deletion verification is pending provider configuration.");
    }
    static String generateOtp(){return String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000));}
    static String hmac(String code,String secret){
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("account-deletion:"+code).getBytes(StandardCharsets.US_ASCII)));
        } catch(Exception error){throw new IllegalStateException("OTP verification is unavailable.");}
    }
    static boolean verifyEmailOtp(String expectedHash,String code,String secret){
        if(expectedHash==null||code==null||!code.matches("[0-9]{6}"))return false;
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),hmac(code,secret).getBytes(StandardCharsets.US_ASCII));
    }
    private boolean hasUnresolvedPayments(AppUser user) {
        // Transactions are written only as SETTLED or REJECTED; synced offline logs are client audit records.
        Account account=ledger.findById(user.getUpiId()).orElse(null);
        return account!=null&&account.getBalance()!=null&&account.getBalance().signum()!=0;
    }
    private DeletionResponse response(AccountDeletionRequest r) {
        long seconds=Math.max(0,r.getScheduledDeletionAt().getEpochSecond()-Instant.now().getEpochSecond());
        return new DeletionResponse(r.getId(),r.getStatus().name(),r.getRequestCreatedAt(),r.getScheduledDeletionAt(),seconds);
    }
    private static String mask(String mobile) { return "XXXX"+mobile.substring(Math.max(0,mobile.length()-4)); }
    private static String maskEmail(String email){
        if(email==null||!email.contains("@"))return "registered email";
        int at=email.indexOf('@'); String local=email.substring(0,at);
        return (local.isEmpty()?"*":local.substring(0,1)+"***")+email.substring(at);
    }
    private String emailKey(String email) {
        if (hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Deleted-email protection requires a configured server secret.");
        }
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e){throw new IllegalStateException("Deleted-email protection is unavailable.",e);}
    }
    private static String legacyEmailKey(String email) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException("SHA-256 is unavailable",e);}
    }
    public record OtpResponse(UUID challengeId,String deliveryMode,String maskedDestination,int expiresInSeconds,int resendAfterSeconds){}
    public record DeletionResponse(UUID requestId,String status,Instant requestCreatedAt,Instant scheduledDeletionAt,long remainingSeconds){}
}











