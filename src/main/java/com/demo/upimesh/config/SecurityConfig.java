package com.demo.upimesh.config;

import com.demo.upimesh.model.AppUserRepository;
import com.demo.upimesh.service.SessionTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionTokenService tokens,
                                   AppUserRepository users,
                                   CorsConfigurationSource corsConfigurationSource,
                                   @Value("${upi.mesh.demo.endpoints-enabled:false}") boolean demoEndpointsEnabled) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> {
                    auth
                        .requestMatchers("/", "/error", "/api/health", "/api/integrations/status",
                                "/api/auth/login", "/api/auth/signup", "/api/server-key",
                                "/api/password/forgot/request", "/api/password/otp/verify", "/api/password/reset",
                                "/api/payments/webhook")
                        .permitAll();
                    if (demoEndpointsEnabled) {
                        auth.requestMatchers("/api/demo/**", "/api/mesh/**").permitAll();
                    } else {
                        auth.requestMatchers("/api/demo/**", "/api/mesh/**").denyAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"A valid bearer token is required. Sign in again.\"}");
                }))
                .addFilterBefore(new SessionTokenFilter(tokens, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:8081,http://127.0.0.1:8081}")
            String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Accept", "Authorization", "Content-Type", "X-Bridge-Node-Id", "X-Hop-Count",
                "X-Razorpay-Signature"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService unusedDefaultUserDetailsService() {
        return username -> { throw new UsernameNotFoundException("Use the app account API."); };
    }
}
