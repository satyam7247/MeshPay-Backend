package com.demo.upimesh.controller;

import com.demo.upimesh.dto.OfflineTransactionSyncRequest;
import com.demo.upimesh.dto.OfflineTransactionSyncResponse;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.service.OfflineTransactionSyncService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class OfflineTransactionSyncController {
    private final OfflineTransactionSyncService syncService;

    public OfflineTransactionSyncController(OfflineTransactionSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public OfflineTransactionSyncResponse sync(
            @AuthenticationPrincipal AppUser currentUser,
            @RequestBody @Valid List<@Valid OfflineTransactionSyncRequest> requests) {
        return syncService.sync(currentUser, requests);
    }
}