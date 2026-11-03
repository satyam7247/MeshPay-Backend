package com.demo.upimesh.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BrevoEmailServiceTest {
    @Test
    void paymentReceivedUsesConfiguredFromNameAndEscapesAndLimitsPayerName() {
        BrevoEmailService service = new BrevoEmailService();
        ReflectionTestUtils.setField(service, "senderEmail", "verified@example.test");
        ReflectionTestUtils.setField(service, "senderName", "MeshPay Notifications");
        String payerName = "<script>alert(1)</script>" + "x".repeat(100);

        Map<String, Object> message = service.paymentReceivedMessage("recipient@example.test", payerName,
                new BigDecimal("12.50"), 42L, "2026-11-03", "SETTLED");

        @SuppressWarnings("unchecked")
        Map<String, String> sender = (Map<String, String>) message.get("sender");
        assertEquals("MeshPay Notifications", sender.get("name"));
        String html = (String) message.get("htmlContent");
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;" + "x".repeat(55)));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("x".repeat(56)));
    }
}
