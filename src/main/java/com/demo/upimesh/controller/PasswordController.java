package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.PasswordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/password")
public class PasswordController {
    private final PasswordService passwords;
    private final com.demo.upimesh.service.ClientIpResolver clientIps;
    public PasswordController(PasswordService passwords, com.demo.upimesh.service.ClientIpResolver clientIps){this.passwords=passwords;this.clientIps=clientIps;}

    @PostMapping("/change")
    public Map<String,String> change(@Valid @RequestBody ChangeRequest request, Authentication auth, HttpServletRequest httpRequest) {
        passwords.changeWithCurrentPassword((AppUser)auth.getPrincipal(),request.currentPassword(),request.newPassword(),clientIps.resolve(httpRequest));
        return Map.of("status","password_changed","message","Password updated. Please log in again on this device.");
    }

    @PostMapping("/change/request-otp")
    public PasswordService.ChallengeResponse requestChangeOtp(Authentication auth, HttpServletRequest request) {
        return passwords.requestChangeOtp((AppUser)auth.getPrincipal(),clientIps.resolve(request));
    }

    @PostMapping("/forgot/request")
    public PasswordService.ChallengeResponse requestForgotOtp(@Valid @RequestBody ForgotRequest request, HttpServletRequest httpRequest) {
        return passwords.requestForgotOtp(request.email(),clientIps.resolve(httpRequest));
    }

    @PostMapping("/otp/verify")
    public PasswordService.ResetAuthorization verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return passwords.verifyOtp(request.challengeId(),request.code());
    }

    @PostMapping("/reset")
    public Map<String,String> reset(@Valid @RequestBody ResetRequest request) {
        passwords.resetPassword(request.challengeId(),request.resetToken(),request.newPassword());
        return Map.of("status","password_reset","message","Password updated. Log in with your new password.");
    }

    public record ChangeRequest(@NotBlank @Size(max=72) String currentPassword,
                                @NotBlank @Size(max=72) String newPassword){}
    public record ForgotRequest(@NotBlank @Email @Size(max=254) String email){}
    public record VerifyOtpRequest(@NotBlank @Size(max=36) String challengeId,
                                   @NotBlank @Size(min=6,max=6) String code){}
    public record ResetRequest(@NotBlank @Size(max=36) String challengeId,
                               @NotBlank @Size(max=64) String resetToken,
                               @NotBlank @Size(max=72) String newPassword){}
}
