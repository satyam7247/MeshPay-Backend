package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.RecipientDataSource;
import com.demo.upimesh.model.RecipientLookupStatus;
import com.demo.upimesh.model.RecipientVerificationStatus;
import com.demo.upimesh.model.SavedRecipient;
import com.demo.upimesh.model.SavedRecipientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RecipientService {
    private static final int MAX_SAVED_RECIPIENTS = 200;
    private final AppUserRepository users;
    private final SavedRecipientRepository savedRecipients;
    private final RecipientLookupProvider provider;
    private final RecipientRateLimiter rateLimiter;

    public RecipientService(AppUserRepository users, SavedRecipientRepository savedRecipients,
                            RecipientLookupProvider provider, RecipientRateLimiter rateLimiter) {
        this.users = users;
        this.savedRecipients = savedRecipients;
        this.provider = provider;
        this.rateLimiter = rateLimiter;
    }

    @Transactional(readOnly = true)
    public ProviderStatus providerStatus() {
        return new ProviderStatus(true, false, RecipientLookupStatus.REAL_UPI_PROVIDER_NOT_CONFIGURED,
                "Only the MeshPay application directory is available; no live bank or UPI verification provider is configured.");
    }
    public Discoverability discoverability(AppUser currentUser) {
        AppUser user = requireCurrentUser(currentUser.getId());
        return new Discoverability(user.isRecipientDiscoverable(), false,
                "Your name and MeshPay ID can be returned to authenticated users who search your exact number. Phone ownership is not verified by OTP.");
    }

    @Transactional
    public Discoverability setDiscoverability(AppUser currentUser, boolean enabled) {
        AppUser user = requireCurrentUser(currentUser.getId());
        user.setRecipientDiscoverable(enabled);
        users.save(user);
        return discoverability(user);
    }

    @Transactional(readOnly = true)
    public LookupResponse lookup(AppUser currentUser, String mobileNumber) {
        rateLimiter.check(currentUser.getId());
        String normalized = MobileNumberNormalizer.normalize(mobileNumber);
        RecipientLookupProvider.ProviderResult result = provider.lookup(normalized);
        RecipientLookupProvider.RecipientMatch match = result.recipient();
        if (match != null && match.recipientId().equals(currentUser.getId().toString())) {
            return notFound(result.checkedAt());
        }
        if (result.status() != RecipientLookupStatus.SERVER_DATABASE_MATCH || match == null) {
            return notFound(result.checkedAt());
        }
        return new LookupResponse(result.status(), result.source(), RecipientVerificationStatus.NOT_VERIFIED,
                new RecipientProfile(match.recipientId(), match.displayName(), match.meshPayId(), null,
                        match.recordVersion(), match.updatedAt(), false), result.checkedAt());
    }

    @Transactional
    public SyncRecipient save(AppUser currentUser, String recipientId) {
        UUID targetId = parseId(recipientId);
        AppUser target = users.findById(targetId)
                .filter(AppUser::isRecipientDiscoverable)
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient is not available."));

        List<SavedRecipient> existing = savedRecipients.findAllByOwnerUserIdOrderBySavedAtDesc(currentUser.getId());
        boolean alreadySaved = existing.stream().anyMatch(row -> row.getRecipientUserId().equals(targetId));
        if (!alreadySaved) {
            if (existing.size() >= MAX_SAVED_RECIPIENTS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Saved recipient limit reached.");
            }
            savedRecipients.save(new SavedRecipient(currentUser.getId(), targetId));
        }
        return syncRecipient(target);
    }

    @Transactional(readOnly = true)
    public SyncResponse sync(AppUser currentUser) {
        Instant syncedAt = Instant.now();
        List<SyncRecipient> records = savedRecipients.findAllByOwnerUserIdOrderBySavedAtDesc(currentUser.getId()).stream()
                .map(row -> users.findById(row.getRecipientUserId()).orElse(null))
                .filter(user -> user != null && user.isRecipientDiscoverable())
                .map(this::syncRecipient)
                .toList();
        return new SyncResponse(records, syncedAt, false);
    }

    @Transactional
    public void removeSaved(AppUser currentUser, String recipientId) {
        UUID targetId = parseId(recipientId);
        savedRecipients.deleteByOwnerUserIdAndRecipientUserId(currentUser.getId(), targetId);
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> contacts(AppUser currentUser) {
        return sync(currentUser).records().stream()
                .map(record -> new ContactResponse(record.recipientId(), record.displayName(), record.meshPayId()))
                .toList();
    }

    private SyncRecipient syncRecipient(AppUser user) {
        return new SyncRecipient(user.getId().toString(), user.getPhoneNormalized(), user.getName(), user.getUpiId(),
                RecipientLookupStatus.SERVER_DATABASE_MATCH, RecipientDataSource.APPLICATION_DATABASE,
                RecipientVerificationStatus.NOT_VERIFIED, user.getRecipientVersion(), user.getRecipientUpdatedAt());
    }

    private LookupResponse notFound(Instant checkedAt) {
        return new LookupResponse(RecipientLookupStatus.NOT_FOUND, RecipientDataSource.APPLICATION_DATABASE,
                RecipientVerificationStatus.NOT_VERIFIED, null, checkedAt);
    }

    private AppUser requireCurrentUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again to continue."));
    }

    private UUID parseId(String id) {
        try { return UUID.fromString(id); }
        catch (RuntimeException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient ID is invalid."); }
    }

    public record Discoverability(boolean enabled, boolean phoneOwnershipVerified, String notice) {}
    public record ProviderStatus(boolean appDirectoryAvailable, boolean liveVerificationAvailable, RecipientLookupStatus liveProviderStatus, String notice) {}
    public record RecipientProfile(String id, String displayName, String meshPayId, String mobileNumber,
                                   int recordVersion, Instant updatedAt, boolean liveVerified) {}
    public record LookupResponse(RecipientLookupStatus status, RecipientDataSource source,
                                 RecipientVerificationStatus verificationStatus, RecipientProfile recipient,
                                 Instant checkedAt) {}
    public record SyncRecipient(String recipientId, String mobileNumber, String displayName, String meshPayId,
                                RecipientLookupStatus status, RecipientDataSource source,
                                RecipientVerificationStatus verificationStatus, int recordVersion, Instant updatedAt) {}
    public record SyncResponse(List<SyncRecipient> records, Instant syncedAt, boolean liveVerificationAvailable) {}
    public record ContactResponse(String id, String name, String upiId) {}
}

