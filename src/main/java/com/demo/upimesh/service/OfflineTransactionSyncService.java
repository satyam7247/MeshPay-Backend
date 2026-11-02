package com.demo.upimesh.service;

import com.demo.upimesh.dto.OfflineTransactionSyncRequest;
import com.demo.upimesh.dto.OfflineTransactionSyncResponse;
import com.demo.upimesh.model.AppUser;
import com.demo.upimesh.model.OfflineTransactionLog;
import com.demo.upimesh.model.OfflineTransactionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OfflineTransactionSyncService {
    private static final Logger log = LoggerFactory.getLogger(OfflineTransactionSyncService.class);
    private final OfflineTransactionLogRepository logs;

    public OfflineTransactionSyncService(OfflineTransactionLogRepository logs) { this.logs = logs; }

    @Transactional
    public OfflineTransactionSyncResponse sync(AppUser owner, List<OfflineTransactionSyncRequest> requests) {
        if (requests == null || requests.isEmpty() || requests.size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send between 1 and 100 offline transaction logs.");
        }

        Map<String, OfflineTransactionSyncRequest> unique = new LinkedHashMap<>();
        int duplicateInBatch = 0;
        for (OfflineTransactionSyncRequest request : requests) {
            if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction log is required.");
            if (!owner.getUpiId().equalsIgnoreCase(request.customerUpiId().trim())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A user can sync only their own offline transaction logs.");
            }
            if (!request.merchantUpiId().contains("@")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merchant UPI ID is invalid.");
            }
            if (request.amount().scale() > 2 || request.amount().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction amount must be positive with at most two decimal places.");
            }
            String bluetoothTxnId = request.bluetoothTxnId().trim();
            OfflineTransactionSyncRequest previous = unique.putIfAbsent(bluetoothTxnId, request);
            if (previous != null) {
                if (!sameDetails(previous, request)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A Bluetooth transaction ID was reused with different details.");
                }
                duplicateInBatch++;
            }
        }

        List<OfflineTransactionLog> existing = logs.findAllByBluetoothTxnIdIn(unique.keySet());
        Map<String, OfflineTransactionLog> existingById = new LinkedHashMap<>();
        existing.forEach(item -> existingById.put(item.getBluetoothTxnId(), item));

        List<OfflineTransactionLog> newRows = new ArrayList<>();
        int duplicateCount = duplicateInBatch;
        for (Map.Entry<String, OfflineTransactionSyncRequest> entry : unique.entrySet()) {
            OfflineTransactionLog stored = existingById.get(entry.getKey());
            if (stored == null) {
                OfflineTransactionSyncRequest item = entry.getValue();
                newRows.add(new OfflineTransactionLog(owner.getId(), owner.getUpiId(), item.merchantUpiId().trim(),
                        item.amount(), item.localTimestamp(), entry.getKey()));
            } else if (stored.getOwnerUserId().equals(owner.getId()) && sameDetails(stored, entry.getValue())) {
                duplicateCount++;
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A Bluetooth transaction ID conflicts with a previously received log.");
            }
        }

        // The database unique constraint is the final guard for concurrent device syncs.
        logs.saveAllAndFlush(newRows);
        List<String> acknowledged = List.copyOf(unique.keySet());
        if (!newRows.isEmpty()) {
            log.info("Accepted {} offline transaction log(s); {} duplicate(s) skipped for account {}.",
                    newRows.size(), duplicateCount, owner.getId());
        }
        return new OfflineTransactionSyncResponse(newRows.size(), duplicateCount, acknowledged);
    }

    private static boolean sameDetails(OfflineTransactionSyncRequest left, OfflineTransactionSyncRequest right) {
        return left.customerUpiId().equalsIgnoreCase(right.customerUpiId().trim())
                && left.merchantUpiId().trim().equalsIgnoreCase(right.merchantUpiId().trim())
                && left.amount().compareTo(right.amount()) == 0
                && left.localTimestamp().equals(right.localTimestamp());
    }

    private static boolean sameDetails(OfflineTransactionLog left, OfflineTransactionSyncRequest right) {
        return left.getCustomerUpiId().equalsIgnoreCase(right.customerUpiId().trim())
                && left.getMerchantUpiId().equalsIgnoreCase(right.merchantUpiId().trim())
                && left.getAmount().compareTo(right.amount()) == 0
                && Objects.equals(left.getLocalTimestamp(), right.localTimestamp());
    }
}