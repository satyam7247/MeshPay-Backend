package com.demo.upimesh.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigurationTest {
    @Test
    void allowsLocalExpoWebOriginToSubmitJsonLogin() {
        CorsConfigurationSource source = new SecurityConfig()
                .corsConfigurationSource("http://localhost:8081,http://127.0.0.1:8081");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertEquals("http://localhost:8081", configuration.checkOrigin("http://localhost:8081"));
        assertTrue(configuration.checkHttpMethod(HttpMethod.POST).contains(HttpMethod.POST));
        assertEquals(List.of("Content-Type"), configuration.checkHeaders(List.of("Content-Type")));
        assertNull(configuration.checkOrigin("http://untrusted.example"));
    }
}
