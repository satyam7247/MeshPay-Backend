package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.AppLockService;
import com.demo.upimesh.service.ClientIpResolver;
import com.demo.upimesh.service.PasswordService;
import com.demo.upimesh.service.BrevoEmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/app-lock")
public class AppLockController {
    private final AppLockService locks;
    private final PasswordService passwords;
    private final ClientIpResolver clientIps;
    private final com.demo.upimesh.service.SessionTokenService sessions;
    private final BrevoEmailService email;

    public AppLockController(AppLockService locks, PasswordService passwords, ClientIpResolver clientIps,
                             com.demo.upimesh.service.SessionTokenService sessions, BrevoEmailService email) {
        this.locks = locks;
        this.passwords = passwords;
        this.clientIps = clientIps;
        this.sessions = sessions;
        this.email = email;
    }

    @GetMapping
    public AppLockService.Status status(Authentication authentication) {
        return locks.status((AppUser) authentication.getPrincipal());
    }

    @PostMapping("/enable")
    @Transactional
    public AppLockService.Status enable(@Valid @RequestBody EnableRequest request, Authentication authentication,
                                        @RequestHeader("Authorization") String authorization, HttpServletRequest httpRequest) {
        AppUser user=(AppUser) authentication.getPrincipal();
        passwords.verifyAccountPasswordForAppLock(user,request.accountPassword(),clientIps.resolve(httpRequest));
        AppLockService.Status status=locks.enable(user, request.passwordType(), request.password());
        sessions.setAppLockVerified(bearer(authorization),user.getId().toString(),false);
        email.sendAppLockChanged(user.getEmail(),false);
        return status;
    }

    @PostMapping("/verify")
    public AppLockService.Status verify(@Valid @RequestBody ScreenLockPasswordRequest request, Authentication authentication,
                                        @RequestHeader("Authorization") String authorization) {
        AppUser user=(AppUser) authentication.getPrincipal();
        AppLockService.Status status=locks.verify(user, request.password());
        sessions.setAppLockVerified(bearer(authorization),user.getId().toString(),true);
        return status;
    }

    @PostMapping("/disable")
    public AppLockService.Status disable(@Valid @RequestBody ScreenLockPasswordRequest request, Authentication authentication) {
        return locks.disable((AppUser) authentication.getPrincipal(), request.password());
    }

    @PostMapping("/lock")
    public java.util.Map<String,String> lockSession(Authentication authentication,
                                                    @RequestHeader("Authorization") String authorization) {
        AppUser user=(AppUser) authentication.getPrincipal();
        sessions.setAppLockVerified(bearer(authorization),user.getId().toString(),false);
        return java.util.Map.of("status","locked");
    }

    @PostMapping("/forgot/request")
    public PasswordService.ChallengeResponse requestReset(Authentication authentication, HttpServletRequest request) {
        return passwords.requestScreenLockOtp((AppUser) authentication.getPrincipal(), clientIps.resolve(request));
    }

    @PostMapping("/forgot/verify")
    public PasswordService.ResetAuthorization verifyReset(@Valid @RequestBody VerifyOtpRequest request,
                                                            Authentication authentication) {
        return passwords.verifyScreenLockOtp((AppUser) authentication.getPrincipal(), request.challengeId(), request.code());
    }

    @PostMapping("/forgot/reset")
    @Transactional
    public AppLockService.Status reset(@Valid @RequestBody ResetRequest request, Authentication authentication,
                                      @RequestHeader("Authorization") String authorization) {
        AppUser user = (AppUser) authentication.getPrincipal();
        passwords.resetScreenLock(user, request.challengeId(), request.resetToken(), request.passwordType(), request.password());
        sessions.setAppLockVerified(bearer(authorization),user.getId().toString(),false);
        return locks.status(user);
    }

    public record EnableRequest(@NotBlank @Size(max = 72) String accountPassword,
                                @NotBlank @Size(max = 12) String passwordType,
                                @NotBlank @Size(min = 6, max = 6) String password) {}
    public record ScreenLockPasswordRequest(@NotBlank @Size(max = 6) String password) {}
    public record VerifyOtpRequest(@NotBlank @Size(max = 36) String challengeId,
                                   @NotBlank @Size(min = 6, max = 6) String code) {}
    public record ResetRequest(@NotBlank @Size(max = 36) String challengeId,
                               @NotBlank @Size(max = 64) String resetToken,
                               @NotBlank @Size(max = 12) String passwordType,
                               @NotBlank @Size(min = 6, max = 6) String password) {}

    private static String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
    }
}
