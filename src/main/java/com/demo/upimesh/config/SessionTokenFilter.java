package com.demo.upimesh.config;

import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.service.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class SessionTokenFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SessionTokenFilter.class);
    private final SessionTokenService tokens;
    private final AppUserRepository users;

    public SessionTokenFilter(SessionTokenService tokens, AppUserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/integrations/status".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            String path = request.getRequestURI();
            if (path.startsWith("/api/") && !path.equals("/api/auth/login") && !path.equals("/api/auth/signup") && !path.startsWith("/api/password/") && !path.equals("/api/health") && !path.equals("/api/integrations/status")) {
                log.warn("Protected API request has no bearer token: {}", path);
            }
        } else {
            String token = authorization.substring(7).trim();
            java.util.Optional<String> userId = tokens.resolve(token);
            if (userId.isEmpty()) log.warn("Protected API request has a rejected bearer token: {}", request.getRequestURI());
            else {
                java.util.Optional<AppUser> user = userId.flatMap(id -> {
                    try { return users.findById(java.util.UUID.fromString(id)); }
                    catch (IllegalArgumentException e) { return java.util.Optional.empty(); }
                });
                String requestPath = request.getRequestURI();
                boolean appLockExempt = isAppLockExempt(requestPath);
                if (user.isPresent() && user.get().isAppLockEnabled() && !appLockExempt
                        && !tokens.isAppLockVerifiedAndTouch(token, requestPath, request.getMethod())) {
                    response.setStatus(423);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"APP_LOCK_REQUIRED\",\"message\":\"Unlock MeshPay to continue.\"}");
                    return;
                }
                user.ifPresent(account -> {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(account, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isAppLockExempt(String path) {
        return path.equals("/api/auth/logout") || path.equals("/api/app-lock") || path.startsWith("/api/app-lock/");
    }
}
