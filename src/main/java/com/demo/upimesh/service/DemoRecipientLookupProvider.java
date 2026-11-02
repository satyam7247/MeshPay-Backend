package com.demo.upimesh.service;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.RecipientDataSource;
import com.demo.upimesh.model.RecipientLookupStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DemoRecipientLookupProvider implements RecipientLookupProvider {
    private final AppUserRepository users;

    public DemoRecipientLookupProvider(AppUserRepository users) { this.users = users; }

    @Override
    public ProviderResult lookup(String normalizedMobile) {
        Instant checkedAt = Instant.now();
        return users.findFirstByPhoneNormalizedAndRecipientDiscoverableTrue(normalizedMobile)
                .map(user -> new ProviderResult(
                        RecipientLookupStatus.SERVER_DATABASE_MATCH,
                        RecipientDataSource.APPLICATION_DATABASE,
                        new RecipientMatch(user.getId().toString(), user.getName(), user.getUpiId(),
                                user.getRecipientVersion(), user.getRecipientUpdatedAt()), checkedAt))
                .orElseGet(() -> new ProviderResult(RecipientLookupStatus.NOT_FOUND,
                        RecipientDataSource.APPLICATION_DATABASE, null, checkedAt));
    }
}
