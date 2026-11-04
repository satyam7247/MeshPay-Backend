package com.demo.upimesh.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AttemptRateLimiter {
    private static final int MAX_KEYS = 20000;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    public synchronized void check(String key, int maximum, long windowSeconds) {
        Instant now = Instant.now();
        if (windows.size() >= MAX_KEYS) {
            Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
            while (it.hasNext()) if (it.next().getValue().expiresAt.isBefore(now)) it.remove();
            if (windows.size() >= MAX_KEYS) throw limited();
        }
        Window window = windows.get(key);
        if (window == null || window.expiresAt.isBefore(now)) {
            window = new Window(now.plusSeconds(windowSeconds), 0); windows.put(key, window);
        }
        if (window.count >= maximum) throw limited();
        window.count++;
    }
    public synchronized void clear(String key) { windows.remove(key); }
    private static ResponseStatusException limited() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again in 15 minutes.") {
            @Override public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Retry-After", "900");
                return HttpHeaders.readOnlyHttpHeaders(headers);
            }
        };
    }
    private static final class Window { final Instant expiresAt; int count; Window(Instant expiresAt,int count){this.expiresAt=expiresAt;this.count=count;} }
}
