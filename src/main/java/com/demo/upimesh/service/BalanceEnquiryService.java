package com.demo.upimesh.service;

import com.demo.upimesh.model.BankAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class BalanceEnquiryService {
    private final BankAccountRepository accounts;
    private final AccountAggregatorService accountAggregator;

    public BalanceEnquiryService(BankAccountRepository accounts,
                                 AccountAggregatorService accountAggregator) {
        this.accounts = accounts;
        this.accountAggregator = accountAggregator;
    }

    @Transactional(readOnly = true)
    public void checkBalance(UUID userId) {
        boolean hasVerifiedAccount = accounts.existsByOwnerUserIdAndVerificationStatusAndVerificationModeNot(
                userId, "VERIFIED", "MOCK_SIMULATED");
        if (!hasVerifiedAccount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Link an account verified by an approved bank provider before requesting balance access.");
        }
        accountAggregator.requireProductionAccessAndConsent();
    }
}
