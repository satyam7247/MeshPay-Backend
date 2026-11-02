package com.demo.upimesh.controller;

import com.demo.upimesh.dto.NotificationDtos;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications){this.notifications=notifications;}

    @GetMapping
    public NotificationDtos.History history(@AuthenticationPrincipal AppUser user,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return notifications.history(user.getId(),page,size);
    }

    @GetMapping("/unread-count")
    public NotificationDtos.UnreadCount unreadCount(@AuthenticationPrincipal AppUser user) {
        return notifications.unreadCount(user.getId());
    }

    @PutMapping("/{id}/read")
    public NotificationDtos.Item markRead(@AuthenticationPrincipal AppUser user,@PathVariable UUID id) {
        return notifications.markRead(user.getId(),id);
    }

    @PutMapping("/read-all")
    public Map<String,Integer> markAllRead(@AuthenticationPrincipal AppUser user) {
        return Map.of("updated",notifications.markAllRead(user.getId()));
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AppUser user,@PathVariable UUID id) {
        notifications.delete(user.getId(),id);
    }

    @PutMapping("/push-token")
    public Map<String,String> registerPushToken(@AuthenticationPrincipal AppUser user,
                                                @Valid @RequestBody PushTokenRequest request) {
        notifications.registerPushToken(user.getId(),request.expoPushToken(),request.enabled(),request.soundEnabled());
        return Map.of("status","saved");
    }

    public record PushTokenRequest(
            @NotBlank @Size(max=255) @Pattern(regexp="^(Expo|Exponent)PushToken\\[[A-Za-z0-9_-]{10,240}\\]$") String expoPushToken,
            @NotNull Boolean enabled,@NotNull Boolean soundEnabled) {}
}