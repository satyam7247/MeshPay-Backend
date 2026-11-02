package com.demo.upimesh.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class RecipientRateLimiter {
    private static final int MAX_LOOKUPS = 30;
    private static final long WINDOW_SECONDS = 60;
    private final Map<UUID, ArrayDeque<Instant>> windows = new HashMap<>();

    public synchronized void check(UUID userId) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
        windows.values().forEach(attempts -> {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) attempts.removeFirst();
        });
        windows.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        ArrayDeque<Instant> attempts = windows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        if (attempts.size() >= MAX_LOOKUPS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Recipient lookup limit reached. Try again shortly.");
        }
        attempts.addLast(now);
    }
}
