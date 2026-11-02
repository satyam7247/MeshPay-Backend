package com.demo.upimesh.controller;

import com.demo.upimesh.service.IntegrationStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationStatusController {
    private final IntegrationStatusService integrationStatus;

    public IntegrationStatusController(IntegrationStatusService integrationStatus) {
        this.integrationStatus = integrationStatus;
    }

    @GetMapping("/status")
    public IntegrationStatusService.IntegrationStatus status() {
        return integrationStatus.status();
    }
}
