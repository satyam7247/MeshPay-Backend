package com.demo.upimesh.controller;

import com.demo.upimesh.dto.BankAccountDtos.LinkBankAccountRequest;
import com.demo.upimesh.dto.BankAccountDtos.SendOtpRequest;
import com.demo.upimesh.dto.BankAccountDtos.VerifyBankAccountRequest;
import com.demo.upimesh.dto.BankAccountDtos.VerifyOtpRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankAccountControllerTest {
    @Test
    void validatesMobileNumberAsExactlyTenDigits() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertFalse(validator.validate(new SendOtpRequest("123456789", null)).isEmpty());
            assertTrue(validator.validate(new SendOtpRequest("1234567890", null)).isEmpty());
        }
    }

    @Test
    void validatesOtpAndAccountNumberShapes() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            UUID id = UUID.randomUUID();

            assertFalse(validator.validate(new VerifyOtpRequest(id, "12345")).isEmpty());
            assertTrue(validator.validate(new VerifyOtpRequest(id, "123456")).isEmpty());
            assertFalse(validator.validate(new VerifyBankAccountRequest(id, "12345678", "HDFC0000240")).isEmpty());
            assertFalse(validator.validate(new VerifyBankAccountRequest(id, "123456789", "HDFC000024")).isEmpty());
            assertTrue(validator.validate(new VerifyBankAccountRequest(
                    id, "123456789", "HDFC0000240")).isEmpty());
        }
    }

    @Test
    void validatesBankLinkAccountDetails() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            UUID challengeId = UUID.randomUUID();

            assertFalse(validator.validate(new LinkBankAccountRequest(challengeId, "SBI", "User",
                    "12345678", "12345678")).isEmpty());
            assertTrue(validator.validate(new LinkBankAccountRequest(challengeId, "State Bank of India",
                    "User", "123456789", "123456789")).isEmpty());
        }
    }
}
