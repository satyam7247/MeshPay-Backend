package com.demo.upimesh.service;

import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository transactions;

    public TransactionService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    public List<Transaction> listConfirmedPayments(String userVpa) {
        if (userVpa == null || userVpa.isBlank()) {
            return List.of();
        }
        return transactions.findTop20ByStatusAndSenderVpaOrStatusAndReceiverVpaOrderByIdDesc(
                Transaction.Status.SETTLED, userVpa, Transaction.Status.SETTLED, userVpa);
    }
}
