package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OfflineTransactionLogRepository extends JpaRepository<OfflineTransactionLog, UUID> {
    List<OfflineTransactionLog> findAllByBluetoothTxnIdIn(Collection<String> bluetoothTxnIds);
}