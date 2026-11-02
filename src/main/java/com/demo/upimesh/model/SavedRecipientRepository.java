package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedRecipientRepository extends JpaRepository<SavedRecipient, UUID> {
    long deleteAllByOwnerUserId(UUID ownerUserId);
    long deleteAllByRecipientUserId(UUID recipientUserId);
    List<SavedRecipient> findAllByOwnerUserIdOrderBySavedAtDesc(UUID ownerUserId);
    boolean existsByOwnerUserIdAndRecipientUserId(UUID ownerUserId, UUID recipientUserId);
    long countByOwnerUserId(UUID ownerUserId);
    void deleteByOwnerUserIdAndRecipientUserId(UUID ownerUserId, UUID recipientUserId);
}



