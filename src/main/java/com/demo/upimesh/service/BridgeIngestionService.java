package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Orchestrates the full server-side pipeline for one inbound packet from a
 * bridge node:
 *
 *   1. Hash the ciphertext.
 *   2. Try to claim that hash via the idempotency cache.
 *      - If already claimed: this is a duplicate. Drop it.
 *   3. Decrypt the ciphertext with the server's private key.
 *      - If decryption fails: tampered or junk. Reject.
 *   4. Check freshness — reject if signedAt is too old (replay protection).
 *   5. Hand off to SettlementService for the actual debit/credit.
 */
@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);

    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private SettlementService settlement;
    @Autowired private TransactionRepository transactions;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        String packetHash = "?";
        boolean claimed = false;
        try {
            if (packet == null || packet.getCiphertext() == null || packet.getCiphertext().isBlank()) {
                return IngestResult.invalid("?", "missing_ciphertext");
            }
            packetHash = crypto.hashCiphertext(packet.getCiphertext());

            if (transactions.existsByPacketHash(packetHash)) {
                return IngestResult.duplicate(packetHash);
            }

            // ---- Idempotency gate ----
            if (!idempotency.claim(packetHash)) {
                log.info("DUPLICATE packet {} from bridge {} — dropped",
                        shortHash(packetHash), bridgeNodeId);
                return IngestResult.duplicate(packetHash);
            }
            claimed = true;

            // ---- Decrypt ----
            PaymentInstruction instruction;
            try {
                instruction = crypto.decrypt(packet.getCiphertext());
            } catch (Exception e) {
                log.warn("Decryption failed for packet {}: {}",
                        shortHash(packetHash), e.getMessage());
                return IngestResult.invalid(packetHash, "decryption_failed");
            }

            // ---- Freshness check (replay protection) ----
            if (instruction.getSignedAt() == null) {
                return IngestResult.invalid(packetHash, "missing_signed_at");
            }
            if (instruction.getNonce() == null || instruction.getNonce().isBlank()
                    || instruction.getNonce().length() > 64) {
                return IngestResult.invalid(packetHash, "missing_or_invalid_transaction_id");
            }
            if (transactions.existsByTransactionNonce(instruction.getNonce())) {
                return IngestResult.duplicate(packetHash);
            }
            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
            if (ageSeconds > maxAgeSeconds) {
                log.warn("Packet {} too old ({}s), rejected",
                        shortHash(packetHash), ageSeconds);
                return IngestResult.invalid(packetHash, "stale_packet");
            }
            if (ageSeconds < -300) { // small clock-skew tolerance
                return IngestResult.invalid(packetHash, "future_dated");
            }

            // ---- Settle ----
            Transaction tx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            if (tx.getStatus() == Transaction.Status.REJECTED) {
                return IngestResult.rejected(packetHash, tx);
            }
            return IngestResult.settled(packetHash, tx);

        } catch (SettlementService.DuplicateTransactionException e) {
            return IngestResult.duplicate(packetHash);
        } catch (DataIntegrityViolationException e) {
            if (transactions.existsByPacketHash(packetHash)) return IngestResult.duplicate(packetHash);
            log.warn("Transaction integrity constraint rejected packet {}", shortHash(packetHash));
            return IngestResult.invalid(packetHash, "transaction_integrity_rejected");
        } catch (IllegalArgumentException e) {
            log.warn("Ingestion rejected packet {}: {}", shortHash(packetHash), e.getMessage());
            return IngestResult.invalid(packetHash, "invalid_instruction");
        } catch (Exception e) {
            if (claimed) {
                idempotency.release(packetHash);
            }
            log.error("Ingestion error: {}", e.getMessage(), e);
            return IngestResult.invalid(packetHash, "internal_error");
        }
    }

    private static String shortHash(String packetHash) {
        if (packetHash == null || packetHash.length() < 12) return String.valueOf(packetHash);
        return packetHash.substring(0, 12) + "...";
    }

    public record IngestResult(String outcome, String packetHash, String reason, Long transactionId) {
        public static IngestResult settled(String hash, Transaction tx) {
            return new IngestResult("SETTLED", hash, null, tx.getId());
        }
        public static IngestResult rejected(String hash, Transaction tx) {
            String reason = tx.getRejectionReason() == null ? "settlement_rejected" : tx.getRejectionReason();
            return new IngestResult("REJECTED", hash, reason, tx.getId());
        }
        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE_DROPPED", hash, null, null);
        }
        public static IngestResult invalid(String hash, String reason) {
            return new IngestResult("INVALID", hash, reason, null);
        }
    }
}
