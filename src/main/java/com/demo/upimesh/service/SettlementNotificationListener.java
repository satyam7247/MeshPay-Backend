package com.demo.upimesh.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SettlementNotificationListener {
    private final NotificationService notifications;
    public SettlementNotificationListener(NotificationService notifications){this.notifications=notifications;}

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void onPaymentSettled(PaymentSettledEvent event) {
        notifications.createForSettledTransaction(event.transactionId());
    }
}
