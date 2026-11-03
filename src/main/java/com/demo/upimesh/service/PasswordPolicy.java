package com.demo.upimesh.service;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static boolean isValid(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.length() >= 8 && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*") && password.matches(".*[0-9].*")
                && password.matches(".*[^A-Za-z0-9].*");
    }
    public static void requireValid(String password) {
        if (!isValid(password)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Password must be 8-72 UTF-8 bytes and include uppercase, lowercase, a number, and a special character.");
    }
}
