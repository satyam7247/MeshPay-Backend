package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.service.RecipientService;
import com.demo.upimesh.service.TransactionService;
import com.demo.upimesh.service.PrivacyAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountDataController {
    private final RecipientService recipients;
    private final PrivacyAccountService privacy;

    public AccountDataController(PrivacyAccountService privacy, RecipientService recipients) {
        this.privacy = privacy;
        this.recipients = recipients;
    }

    // Compatibility endpoint now returns only the signed-in user's saved recipients.
    @GetMapping({"/contacts", "/accounts"})
    public List<RecipientService.ContactResponse> contacts(@AuthenticationPrincipal AppUser currentUser) {
        return recipients.contacts(currentUser);
    }

    @GetMapping({"/transactions", "/transactions/my-transactions"})
    public List<PrivacyAccountService.TransactionView> transactions(@AuthenticationPrincipal AppUser currentUser) {
        return privacy.transactions(currentUser);
    }
}
