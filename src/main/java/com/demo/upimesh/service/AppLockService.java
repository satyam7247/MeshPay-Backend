package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppLockService {
    private static final String NUMERIC = "numeric";
    private static final String ALPHABETIC = "alphabetic";
    private final AppUserRepository users;
    private final PasswordEncoder passwords;

    public AppLockService(AppUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public Status status(AppUser principal) {
        AppUser user = users.findById(principal.getId()).orElseThrow(AppLockService::unavailable);
        return new Status(user.isAppLockEnabled(), user.isAppLockEnabled() ? user.getAppLockType() : null);
    }

    @Transactional
    public Status enable(AppUser principal, String type, String password) {
        validatePassword(type, password);
        AppUser user = lockUser(principal.getId());
        if (user.isAppLockEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "App Lock is already enabled.");
        user.enableAppLock(type, passwords.encode(password));
        return new Status(true, type);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Status verify(AppUser principal, String password) {
        AppUser user = lockUser(principal.getId());
        verifyPassword(user, password);
        user.clearAppLockFailures();
        return new Status(true, user.getAppLockType());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Status disable(AppUser principal, String password) {
        AppUser user = lockUser(principal.getId());
        verifyPassword(user, password);
        user.disableAppLock();
        return new Status(false, null);
    }

    private void verifyPassword(AppUser user, String password) {
        if (!user.isAppLockEnabled() || user.getAppLockPasswordHash() == null) throw unavailable();
        Instant now = Instant.now();
        Instant lockedUntil = user.getAppLockLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now)) throw limited();
        if (lockedUntil != null) user.clearAppLockFailures();
        boolean shapeValid = validPassword(user.getAppLockType(), password);
        boolean matches = passwords.matches(password == null ? "" : password, user.getAppLockPasswordHash());
        if (!shapeValid || !matches) {
            if (user.recordFailedAppLockAttempt(now) >= 5) throw limited();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect Password. Please Try Again.");
        }
    }

    private AppUser lockUser(UUID id) {
        return users.findByIdForUpdate(id).orElseThrow(AppLockService::unavailable);
    }

    private static boolean validPassword(String type, String password) {
        if (password == null || password.length() != 6) return false;
        if (NUMERIC.equals(type)) return password.matches("[0-9]{6}");
        return ALPHABETIC.equals(type) && password.matches("[A-Za-z]{6}");
    }

    private static void validatePassword(String type, String password) {
        if (!validPassword(type, password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Screen lock password must match the selected six-character password type.");
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "App Lock is not available for this account.");
    }

    private static ResponseStatusException limited() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect screen lock attempts. Try again in 15 minutes.");
    }

    public record Status(boolean enabled, String type) {}
}
