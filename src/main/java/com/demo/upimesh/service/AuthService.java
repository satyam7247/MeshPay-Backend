package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final SessionTokenService tokens;
    private final TrustedDeviceService trustedDevices;
    private final AccountDeletionService deletion;
    private final AttemptRateLimiter limiter;
    private static final String DUMMY_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    public AuthService(AppUserRepository users, AccountRepository accounts,
                       PasswordEncoder passwords, SessionTokenService tokens,
                       TrustedDeviceService trustedDevices, AccountDeletionService deletion, AttemptRateLimiter limiter) {
        this.users = users;
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.trustedDevices = trustedDevices;
        this.deletion = deletion;
        this.limiter = limiter;
    }

    public AuthService(AppUserRepository users, AccountRepository accounts, PasswordEncoder passwords,
                       SessionTokenService tokens, TrustedDeviceService trustedDevices, AccountDeletionService deletion) {
        this(users, accounts, passwords, tokens, trustedDevices, deletion, new AttemptRateLimiter());
    }

    @Transactional
    public AuthResponse signup(String name, String email, String phone, String password) {
        return signup(name, email, phone, password, null, null, null);
    }

    @Transactional
    public AuthResponse signup(String name, String email, String phone, String password,
                               String deviceId, String deviceName, String platform) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = phone.trim();
        String phoneLookupKey = MobileNumberNormalizer.normalizeStored(normalizedPhone);
        PasswordPolicy.requireValid(password);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        if (users.existsByPhone(normalizedPhone) || users.existsByPhoneNormalized(phoneLookupKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this phone number already exists.");
        }

        String upiId = createUpiId(normalizedEmail);
        String safeName = name.trim();
        AppUser user = users.save(new AppUser(safeName, normalizedEmail, normalizedPhone,
                passwords.encode(password), upiId));
        // This is an internal simulation ledger, not a connected bank balance.
        accounts.save(new Account(upiId, safeName, BigDecimal.ZERO.setScale(2)));
        return issueSession(user, deviceId, deviceName, platform, true, false);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        return login(email, password, null, null, null);
    }

    @Transactional
    public AuthResponse login(String email, String password, String deviceId, String deviceName, String platform) {
        return login(email, password, deviceId, deviceName, platform, "unknown");
    }

    @Transactional
    public AuthResponse login(String email, String password, String deviceId, String deviceName, String platform, String remoteAddress) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String emailKey = "login:email:" + normalized;
        String ipKey = "login:ip:" + (remoteAddress == null ? "unknown" : remoteAddress);
        limiter.check(emailKey, 5, 900); limiter.check(ipKey, 5, 900);
        AppUser user = users.findByEmailIgnoreCase(normalized).orElse(null);
        boolean valid = passwords.matches(password == null ? "" : password,
                user == null ? DUMMY_HASH : user.getPasswordHash());
        String deletedMessage = user == null ? deletion.messageForDeletedEmail(normalized) : null;
        if (user == null) {
            limiter.check("login-deleted:ip:" + (remoteAddress == null ? "unknown" : remoteAddress), 5, 900);
            if (deletedMessage != null) throw new ResponseStatusException(HttpStatus.GONE, deletedMessage);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        if (!valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        limiter.clear(emailKey); limiter.clear(ipKey);
        boolean deletionCancelled = deletion.cancelForLogin(user.getId());
        return issueSession(user, deviceId, deviceName, platform, false, deletionCancelled);
    }

    private AuthResponse issueSession(AppUser user, String deviceId, String deviceName, String platform, boolean newUser, boolean deletionCancelled) {
        String registeredDeviceId;
        try {
            registeredDeviceId = newUser
                    ? trustedDevices.registerNewUserDevice(user, deviceId, deviceName, platform)
                    : trustedDevices.registerPasswordVerifiedDevice(user, deviceId, deviceName, platform);
        } catch (DataIntegrityViolationException conflict) {
            if (newUser || !isTrustedDeviceConflict(conflict)) throw conflict;
            try {
                registeredDeviceId = trustedDevices.registerPasswordVerifiedDevice(user, deviceId, deviceName, platform);
            } catch (DataIntegrityViolationException retryConflict) {
                if (!isTrustedDeviceConflict(retryConflict)) throw retryConflict;
                // Password verification has already succeeded; recover from a stale device-key collision.
                registeredDeviceId = trustedDevices.registerPasswordVerifiedDevice(
                        user, UUID.randomUUID().toString(), deviceName, platform);
            }
        }
        String token = newUser
                ? tokens.issue(user.getId().toString(), registeredDeviceId)
                : tokens.issueForLogin(user.getId().toString(), registeredDeviceId);
        return new AuthResponse(token, profile(user), registeredDeviceId, deletionCancelled);
    }

    private boolean isTrustedDeviceConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("uk_trusted_device_owner_id")) return true;
        }
        return false;
    }

    public UserProfile profile(AppUser user) {
        return new UserProfile(user.getId().toString(), user.getName(), user.getEmail(),
                user.getPhone(), user.getUpiId());
    }

    public boolean verifyCurrentPassword(AppUser user, String password) { return verifyCurrentPassword(user,password,"unknown"); }
    public boolean verifyCurrentPassword(AppUser user, String password, String remoteAddress) {
        String ip=remoteAddress==null?"unknown":remoteAddress;
        limiter.check("device-register:user:"+user.getId(),5,900);
        limiter.check("device-register:ip:"+ip,5,900);
        boolean valid=password!=null && passwords.matches(password,user.getPasswordHash());
        if(valid) { limiter.clear("device-register:user:"+user.getId()); limiter.clear("device-register:ip:"+ip); }
        return valid;
    }

    private String createUpiId(String email) {
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        if (localPart.isBlank()) localPart = "user";
        if (localPart.length() > 18) localPart = localPart.substring(0, 18);
        String candidate;
        do {
            candidate = localPart + "." + UUID.randomUUID().toString().substring(0, 6) + "@meshpay";
        } while (users.existsByUpiId(candidate));
        return candidate;
    }

    public record UserProfile(String id, String name, String email, String phone, String upiId) {}
    public record AuthResponse(String token, UserProfile user, String deviceId, boolean deletionCancelled) {}
}









