package com.demo.upimesh.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class MobileNumberNormalizer {
    private MobileNumberNormalizer() {}

    public static String normalize(String input) {
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit mobile number.");
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        else if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        if (!digits.matches("[0-9]{10}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit mobile number.");
        }
        return digits;
    }

    public static String normalizeStored(String input) {
        if (input == null) return "";
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) return digits.substring(1);
        return digits;
    }
}
