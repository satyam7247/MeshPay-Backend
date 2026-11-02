package com.demo.upimesh.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class PaymentWebhookVerifier {
    private final String secret;

    public PaymentWebhookVerifier(
            @Value("${app.payments.razorpay.webhook-secret:}") String secret) {
        this.secret = secret;
    }

    public void verify(byte[] rawBody, String signature) {
        if (secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment webhook verification is pending provider setup.");
        }
        if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Payment webhook signature is invalid.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody);
            byte[] received = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, received)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Payment webhook signature is invalid.");
            }
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Payment webhook signature verification is unavailable.", error);
        }
    }
}
