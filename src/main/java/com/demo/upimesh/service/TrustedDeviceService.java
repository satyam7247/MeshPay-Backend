package com.demo.upimesh.service;

import com.demo.upimesh.model.AppSessionRepository;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.TrustedDevice;
import com.demo.upimesh.model.TrustedDeviceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TrustedDeviceService {
    private final TrustedDeviceRepository devices;
    private final AppSessionRepository sessions;
    private final AppUserRepository users;

    public TrustedDeviceService(TrustedDeviceRepository devices, AppSessionRepository sessions, AppUserRepository users) {
        this.devices = devices;
        this.sessions = sessions;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String registerPasswordVerifiedDevice(AppUser user, String requestedId, String name, String platform) {
        users.findByIdForUpdate(user.getId()).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Account is no longer available."));
        return registerDevice(user, requestedId, name, platform);
    }

    @Transactional
    public String registerNewUserDevice(AppUser user, String requestedId, String name, String platform) {
        users.findByIdForUpdate(user.getId()).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Account is no longer available."));
        return registerDevice(user, requestedId, name, platform);
    }

    private String registerDevice(AppUser user, String requestedId, String name, String platform) {
        String deviceId;
        try { deviceId = UUID.fromString(requestedId).toString(); }
        catch (Exception ignored) { deviceId = UUID.randomUUID().toString(); }
        String safeName = name == null || name.isBlank() ? "Unknown device" : name.trim();
        if (safeName.length() > 80) safeName = safeName.substring(0, 80);
        String safePlatform = platform == null || !List.of("web", "android", "ios").contains(platform.toLowerCase())
                ? "unknown" : platform.toLowerCase();

        TrustedDevice device = findDeviceForUpdate(user.getId(), deviceId);
        if (device == null) device = new TrustedDevice(user.getId(), deviceId, safeName, safePlatform);
        else device.updateLogin(safeName, safePlatform);
        TrustedDevice persistedDevice = devices.saveAndFlush(device);
        return persistedDevice.getDeviceId();
    }
    private TrustedDevice findDeviceForUpdate(UUID ownerId, String deviceId) {
        TrustedDevice locked = devices.findByOwnerUserIdAndDeviceIdForUpdate(ownerId, deviceId).orElse(null);
        return locked != null ? locked : findOwnedDevice(ownerId, deviceId);
    }

    private TrustedDevice findOwnedDevice(UUID ownerId, String deviceId) {
        TrustedDevice exact = devices.findByOwnerUserIdAndDeviceId(ownerId, deviceId).orElse(null);
        if (exact != null) return exact;
        TrustedDevice byOwner = devices.findAllByOwnerUserIdOrderByRegisteredAtDesc(ownerId).stream()
                .filter(candidate -> candidate.getDeviceId().equalsIgnoreCase(deviceId))
                .findFirst().orElse(null);
        if (byOwner != null) return byOwner;
        return devices.findAllByDeviceId(deviceId).stream()
                .filter(candidate -> ownerId.equals(candidate.getOwnerUserId()))
                .filter(candidate -> candidate.getDeviceId().equalsIgnoreCase(deviceId))
                .findFirst().orElse(null);
    }
    @Transactional(readOnly = true)
    public List<DeviceResponse> list(AppUser user) {
        return devices.findAllByOwnerUserIdOrderByRegisteredAtDesc(user.getId()).stream()
                .map(device -> new DeviceResponse(device.getDeviceId(), device.getDeviceName(), device.getPlatform(),
                        device.getVerificationMethod(), device.getRegisteredAt(), device.getLastActiveAt(), device.getRevokedAt()))
                .toList();
    }

    @Transactional
    public void revoke(AppUser user, String deviceId) {
        TrustedDevice device = devices.findByOwnerUserIdAndDeviceIdForUpdate(user.getId(), deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device was not found."));
        if (!device.isRevoked()) {
            device.revoke(Instant.now());
            devices.save(device);
            sessions.deleteAllByDeviceIdAndUserId(deviceId, user.getId().toString());
        }
    }

    @Transactional
    public boolean isActive(String userId, String deviceId) {
        try {
            UUID ownerId = UUID.fromString(userId);
            TrustedDevice device = devices.findByOwnerUserIdAndDeviceIdAndRevokedAtIsNull(ownerId, deviceId)
                    .orElseGet(() -> findOwnedDevice(ownerId, deviceId));
            if (device == null || device.isRevoked()) return false;
            Instant now = Instant.now();
            if (device.getLastActiveAt().plusSeconds(300).isBefore(now)) {
                device.touch(now);
                devices.save(device);
            }
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public record DeviceResponse(String deviceId, String deviceName, String platform,
                                 String verificationMethod, Instant registeredAt,
                                 Instant lastActiveAt, Instant revokedAt) {}
}
