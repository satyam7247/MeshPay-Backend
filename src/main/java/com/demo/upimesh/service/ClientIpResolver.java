package com.demo.upimesh.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {
    private final List<String> trusted;
    public ClientIpResolver(@Value("${app.security.trusted-proxy-addresses:}") String addresses) {
        trusted = Arrays.stream(addresses.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || !trusted.contains(remote)) return remote == null ? "unknown" : remote;
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) return remote;
        List<String> chain = Arrays.stream(header.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        String current = remote;
        for (int i = chain.size() - 1; i >= 0 && trusted.contains(current); i--) current = chain.get(i);
        return current;
    }
}
