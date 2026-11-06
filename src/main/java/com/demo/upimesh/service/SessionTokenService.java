package com.demo.upimesh.service;

import com.demo.upimesh.model.AppSession;
import com.demo.upimesh.model.AppSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class SessionTokenService {
    private static final Logger log = LoggerFactory.getLogger(SessionTokenService.class);
    private final AppSessionRepository sessions;
    private final TrustedDeviceService trustedDevices;
    private final SecureRandom random = new SecureRandom();

    @Value("${upi.app-lock.idle-timeout-seconds:300}")
    private long appLockIdleTimeoutSeconds;

    @Value("${upi.auth.session-ttl-seconds:43200}")
    private long sessionTtlSeconds;

    public SessionTokenService(AppSessionRepository sessions, TrustedDeviceService trustedDevices) {
        this.sessions = sessions;
        this.trustedDevices = trustedDevices;
    }

    public String issue(String userId) {
        return issue(userId, null);
    }

    public String issue(String userId, String deviceId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = hash(rawToken);
        sessions.save(new AppSession(tokenHash, userId, Instant.now().plusSeconds(sessionTtlSeconds), deviceId));
        log.info("Issued session fingerprint={} deviceBound={}", fingerprint(tokenHash), deviceId != null);
        return rawToken;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issueForLogin(String userId, String deviceId) {
        return issue(userId, deviceId);
    }
    public void bindDevice(String rawToken, String userId, String deviceId) {
        if (rawToken == null || rawToken.isBlank()) throw new IllegalArgumentException("A valid session is required.");
        String tokenHash = hash(rawToken);
        AppSession session = sessions.findById(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("The current session is no longer valid."));
        if (!session.getUserId().equals(userId) || session.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("The current session is no longer valid.");
        }
        sessions.save(new AppSession(tokenHash, userId, session.getExpiresAt(), deviceId));
    }

    public Optional<String> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        String tokenHash = hash(rawToken);
        AppSession session = sessions.findById(tokenHash).orElse(null);
        if (session == null) {
            log.warn("Bearer rejected: no matching session record, fingerprint={}", fingerprint(tokenHash));
            return Optional.empty();
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            sessions.deleteById(tokenHash);
            return Optional.empty();
        }
        if (session.getDeviceId() != null && !trustedDevices.isActive(session.getUserId(), session.getDeviceId())) {
            log.warn("Bearer rejected: trusted device inactive, fingerprint={}", fingerprint(tokenHash));
            sessions.deleteById(tokenHash);
            return Optional.empty();
        }
        return Optional.of(session.getUserId());
    }

    public boolean isAppLockVerified(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        return sessions.findById(hash(rawToken)).map(AppSession::isAppLockVerified).orElse(false);
    }

    @Transactional
    public boolean isAppLockVerifiedAndTouch(String rawToken, String requestPath, String requestMethod) {
        if (rawToken == null || rawToken.isBlank()) return false;
        AppSession session = sessions.findById(hash(rawToken)).orElse(null);
        if (session == null || !session.isAppLockVerified()) return false;
        Instant now = Instant.now();
        if (session.isAppLockVerifiedExpired(now, Math.max(1, appLockIdleTimeoutSeconds))) {
            session.setAppLockVerified(false);
            return false;
        }
        if (!isPollingRequest(requestMethod, requestPath) && session.shouldRecordAppLockActivity(now, 30)) {
            session.recordAppLockActivity(now);
        }
        return true;
    }

    private boolean isPollingRequest(String method, String path) {
        return "GET".equalsIgnoreCase(method)
                && ("/api/privacy-account".equals(path)
                || "/api/notifications/unread-count".equals(path));
    }

    @Transactional
    public void setAppLockVerified(String rawToken, String userId, boolean verified) {
        if (rawToken == null || rawToken.isBlank()) throw new IllegalArgumentException("A valid session is required.");
        AppSession session = sessions.findById(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("The current session is no longer valid."));
        if (!session.getUserId().equals(userId) || session.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("The current session is no longer valid.");
        }
        session.setAppLockVerified(verified);
    }

    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) sessions.deleteById(hash(rawToken));
    }

    @Transactional
    public void revokeAllForUser(String userId) {
        sessions.deleteAllByUserId(userId);
    }

    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void removeExpired() {
        sessions.deleteAllByExpiresAtBefore(Instant.now());
    }

    private String fingerprint(String tokenHash) {
        return tokenHash.substring(0, 8);
    }
    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

}
