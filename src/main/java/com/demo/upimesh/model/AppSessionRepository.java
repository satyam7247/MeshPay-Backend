package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AppSessionRepository extends JpaRepository<AppSession, String> {
    long deleteAllByExpiresAtBefore(Instant expiresAt);
    long deleteAllByDeviceIdAndUserId(String deviceId, String userId);
    long deleteAllByUserId(String userId);
}
