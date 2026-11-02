package com.demo.upimesh.service;

import org.springframework.stereotype.Service;

@Service
public class IntegrationStatusService {
    private final BankOtpService otp;
    private final BankVerificationService bankVerification;
    private final AccountAggregatorService accountAggregator;
    private final PaymentService payments;

    public IntegrationStatusService(BankOtpService otp,
                                    BankVerificationService bankVerification,
                                    AccountAggregatorService accountAggregator,
                                    PaymentService payments) {
        this.otp = otp;
        this.bankVerification = bankVerification;
        this.accountAggregator = accountAggregator;
        this.payments = payments;
    }

    public IntegrationStatus status() {
        return new IntegrationStatus(
                new CapabilityStatus(otp.isProviderConfigured()
                        ? "CONFIGURED_NOT_LIVE_VALIDATED" : "CONFIGURATION_PENDING",
                        otp.providerName(), otp.configurationDetail()),
                new CapabilityStatus(bankVerification.status(),
                        bankVerification.providerName(), bankVerification.configurationDetail()),
                new CapabilityStatus(accountAggregator.status(),
                        "Setu Account Aggregator", "FIU production approval, consent UI and data-fetch flow are required."),
                new CapabilityStatus(payments.status(),
                        "UPI PSP/acquirer", "Merchant onboarding and user-specific settlement routing are required."));
    }

    public record CapabilityStatus(String status, String provider, String detail) {}

    public record IntegrationStatus(CapabilityStatus mobileOtp,
                                    CapabilityStatus bankVerification,
                                    CapabilityStatus balanceEnquiry,
                                    CapabilityStatus paymentsAndQr) {}
}
