package com.demo.upimesh.controller;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.AuthService;
import com.demo.upimesh.service.SessionTokenService;
import com.demo.upimesh.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final SessionTokenService tokens;
    private final ClientIpResolver clientIps;

    public AuthController(AuthService auth, SessionTokenService tokens, ClientIpResolver clientIps) {
        this.auth = auth;
        this.tokens = tokens;
        this.clientIps = clientIps;
    }

    @PostMapping("/signup")
    public AuthService.AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return auth.signup(request.name(), request.email(), request.phone(), request.password(),
                request.deviceId(), request.deviceName(), request.platform());
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return auth.login(request.email(), request.password(), request.deviceId(), request.deviceName(), request.platform(), clientIps.resolve(httpRequest));
    }

    @GetMapping("/me")
    public AuthService.UserProfile me(Authentication authentication) {
        return auth.profile((AppUser) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    public java.util.Map<String, String> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokens.revoke(authorization.substring(7).trim());
        }
        return java.util.Map.of("status", "signed_out");
    }

    public record SignupRequest(
            @NotBlank @Size(max=80) String name,
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Pattern(regexp="^\\+?[0-9() -]{8,18}$") String phone,
            @NotBlank @Size(min=8, max=72) String password,
            @Size(max=36) String deviceId,
            @Size(max=80) String deviceName,
            @Size(max=16) String platform) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(max=72) String password,
            @Size(max=36) String deviceId,
            @Size(max=80) String deviceName,
            @Size(max=16) String platform) {}
}
