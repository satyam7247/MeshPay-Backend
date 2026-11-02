package com.demo.upimesh.dto;

import com.demo.upimesh.model.UserNotification;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}
    public record Item(UUID id,String senderName,BigDecimal amount,Long transactionId,
                       Instant createdAt,String paymentStatus,boolean read,Instant readAt) {
        public static Item from(UserNotification n) {
            return new Item(n.getId(),n.getSenderName(),n.getAmount(),n.getTransactionId(),
                    n.getCreatedAt(),n.getPaymentStatus(),n.isRead(),n.getReadAt());
        }
    }
    public record History(List<Item> items,int page,int size,boolean hasNext) {}
    public record UnreadCount(long unreadCount) {}
    public record PushTokenRequest(String expoPushToken,Boolean enabled,Boolean soundEnabled) {}
}