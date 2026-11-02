package com.demo.upimesh.controller;

import com.demo.upimesh.dto.BankAccountDtos.BankAccountResponse;
import com.demo.upimesh.dto.BankAccountDtos.LinkBankAccountRequest;
import com.demo.upimesh.dto.BankAccountDtos.SendOtpRequest;
import com.demo.upimesh.dto.BankAccountDtos.SendOtpResponse;
import com.demo.upimesh.dto.BankAccountDtos.VerifyBankAccountRequest;
import com.demo.upimesh.dto.BankAccountDtos.VerifyOtpRequest;
import com.demo.upimesh.dto.BankAccountDtos.VerifyOtpResponse;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.BankAccountService;
import com.demo.upimesh.service.BankOtpService;
import com.demo.upimesh.service.BalanceEnquiryService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bank-accounts")
public class BankAccountController {
    private final BankAccountService bankAccounts;
    private final BankOtpService bankOtp;
    private final BalanceEnquiryService balanceEnquiry;

    public BankAccountController(BankAccountService bankAccounts, BankOtpService bankOtp,
                                 BalanceEnquiryService balanceEnquiry) {
        this.bankAccounts = bankAccounts;
        this.bankOtp = bankOtp;
        this.balanceEnquiry = balanceEnquiry;
    }

    @PostMapping("/send-otp")
    public SendOtpResponse sendOtp(@AuthenticationPrincipal AppUser currentUser,
                                   @Valid @RequestBody SendOtpRequest request) {
        return bankOtp.send(currentUser, request.mobile(), request.challengeId());
    }

    @PostMapping("/verify-otp")
    public VerifyOtpResponse verifyOtp(@AuthenticationPrincipal AppUser currentUser,
                                       @Valid @RequestBody VerifyOtpRequest request) {
        bankOtp.verify(currentUser.getId(), currentUser.getPhone(),
                request.challengeId(), request.otp());
        return new VerifyOtpResponse(true, bankOtp.verificationMessage());
    }

    @PostMapping("/link")
    public BankAccountResponse linkAccount(@AuthenticationPrincipal AppUser currentUser,
                                           @Valid @RequestBody LinkBankAccountRequest request) {
        return bankAccounts.link(currentUser.getId(), request);
    }

    @PostMapping("/verify")
    public BankAccountResponse verifyAccount(@AuthenticationPrincipal AppUser currentUser,
                                             @Valid @RequestBody VerifyBankAccountRequest request) {
        return bankAccounts.verify(currentUser.getId(), request);
    }

    @GetMapping("/my-accounts")
    public List<BankAccountResponse> listMyAccounts(@AuthenticationPrincipal AppUser currentUser) {
        return bankAccounts.list(currentUser.getId());
    }

    @GetMapping("/balance")
    public void checkBalance(@AuthenticationPrincipal AppUser currentUser) {
        balanceEnquiry.checkBalance(currentUser.getId());
    }

    @DeleteMapping("/{accountId}")
    public org.springframework.http.ResponseEntity<Void> removeAccount(
            @AuthenticationPrincipal AppUser currentUser, @PathVariable UUID accountId) {
        bankAccounts.remove(currentUser.getId(), accountId);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
