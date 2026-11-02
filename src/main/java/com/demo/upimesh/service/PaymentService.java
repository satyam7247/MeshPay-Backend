package com.demo.upimesh.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    public void requireApprovedPaymentProvider() {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Payments are pending PSP/acquirer onboarding and server-side provider configuration.");
    }

    public String status() {
        return "PENDING_PSP_ACQUIRER_APPROVAL";
    }
}
