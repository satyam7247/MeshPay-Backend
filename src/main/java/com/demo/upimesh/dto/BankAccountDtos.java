package com.demo.upimesh.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class BankAccountDtos {
    private BankAccountDtos() {}

    public record SendOtpRequest(
            @NotBlank @Pattern(regexp = "[0-9]{10}") String mobile,
            UUID challengeId) {}

    public record SendOtpResponse(
            UUID challengeId,
            int expiresInSeconds,
            int resendAfterSeconds,
            String deliveryMode,
            String maskedDestination) {}

    public record VerifyOtpRequest(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {}

    public record VerifyOtpResponse(boolean verified, String message) {}

    public record LinkBankAccountRequest(
            @NotNull UUID challengeId,
            @NotBlank @Size(max = 100) String bankName,
            @NotBlank @Size(max = 100) String accountHolderName,
            @NotBlank @Pattern(regexp = "[0-9]{9,18}") String accountNumber,
            @NotBlank @Pattern(regexp = "[0-9]{9,18}") String confirmAccountNumber) {}

    public record VerifyBankAccountRequest(
            @NotNull UUID accountId,
            @NotBlank @Pattern(regexp = "[0-9]{9,18}") String accountNumber,
            @NotBlank @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}") String ifsc) {}

    public record BankAccountResponse(
            UUID id,
            String bankName,
            String accountHolderName,
            String maskedAccountNumber,
            String verificationStatus,
            String verificationMode,
            String verificationMessage,
            String providerReference,
            Instant createdAt,
            Instant updatedAt) {}
}
