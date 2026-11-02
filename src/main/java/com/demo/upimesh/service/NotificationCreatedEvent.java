package com.demo.upimesh.service;
import java.util.UUID;
public record NotificationCreatedEvent(UUID notificationId,UUID recipientUserId) {}