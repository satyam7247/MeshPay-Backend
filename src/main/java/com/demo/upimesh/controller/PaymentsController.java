package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.PaymentService;
import com.demo.upimesh.service.PaymentWebhookVerifier;
import com.demo.upimesh.service.QRCodeService;
import com.demo.upimesh.service.PrivacyAccountService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentsController {
    private final PaymentService payments;
    private final QRCodeService qrCodes;
    private final PaymentWebhookVerifier webhookVerifier;
    private final PrivacyAccountService privacy;

    public PaymentsController(PaymentService payments, QRCodeService qrCodes,
                              PaymentWebhookVerifier webhookVerifier, PrivacyAccountService privacy) {
        this.payments = payments;
        this.qrCodes = qrCodes;
        this.webhookVerifier = webhookVerifier;
        this.privacy = privacy;
    }

    @PostMapping("/prepare")
    public void prepare(@AuthenticationPrincipal AppUser currentUser,
                        @RequestBody(required = false) Map<String, Object> paymentData) {
        privacy.requireUnlocked(currentUser.getId());
        payments.requireApprovedPaymentProvider();
    }

    @PostMapping("/qr/generate")
    public void generateQr(@AuthenticationPrincipal AppUser currentUser) {
        privacy.requireUnlocked(currentUser.getId());
        qrCodes.generate(currentUser.getId());
    }

    @GetMapping("/qr/my-qr")
    public void getMyQr(@AuthenticationPrincipal AppUser currentUser) {
        qrCodes.getMyQr(currentUser.getId());
    }

    @PostMapping("/create")
    public void createPayment(@AuthenticationPrincipal AppUser currentUser,
                              @RequestBody(required = false) Map<String, Object> paymentData) {
        privacy.requireUnlocked(currentUser.getId());
        payments.requireApprovedPaymentProvider();
    }

    @GetMapping("/status/{referenceId}")
    public void getPaymentStatus(@AuthenticationPrincipal AppUser currentUser,
                                 @PathVariable String referenceId) {
        payments.requireApprovedPaymentProvider();
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> webhook(
            @RequestBody @NotNull byte[] rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        webhookVerifier.verify(rawBody, signature);
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Payment webhook processing is pending an approved payment-provider integration.");
    }
}
