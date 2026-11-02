package com.demo.upimesh.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnExpression("!${upi.mesh.demo.seed-accounts:true} || !${upi.mesh.demo.endpoints-enabled:false}")
public class BackendLandingController {
    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("status", "UP", "service", "UPI Without Internet backend", "health", "/api/health");
    }
}
