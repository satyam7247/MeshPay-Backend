package com.demo.upimesh.controller;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name="upi.mesh.demo.seed-accounts", havingValue="true")
public class DemoDashboardController {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public DemoDashboardController(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @GetMapping("/accounts")
    public List<Account> accounts() {
        return accounts.findAllByOrderByHolderNameAsc().stream()
                .filter(account -> account.getVpa().endsWith("@demo"))
                .toList();
    }

    @GetMapping("/transactions")
    public List<Transaction> transactions() {
        return transactions.findTop20ByOrderByIdDesc().stream()
                .filter(transaction -> transaction.getSenderVpa().endsWith("@demo")
                        || transaction.getReceiverVpa().endsWith("@demo"))
                .toList();
    }
}
