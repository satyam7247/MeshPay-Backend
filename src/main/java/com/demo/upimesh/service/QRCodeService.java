package com.demo.upimesh.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class QRCodeService {
    private final PaymentService payments;

    public QRCodeService(PaymentService payments) {
        this.payments = payments;
    }

    public void generate(UUID userId) {
        payments.requireApprovedPaymentProvider();
    }

    public void getMyQr(UUID userId) {
        payments.requireApprovedPaymentProvider();
    }
}
