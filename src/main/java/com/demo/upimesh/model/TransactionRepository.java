package com.demo.upimesh.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    boolean existsBySenderVpaAndStatusNotIn(String senderVpa, List<Transaction.Status> statuses);
    boolean existsByReceiverVpaAndStatusNotIn(String receiverVpa, List<Transaction.Status> statuses);
    List<Transaction> findTop20ByOrderByIdDesc();
    List<Transaction> findTop20BySenderVpaOrReceiverVpaOrderByIdDesc(String senderVpa, String receiverVpa);
    List<Transaction> findTop20ByStatusAndSenderVpaOrStatusAndReceiverVpaOrderByIdDesc(
            Transaction.Status senderStatus, String senderVpa,
            Transaction.Status receiverStatus, String receiverVpa);
    boolean existsByPacketHash(String packetHash);
    boolean existsByTransactionNonce(String transactionNonce);
    @Modifying
    @Query("update Transaction t set t.senderVpa=:replacement where t.senderVpa=:original")
    int anonymizeSenderVpa(@Param("original") String original, @Param("replacement") String replacement);
    @Modifying
    @Query("update Transaction t set t.receiverVpa=:replacement where t.receiverVpa=:original")
    int anonymizeReceiverVpa(@Param("original") String original, @Param("replacement") String replacement);
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.senderVpa = :senderVpa and t.status = :status and t.signedAt > :signedAt")
    BigDecimal sumSettledAmountSince(@Param("senderVpa") String senderVpa,
                                     @Param("status") Transaction.Status status,
                                     @Param("signedAt") Instant signedAt);
}



