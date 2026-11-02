package com.demo.upimesh.service;

import com.demo.upimesh.model.RecipientLookupStatus;
import com.demo.upimesh.model.RecipientDataSource;
import com.demo.upimesh.model.AppUser;
import java.time.Instant;

public interface RecipientLookupProvider {
    ProviderResult lookup(String normalizedMobile);

    record ProviderResult(RecipientLookupStatus status, RecipientDataSource source,
                         RecipientMatch recipient, Instant checkedAt) {}
    record RecipientMatch(String recipientId, String displayName, String meshPayId,
                          int recordVersion, Instant updatedAt) {}
}
