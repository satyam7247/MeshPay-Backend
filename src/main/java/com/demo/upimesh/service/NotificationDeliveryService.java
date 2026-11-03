package com.demo.upimesh.service;

import com.demo.upimesh.dto.NotificationDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

@Component
public class NotificationDeliveryService {
    private static final Logger log=LoggerFactory.getLogger(NotificationDeliveryService.class);
    private final NotificationService notifications;
    private final BrevoEmailService email;
    private final ExpoPushService push;

    public NotificationDeliveryService(NotificationService notifications,BrevoEmailService email,ExpoPushService push) {
        this.notifications=notifications; this.email=email; this.push=push;
    }

    @Async
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void deliver(NotificationCreatedEvent event) {
        try {
            NotificationService.DeliveryDetails details=notifications.detailsForDelivery(event.notificationId(),event.recipientUserId());
            if(details==null) return;
            NotificationDtos.Item item=details.item();
            try {
                if (details.hideTransactionDetails()) {
                    email.sendPaymentReceivedGeneric(details.recipientEmail(),
                            DateTimeFormatter.ISO_INSTANT.format(item.createdAt()));
                } else {
                    email.sendPaymentReceived(details.recipientEmail(),item.senderName(),item.amount(),
                            item.transactionId(),DateTimeFormatter.ISO_INSTANT.format(item.createdAt()),item.paymentStatus());
                }
            } catch(Exception failure) {
                log.warn("Payment notification email could not be sent ({}).",failure.getClass().getSimpleName());
            }
            try {
                push.send(details);
            } catch(Exception failure) {
                log.warn("Payment push notification could not be sent ({}).",failure.getClass().getSimpleName());
            }
        } catch(Exception failure) {
            log.warn("Payment notification delivery could not be prepared ({}).",failure.getClass().getSimpleName());
        }
    }
}
