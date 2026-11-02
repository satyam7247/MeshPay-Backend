package com.demo.upimesh.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OfflineTransactionSyncRequest(
        @NotBlank @Size(max = 80) String customerUpiId,
        @NotBlank @Size(max = 255) String merchantUpiId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull LocalDateTime localTimestamp,
        @NotBlank @Size(max = 128) String bluetoothTxnId) {}