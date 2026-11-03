package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.PrivacyAccountService;
import com.demo.upimesh.service.TrustedDeviceService;
import com.demo.upimesh.service.AuthService;
import com.demo.upimesh.service.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@RestController
@RequestMapping("/api/privacy-account")
public class PrivacyAccountController {
    private final PrivacyAccountService privacy;
    private final TrustedDeviceService devices;
    private final AuthService auth;
    private final SessionTokenService sessions;
    private final com.demo.upimesh.service.ClientIpResolver clientIps;

    public PrivacyAccountController(PrivacyAccountService privacy, TrustedDeviceService devices,
                                    AuthService auth, SessionTokenService sessions,
                                    com.demo.upimesh.service.ClientIpResolver clientIps) {
        this.privacy = privacy;
        this.devices = devices;
        this.auth = auth;
        this.sessions = sessions;
        this.clientIps = clientIps;
    }

    @GetMapping
    public PrivacyAccountService.PrivacyResponse get(@AuthenticationPrincipal AppUser user) {
        return privacy.get(user);
    }

    @PutMapping
    public PrivacyAccountService.PrivacyResponse update(@AuthenticationPrincipal AppUser user,
            @Valid @RequestBody PrivacyAccountService.UpdateRequest request, HttpServletRequest httpRequest) {
        return privacy.update(user, request, clientIps.resolve(httpRequest));
    }

    @GetMapping("/devices")
    public List<TrustedDeviceService.DeviceResponse> devices(@AuthenticationPrincipal AppUser user) {
        return devices.list(user);
    }

    @PostMapping("/devices/register")
    @Transactional
    public TrustedDeviceService.DeviceResponse registerCurrentDevice(@AuthenticationPrincipal AppUser user,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DeviceRegistrationRequest request, HttpServletRequest httpRequest) {
        if (!auth.verifyCurrentPassword(user, request.password(), clientIps.resolve(httpRequest))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password verification failed.");
        }
        String deviceId = devices.registerPasswordVerifiedDevice(user, request.deviceId(), request.deviceName(), request.platform());
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid session is required.");
        }
        sessions.bindDevice(authorization.substring(7).trim(), user.getId().toString(), deviceId);
        return devices.list(user).stream().filter(device -> device.deviceId().equals(deviceId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "The device registration could not be confirmed."));
    }

    @DeleteMapping("/devices/{deviceId}")
    public void revokeDevice(@AuthenticationPrincipal AppUser user, @PathVariable String deviceId) {
        devices.revoke(user, deviceId);
    }

    @PostMapping("/lock")
    public PrivacyAccountService.PrivacyResponse lock(@AuthenticationPrincipal AppUser user,
            @Valid @RequestBody LockRequest request) {
        return privacy.lock(user, request.confirmed());
    }

    @PostMapping("/unlock")
    public PrivacyAccountService.PrivacyResponse unlock(@AuthenticationPrincipal AppUser user,
            @Valid @RequestBody UnlockRequest request, HttpServletRequest httpRequest) {
        return privacy.unlock(user, request.password(), clientIps.resolve(httpRequest));
    }

    @GetMapping("/sync-status")
    public PrivacyAccountService.SyncAvailability syncStatus() {
        return privacy.syncAvailability();
    }

    public record LockRequest(@NotNull Boolean confirmed) {}
    public record UnlockRequest(@NotBlank @Size(max=72) String password) {}
    public record DeviceRegistrationRequest(@NotBlank @Size(min=36,max=36) String deviceId,
            @NotBlank @Size(max=80) String deviceName, @NotBlank @Size(max=16) String platform,
            @NotBlank @Size(max=72) String password) {}
}
