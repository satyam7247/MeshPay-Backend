package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {
    long deleteAllByOwnerUserId(UUID ownerUserId);
    List<TrustedDevice> findAllByOwnerUserIdOrderByRegisteredAtDesc(UUID ownerUserId);
    List<TrustedDevice> findAllByDeviceId(String deviceId);
    Optional<TrustedDevice> findByOwnerUserIdAndDeviceId(UUID ownerUserId, String deviceId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from TrustedDevice d where d.ownerUserId = :ownerUserId and d.deviceId = :deviceId")
    Optional<TrustedDevice> findByOwnerUserIdAndDeviceIdForUpdate(@org.springframework.data.repository.query.Param("ownerUserId") UUID ownerUserId, @org.springframework.data.repository.query.Param("deviceId") String deviceId);
    Optional<TrustedDevice> findByOwnerUserIdAndDeviceIdAndRevokedAtIsNull(UUID ownerUserId, String deviceId);
}



