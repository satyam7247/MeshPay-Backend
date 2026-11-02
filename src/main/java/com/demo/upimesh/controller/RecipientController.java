package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.RecipientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipients")
public class RecipientController {
    private final RecipientService recipients;
    public RecipientController(RecipientService recipients) { this.recipients = recipients; }

    @GetMapping("/provider-status")
    public RecipientService.ProviderStatus providerStatus() {
        return recipients.providerStatus();
    }
    @GetMapping("/me/discoverability")
    public RecipientService.Discoverability getDiscoverability(@AuthenticationPrincipal AppUser currentUser) {
        return recipients.discoverability(currentUser);
    }

    @PutMapping("/me/discoverability")
    public RecipientService.Discoverability setDiscoverability(@AuthenticationPrincipal AppUser currentUser,
                                                                 @Valid @RequestBody DiscoverabilityRequest request) {
        return recipients.setDiscoverability(currentUser, request.enabled());
    }

    @PostMapping("/lookup")
    public RecipientService.LookupResponse lookup(@AuthenticationPrincipal AppUser currentUser,
                                                   @Valid @RequestBody LookupRequest request) {
        return recipients.lookup(currentUser, request.mobileNumber());
    }

    @PostMapping("/saved")
    public RecipientService.SyncRecipient save(@AuthenticationPrincipal AppUser currentUser,
                                                @Valid @RequestBody SaveRecipientRequest request) {
        return recipients.save(currentUser, request.recipientId());
    }

    @GetMapping("/sync")
    public RecipientService.SyncResponse sync(@AuthenticationPrincipal AppUser currentUser) {
        return recipients.sync(currentUser);
    }

    @DeleteMapping("/saved/{recipientId}")
    public java.util.Map<String, String> remove(@AuthenticationPrincipal AppUser currentUser,
                                                 @PathVariable String recipientId) {
        recipients.removeSaved(currentUser, recipientId);
        return java.util.Map.of("status", "removed");
    }

    public record LookupRequest(@NotBlank @Size(max=24) String mobileNumber) {}
    public record SaveRecipientRequest(@NotBlank @Size(max=36) String recipientId) {}
    public record DiscoverabilityRequest(boolean enabled) {}
}

