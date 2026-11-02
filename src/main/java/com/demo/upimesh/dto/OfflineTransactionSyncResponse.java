package com.demo.upimesh.dto;

import java.util.List;

public record OfflineTransactionSyncResponse(int acceptedCount, int duplicateCount, List<String> syncedIds) {}