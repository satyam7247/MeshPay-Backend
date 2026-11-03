package com.demo.upimesh.service;

import com.demo.upimesh.dto.NotificationDtos;
import com.demo.upimesh.model.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NotificationService {
    private static final Pattern EXPO_TOKEN = Pattern.compile("^(Expo|Exponent)PushToken\\[[A-Za-z0-9_-]{10,240}\\]$");
    private final UserNotificationRepository notifications;
    private final NotificationPushTokenRepository pushTokens;
    private final AppUserRepository users;
    private final TransactionRepository transactions;
    private final ApplicationEventPublisher events;

    public NotificationService(UserNotificationRepository notifications,NotificationPushTokenRepository pushTokens,
                               AppUserRepository users,TransactionRepository transactions,ApplicationEventPublisher events) {
        this.notifications=notifications; this.pushTokens=pushTokens; this.users=users;
        this.transactions=transactions; this.events=events;
    }

    @Transactional(readOnly=true)
    public NotificationDtos.History history(UUID owner,int page,int size) {
        if(page<0||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid notification page.");
        Page<UserNotification> result=notifications.findAllByRecipientUserIdOrderByCreatedAtDesc(owner,PageRequest.of(page,size));
        return new NotificationDtos.History(result.getContent().stream().map(NotificationDtos.Item::from).toList(),
                result.getNumber(),result.getSize(),result.hasNext());
    }

    @Transactional(readOnly=true)
    public NotificationDtos.UnreadCount unreadCount(UUID owner) {
        return new NotificationDtos.UnreadCount(notifications.countByRecipientUserIdAndReadAtIsNull(owner));
    }

    @Transactional
    public NotificationDtos.Item markRead(UUID owner,UUID id) {
        UserNotification item=owned(owner,id);
        item.markRead(Instant.now());
        return NotificationDtos.Item.from(item);
    }

    @Transactional
    public int markAllRead(UUID owner) {
        return notifications.markAllRead(owner,Instant.now());
    }

    @Transactional
    public void delete(UUID owner,UUID id) {
        notifications.delete(owned(owner,id));
    }

    @Transactional
    public void registerPushToken(UUID owner,String token,boolean enabled,boolean soundEnabled) {
        if(token==null||!EXPO_TOKEN.matcher(token).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Expo push token.");
        }
        NotificationPushToken row=pushTokens.findByExpoPushToken(token)
                .orElseGet(()->new NotificationPushToken(owner,token,enabled,soundEnabled));
        row.update(owner,enabled,soundEnabled);
        pushTokens.save(row);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void createForSettledTransaction(Long transactionId) {
        Transaction transaction=transactions.findById(transactionId).orElse(null);
        if(transaction==null||transaction.getStatus()!=Transaction.Status.SETTLED
                ||notifications.findByTransactionId(transactionId).isPresent()) return;
        AppUser recipient=users.findByUpiId(transaction.getReceiverVpa()).orElse(null);
        if(recipient==null) return;
        AppUser sender=users.findByUpiId(transaction.getSenderVpa()).orElse(null);
        String senderName=sender==null?transaction.getSenderVpa():sender.getName();
        UserNotification saved=notifications.save(new UserNotification(recipient.getId(),transactionId,
                senderName,transaction.getAmount(),transaction.getStatus().name(),transaction.getSettledAt()));
        events.publishEvent(new NotificationCreatedEvent(saved.getId(),recipient.getId()));
    }

    @Transactional(readOnly=true)
    public DeliveryDetails detailsForDelivery(UUID notificationId,UUID ownerId) {
        UserNotification notification=notifications.findByIdAndRecipientUserId(notificationId,ownerId).orElse(null);
        AppUser recipient=users.findById(ownerId).orElse(null);
        if(notification==null||recipient==null) return null;
        List<PushTarget> targets=pushTokens.findAllByOwnerUserIdAndEnabledTrue(ownerId).stream()
                .map(token->new PushTarget(token.getExpoPushToken(),token.isSoundEnabled())).toList();
        return new DeliveryDetails(recipient.getEmail(),NotificationDtos.Item.from(notification),
                notifications.countByRecipientUserIdAndReadAtIsNull(ownerId),targets,recipient.isHideTransactionDetails());
    }

    private UserNotification owned(UUID owner,UUID id) {
        return notifications.findByIdAndRecipientUserId(id,owner)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification was not found."));
    }

    public record PushTarget(String token,boolean soundEnabled) {}
    public record DeliveryDetails(String recipientEmail,NotificationDtos.Item item,long unreadCount,List<PushTarget> pushTargets,boolean hideTransactionDetails) {}
}