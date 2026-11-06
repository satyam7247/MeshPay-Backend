# UPI Offline Mesh â€” Demo

A Spring Boot backend that demonstrates **offline UPI payments routed through a Bluetooth-style mesh network**. You're in a basement with zero connectivity. You send your friend â‚¹500. Your phone encrypts the payment, broadcasts it to nearby phones, and the packet hops device-to-device until *some* phone walks outside, gets 4G, and silently uploads it to this backend. The backend decrypts, deduplicates, and settles.

This repo is the **server side** of that system, plus a software simulator of the mesh so you can demo the whole flow on a single laptop without any real Bluetooth hardware.

---

## Table of Contents

1. [What this demo proves](#what-this-demo-proves)
2. [How to run it](#how-to-run-it)
3. [The demo flow (step by step)](#the-demo-flow-step-by-step)
4. [Architecture](#architecture)
5. [The three hard problems and how they're solved](#the-three-hard-problems-and-how-theyre-solved)
6. [File-by-file walkthrough](#file-by-file-walkthrough)
7. [API reference](#api-reference)
8. [Tests](#tests)
9. [What's NOT real (and what would change for production)](#whats-not-real-and-what-would-change-for-production)
10. [Honest limitations of the concept](#honest-limitations-of-the-concept)

---

## What this demo proves

The system shows three things working end to end:

1. **A payment can travel from sender to backend through untrusted intermediaries** without any of them being able to read or tamper with it. (Hybrid RSA + AES-GCM encryption.)
2. **Even if the same payment reaches the backend simultaneously through multiple bridge nodes, it settles exactly once.** (Idempotency via atomic compare-and-set on the ciphertext hash.)
3. **A tampered or replayed packet is rejected** before it touches the ledger.

You'll see all three in the dashboard.

---

## How to run it

### Profiles and environment

The default `dev` profile uses an in-memory H2 database. Data in H2 is temporary and is cleared when the backend stops. Run `mvnw.cmd spring-boot:run` from this repository. To use MySQL in development, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` before starting; Flyway selects the matching vendor scripts automatically.

For production, activate `prod` with `SPRING_PROFILES_ACTIVE=prod` (or `--spring.profiles.active=prod`), provide a persistent MySQL 8.0.29+ `DB_URL` and credentials, and keep `UPI_MESH_DEMO_SEED=false` and `UPI_MESH_DEMO_ENDPOINTS_ENABLED=false`. Production uses `ddl-auto=validate`; Flyway applies the versioned `db/migration/mysql` migrations before Hibernate validates the schema. Existing pre-Flyway databases are baselined at version 1, then receive the incremental migration. Startup is rejected for demo mode or an H2 URL. Configure `APP_SECURITY_TRUSTED_PROXY_ADDRESSES` with only the load balancer's socket IP addresses so client IP rate limits work as intended.

Development uses Hibernate `ddl-auto=update` and selects Flyway scripts from the active database vendor. The default H2 URL uses `db/migration/h2`; a MySQL `DB_URL` uses `db/migration/mysql`. Set `SPRING_FLYWAY_LOCATIONS` only when you need to override the default `classpath:db/migration/{vendor}`. `.env.example` lists every environment placeholder used by `application*.properties`; replace placeholders only for providers you enable. The required production variables are `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; set `APP_SECURITY_TRUSTED_PROXY_ADDRESSES` when deployed behind a load balancer. Provider credentials are required only when that provider is enabled.

Feature-specific server variables: Twilio OTP delivery needs `SMS_PROVIDER=twilio`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_VERIFY_SERVICE_SID`; Brevo email needs `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, and `BREVO_SENDER_NAME`; Cashfree bank verification needs `BANK_VERIFICATION_PROVIDER=cashfree`, its environment and client credentials, plus `BANK_DATA_ENCRYPTION_KEY`. Keep all credentials in the backend environment or secrets manager. `.env.example` contains placeholders only. `DB_DDL_AUTO` applies to development; production is fixed to `validate`.

App Lock sessions expire after five minutes without an authenticated request by default. Set `APP_LOCK_IDLE_TIMEOUT_SECONDS` to change that idle window; `APP_LOCK_IDLE_TIMEOUT_SECONDS=300` is shown in `.env.example`.

### Prerequisites

- **JDK 17 or newer** installed and on PATH (or `JAVA_HOME` set). Check with `java -version`.
- H2 is included for a quick development run. MySQL 8.0.29 or newer is optional in development and required in production; configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when using it.

### Run on Windows

Open a terminal in the project folder and run:

```cmd
mvnw.cmd spring-boot:run
```

The first run downloads Maven (~10 MB) and all dependencies (~80 MB) â€” give it a couple of minutes. Subsequent runs start in a few seconds.

### Run on Mac/Linux

```bash
./mvnw spring-boot:run
```

The demo dashboard's `/api/demo/**` and `/api/mesh/**` endpoints are disabled by default. To use the local simulator, set `UPI_MESH_DEMO_ENDPOINTS_ENABLED=true` in the backend `.env` before starting it. Keep this false outside a trusted local demo; these endpoints can create simulated settlements. The real `/api/bridge/ingest` endpoint always requires a signed-in app bearer token.

### Open the dashboard

Once you see `Started UpiMeshApplication in X.XXX seconds`, open:

**http://localhost:8080**

You'll get a dark dashboard with everything you need to drive the demo.

### Stop the server

`Ctrl+C` in the terminal.

### Run the tests

```cmd
mvnw.cmd test
```

The interesting one is `IdempotencyConcurrencyTest` â€” it fires three threads delivering the same packet simultaneously and asserts that exactly one settles.

---

## The demo flow (step by step)

The dashboard has four buttons that walk through the full pipeline. The intended sequence:

### Step 1 â€” Compose a payment

Choose sender, receiver, amount, PIN. Click **"ðŸ“¤ Inject into Mesh"**.

**What actually happens on the backend:**
- The server pretends to be the sender's phone.
- It builds a `PaymentInstruction` with a unique nonce and current timestamp.
- It encrypts that with the server's RSA public key (using hybrid encryption â€” see below).
- It wraps the ciphertext in a `MeshPacket` with a TTL of 5.
- It hands the packet to `phone-alice`, an offline virtual device.

You'll see `phone-alice` now holds 1 packet.

### Step 2 â€” Run gossip rounds

Click **"ðŸ”„ Run Gossip Round"**. Then click it again.

Each round, every device that holds a packet broadcasts it to every other device within "Bluetooth range" (which, in our simulator, means everyone). TTL decrements per hop.

After 1 round: every device holds the packet. After 2 rounds: still every device â€” TTL is just lower.

In the real system this would happen organically as people walk past each other in the basement.

### Step 3 â€” Bridge node walks outside

Click **"ðŸ“¡ Bridges Upload to Backend"**.

`phone-bridge` is the only device with `hasInternet=true`. The dashboard simulates that phone walking outside and getting 4G. It POSTs every packet it holds to `/api/bridge/ingest`.

The backend pipeline runs:
1. Hash the ciphertext (`SHA-256`).
2. Try to claim the hash in the idempotency cache.
3. If claimed: decrypt with the server's RSA private key.
4. Verify freshness (signedAt within 24 hours).
5. Run the debit/credit in a single DB transaction.

Watch the **Account Balances** table â€” money has moved. Watch the **Transaction Ledger** â€” a new row appears.

### Step 4 â€” Demonstrate idempotency (the killer feature)

Reset the mesh. Inject a single packet. Run gossip 2 times. Now **all 5 devices hold the same packet, including multiple bridges in a more complex setup**.

To really see idempotency in action, modify `MeshSimulatorService.java` to seed multiple bridge devices, or just:

1. Click "Inject" once.
2. Click "Gossip" twice.
3. Click "Flush Bridges" â€” only `phone-bridge` is a bridge in the default seed, so just one upload happens.

To exercise the *concurrent duplicate* case properly, run the test:
```cmd
mvnw.cmd test -Dtest=IdempotencyConcurrencyTest#singlePacketDeliveredByThreeBridgesSettlesExactlyOnce
```

This test creates one packet, fires 3 threads at `BridgeIngestionService.ingest()` simultaneously, and verifies that exactly one settles, two are dropped as duplicates, and the sender is debited exactly once.

---

## Architecture

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚                         SENDER PHONE (offline)                          â”‚
â”‚  PaymentInstruction { sender, receiver, amount, pinHash, nonce, time }  â”‚
â”‚              â”‚                                                          â”‚
â”‚              â–¼ encrypt with server's RSA public key                     â”‚
â”‚   MeshPacket { packetId, ttl, createdAt, ciphertext }                   â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                                       â”‚ Bluetooth gossip
                                       â–¼
        â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”  hop   â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”  hop   â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”
        â”‚stranger1â”‚ â”€â”€â”€â”€â”€â–¶ â”‚stranger2â”‚ â”€â”€â”€â”€â”€â–¶ â”‚ bridge  â”‚ â—€â”€â”€ walks outside
        â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜        â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜        â””â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”˜     gets 4G
                                                   â”‚
                                                   â–¼ HTTPS POST
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚                     SPRING BOOT BACKEND (this project)                  â”‚
â”‚                                                                         â”‚
â”‚  /api/bridge/ingest                                                     â”‚
â”‚       â”‚                                                                 â”‚
â”‚       â–¼                                                                 â”‚
â”‚  [1] hash ciphertext (SHA-256)                                          â”‚
â”‚       â”‚                                                                 â”‚
â”‚       â–¼                                                                 â”‚
â”‚  [2] IdempotencyService.claim(hash)  â—€â”€â”€ atomic putIfAbsent (â‰ˆ Redis    â”‚
â”‚       â”‚                                  SETNX). Duplicates rejected    â”‚
â”‚       â”‚                                  here, before any work.         â”‚
â”‚       â–¼                                                                 â”‚
â”‚  [3] HybridCryptoService.decrypt(ciphertext)                            â”‚
â”‚       â”‚       (RSA-OAEP unwraps AES key, AES-GCM decrypts payload       â”‚
â”‚       â”‚        AND verifies the auth tag â€” tampering = exception)       â”‚
â”‚       â–¼                                                                 â”‚
â”‚  [4] Freshness check: signedAt within last 24h                          â”‚
â”‚       â”‚                                                                 â”‚
â”‚       â–¼                                                                 â”‚
â”‚  [5] SettlementService.settle()                                         â”‚
â”‚       @Transactional: debit sender, credit receiver, write ledger       â”‚
â”‚       @Version on Account = optimistic locking (defense in depth)       â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

---

## The three hard problems and how they're solved

### Problem 1: Untrusted intermediates

A random stranger's phone is carrying your transaction. How do you stop them from reading the amount or changing it?

**Solution: Hybrid encryption (RSA-OAEP + AES-GCM).**

The sender encrypts the payload with the server's public key. Only the server holds the private key, so intermediates see opaque ciphertext.

But RSA can only encrypt small data (~245 bytes for a 2048-bit key), and our payload is JSON that could exceed that. So we use the standard hybrid pattern:

1. Generate a fresh AES-256 key for *this packet*.
2. Encrypt the JSON with **AES-256-GCM** (fast + authenticated).
3. Encrypt just the AES key with **RSA-OAEP**.
4. Concatenate: `[256 bytes RSA-encrypted AES key][12 bytes IV][AES ciphertext + 16-byte GCM tag]`.

**Why GCM specifically?** It's authenticated encryption. If an intermediate flips one bit anywhere in the ciphertext, decryption throws an exception â€” the GCM tag won't verify. The server cannot be tricked into processing tampered data.

This is the same scheme TLS uses. See `HybridCryptoService.java`.

### Problem 2: The duplicate-storm

Three bridge nodes hold the same packet. They all walk outside at the same instant. They all POST to `/api/bridge/ingest` within milliseconds of each other. If you naively process all three, the sender is debited â‚¹1500 instead of â‚¹500.

**Solution: Atomic compare-and-set on the ciphertext hash.**

The very first thing the server does on receiving a packet is compute `SHA-256(ciphertext)` and try to "claim" that hash:

```java
// IdempotencyService.java
Instant prev = seen.putIfAbsent(packetHash, now);
return prev == null;  // true = first claimer, false = duplicate
```

`ConcurrentHashMap.putIfAbsent` is atomic. Even if 100 threads call it at the exact same nanosecond, exactly one returns `null` (the first claimer) and the rest return the existing entry. Only the first claimer proceeds to decrypt and settle. The rest are short-circuited as `DUPLICATE_DROPPED`.

**Why hash the ciphertext, not the packetId or the cleartext?**
- `packetId` can be rewritten by a malicious intermediate. Two copies of the same payment could have different packetIds. Bad key.
- The cleartext requires decryption first. We want to dedupe *before* spending CPU on RSA.
- The ciphertext is authenticated by GCM, so any tampering is detectable on decrypt. Two legitimate deliveries of the same payment have byte-identical ciphertexts (AES is deterministic for a given key+IV+plaintext, and the same packet means the same key+IV+plaintext).

In production this `ConcurrentHashMap` becomes Redis: `SET key NX EX 86400`. Same semantics, distributed across replicas.

There's also a defense-in-depth fallback: `transactions.packet_hash` has a unique index. If the cache layer ever fails and two settlements somehow try to write the same hash, the database rejects the second one.

### Problem 3: Replay attacks

An attacker who captured a ciphertext weeks ago could replay it whenever convenient.

**Solution: Two layers.**

1. **Inside the encrypted payload**, the sender includes `signedAt` (epoch millis). The server rejects any packet older than 24 hours. The attacker can't change `signedAt` without breaking the GCM tag.
2. **Inside the encrypted payload**, the sender includes a **nonce** (UUID). Even if Alice legitimately sends Bob â‚¹100 twice, the nonces differ â†’ ciphertexts differ â†’ hashes differ â†’ both settle. But a *replay* of one specific signed packet is byte-identical, so the idempotency cache catches it.

See `BridgeIngestionService.java` for the freshness check.

---

## File-by-file walkthrough

```
upi-offline-mesh/
â”œâ”€â”€ pom.xml                                  Maven build, Spring Boot 3.3, Java 17
â”œâ”€â”€ mvnw, mvnw.cmd                           Maven wrapper (no install needed)
â”œâ”€â”€ README.md                                this file
â””â”€â”€ src/main/
    â”œâ”€â”€ resources/
    â”‚   â”œâ”€â”€ application.properties           MySQL datasource, port 8080, TTLs
    â”‚   â””â”€â”€ templates/dashboard.html         The interactive demo UI
    â””â”€â”€ java/com/demo/upimesh/
        â”œâ”€â”€ UpiMeshApplication.java          Spring Boot main class
        â”‚
        â”œâ”€â”€ model/                           â”€â”€ Domain layer
        â”‚   â”œâ”€â”€ Account.java                 JPA entity. @Version = optimistic lock
        â”‚   â”œâ”€â”€ AccountRepository.java       Spring Data JPA
        â”‚   â”œâ”€â”€ Transaction.java             Settled-tx ledger. unique idx on packetHash
        â”‚   â”œâ”€â”€ TransactionRepository.java   Spring Data JPA
        â”‚   â”œâ”€â”€ MeshPacket.java              Wire format. Outer fields readable, ciphertext opaque
        â”‚   â””â”€â”€ PaymentInstruction.java      Decrypted payload (sender/receiver/amount/nonce/time)
        â”‚
        â”œâ”€â”€ crypto/                          â”€â”€ Cryptography layer
        â”‚   â”œâ”€â”€ ServerKeyHolder.java         Generates RSA-2048 keypair on startup
        â”‚   â””â”€â”€ HybridCryptoService.java     RSA-OAEP + AES-256-GCM encrypt/decrypt + ciphertext hash
        â”‚
        â”œâ”€â”€ service/                         â”€â”€ Business logic
        â”‚   â”œâ”€â”€ DemoService.java             Seeds accounts, simulates a sender phone
        â”‚   â”œâ”€â”€ VirtualDevice.java           One simulated phone in the mesh
        â”‚   â”œâ”€â”€ MeshSimulatorService.java    Gossip protocol across virtual devices
        â”‚   â”œâ”€â”€ IdempotencyService.java      ConcurrentHashMap = JVM-local Redis SETNX
        â”‚   â”œâ”€â”€ SettlementService.java       @Transactional debit + credit + ledger insert
        â”‚   â””â”€â”€ BridgeIngestionService.java  THE pipeline: hash â†’ claim â†’ decrypt â†’ freshness â†’ settle
        â”‚
        â”œâ”€â”€ controller/                      â”€â”€ HTTP layer
        â”‚   â”œâ”€â”€ ApiController.java           All REST endpoints
        â”‚   â””â”€â”€ DashboardController.java     Serves the dashboard HTML at /
        â”‚
        â””â”€â”€ config/
            â””â”€â”€ AppConfig.java               @EnableScheduling for cache eviction

src/test/java/com/demo/upimesh/
â””â”€â”€ IdempotencyConcurrencyTest.java          The 3-bridges-at-once test + tamper test
```

---

## Bank account linking (development mock)

The Wallet screen supports a step-by-step account-linking demo. The OTP and bank
verification providers are simulated: no SMS is sent and no bank is contacted. The mock
OTP is returned as `simulatedOtp` so the development flow can be exercised. Do not enable
or use this mock flow for production bank verification or enter real bank account details.
The searchable picker offers the supported banks listed in the mobile app; the backend
rejects bank names outside that catalog.

The OTP is bound to the authenticated user's registered phone number, expires after five
minutes, can be resent after 30 seconds, and permits at most five incorrect attempts.
Bank account numbers are checked for matching digit strings; only the masked value is
stored. The mock verification marks numbers ending in `0000` as `FAILED` and other valid
test numbers as `VERIFIED`. Every account response labels its mode `MOCK_SIMULATED`.
The backend stores the account number only as a masked string plus its final four digits.

Authenticated API routes:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/bank-accounts/send-otp` | Generate a mock code; optional `challengeId` requests a resend |
| POST | `/api/bank-accounts/verify-otp` | Verify the six-digit mock code |
| POST | `/api/bank-accounts/link` | Create an account with `PENDING` status after OTP verification |
| POST | `/api/bank-accounts/verify` | Run simulated verification and return `VERIFIED` or `FAILED` |
| GET | `/api/bank-accounts/my-accounts` | List the signed-in user's masked account records |
| DELETE | `/api/bank-accounts/{accountId}` | Remove an account owned by the signed-in user |

---

## API reference

| Method | Path | What it does |
|---|---|---|
| GET | `/` | Dashboard HTML |
| GET | `/api/server-key` | Server's RSA public key (base64) |
| GET | `/api/accounts` | All accounts and balances |
| GET | `/api/transactions` | Last 20 transactions |
| GET | `/api/mesh/state` | Current state of every virtual device |
| POST | `/api/demo/send` | Simulate sender phone â€” encrypt + inject packet |
| POST | `/api/mesh/gossip` | Run one round of gossip across the mesh |
| POST | `/api/mesh/flush` | Bridges with internet upload to backend (parallel) |
| POST | `/api/mesh/reset` | Clear mesh + idempotency cache |
| POST | `/api/bridge/ingest` | **The production endpoint.** Real bridges POST here |
| POST | `/api/bank-accounts/send-otp` | Generate a mock code for the registered phone |
| POST | `/api/bank-accounts/verify-otp` | Verify the six-digit mock code |
| POST | `/api/bank-accounts/link` | Create a pending linked account after OTP verification |
| POST | `/api/bank-accounts/verify` | Run development-only simulated verification |
| GET | `/api/bank-accounts/my-accounts` | List the signed-in user's masked account records |
| DELETE | `/api/bank-accounts/{accountId}` | Remove an account owned by the signed-in user |


### Request format for `/api/bridge/ingest`

```http
POST /api/bridge/ingest
Content-Type: application/json
X-Bridge-Node-Id: phone-bridge-42
X-Hop-Count: 3

{
  "packetId": "550e8400-e29b-41d4-a716-446655440000",
  "ttl": 2,
  "createdAt": 1730000000000,
  "ciphertext": "base64-encoded-RSA-and-AES-blob"
}
```

Response:
```json
{
  "outcome": "SETTLED",                     // or "DUPLICATE_DROPPED" or "INVALID"
  "packetHash": "a3f8c9...",
  "reason": null,                            // populated on INVALID
  "transactionId": 42                        // populated on SETTLED
}
```

---

## Tests

Run all tests:
```
mvnw.cmd test
```

The backend test suite includes unit tests for auth, OTP, bank verification, privacy, and provider behavior, plus integration tests for mesh idempotency and encryption.

- **`encryptDecryptRoundTrip`** â€” sanity-check that hybrid encryption is symmetric.
- **`tamperedCiphertextIsRejected`** â€” flip a byte in the ciphertext, verify that `BridgeIngestionService` returns `INVALID` instead of crashing or settling.
- **`singlePacketDeliveredByThreeBridgesSettlesExactlyOnce`** â€” the headline test. Three threads, one packet, simultaneous delivery. Asserts exactly one `SETTLED`, two `DUPLICATE_DROPPED`, and that the sender's balance changed by exactly the amount once.

---

## Bank linking and production provider setup

The mobile bank-linking module does not use mock OTPs or simulated bank success. Login and registration remain separate and unchanged. Provider calls stay unavailable until their required credentials and server configuration are present.

### SMS OTP

Configure an approved Twilio Verify service on the server:

- `SMS_PROVIDER=twilio`
- `TWILIO_ACCOUNT_SID`
- `TWILIO_AUTH_TOKEN`
- `TWILIO_VERIFY_SERVICE_SID`

The backend sends and checks OTPs through Twilio Verify. OTP values are never returned or stored by this application. Twilio account/service setup, enabled SMS regions, and production approval are required.

### Cashfree bank-account verification

The bank verification adapter uses Cashfree Secure ID's [Bank Account Verification Sync V2 API](https://www.cashfree.com/docs/api-reference/vrs/v2/bav-v2/bank-account-verification-sync-v2.md). It checks the provider's account status and name-match result; only `VALID` plus `DIRECT_MATCH` or `GOOD_PARTIAL_MATCH` can become `VERIFIED`. Sandbox responses always remain `PENDING` and are explicitly marked test-only.

Configure the backend environment:

- `BANK_VERIFICATION_PROVIDER=cashfree`
- `CASHFREE_ENVIRONMENT=sandbox` or `production`
- `CASHFREE_CLIENT_ID`
- `CASHFREE_CLIENT_SECRET`
- `BANK_DATA_ENCRYPTION_KEY` — base64-encoded, randomly generated 32-byte AES key; store it in a secrets manager and retain it for decrypting stored names.

Cashfree's official request requires both the account number and IFSC. Therefore this provider-specific flow asks for IFSC and sends it only to Cashfree over HTTPS; the app does not store it. The optional bank-associated phone field is not sent. Full account numbers are not persisted. Account-holder names are encrypted at rest using AES-GCM.

Before production, create and enable a Cashfree Secure ID account, generate production API keys, and whitelist the backend's static public IPv4 address in the Secure ID dashboard. This adapter uses IP-whitelist authentication; a Cashfree public-key signature 2FA setup is not currently supported. Sandbox credentials and URLs are distinct from production and cannot produce a real verified status. Cashfree currently documents Deutsche Bank and Paytm Payments Bank as unsupported for this API.

### Other financial capabilities

Setu balance enquiry remains pending FIU onboarding, production approval, explicit user consent, and a complete consent-backed data-fetch flow. User-specific UPI QR generation, payment creation/webhook processing, and provider-confirmed transaction history remain unavailable pending PSP/acquirer onboarding and verified settlement routing. No balance, payment, QR destination, or transaction is simulated or reported as successful.

---

## What's NOT real (and what would change for production)

This is a teaching demo. To make it production-grade you'd swap these things:

| What's in the demo | What it would be in production |
|---|---|
| MySQL database configured through environment variables | Managed MySQL with backups and production credentials |
| `ConcurrentHashMap` for idempotency | Redis with `SET NX EX` |
| RSA keypair regenerated on every startup | Private key in HSM (AWS KMS, HashiCorp Vault). Public key cached on devices. |
| Server-side `DemoService.createPacket()` | Same code running on Android, in a Kotlin port |
| Software-simulated mesh (`MeshSimulatorService`) | Real BLE GATT or Wi-Fi Direct between phones |
| One settlement service that owns the ledger | Integration with NPCI / a real bank core |
| App bearer-token auth on `/api/bridge/ingest`; no bridge-specific identity yet | Mutual TLS or signed bridge-node certificates |
| In-memory accounts seeded on startup | Real KYC'd users, real VPAs, real PIN verification against the bank |
| No rate limiting | Per-bridge-node rate limit, per-sender velocity check |
| Logs to console | Structured logs to a SIEM, alerts on `INVALID` spikes |

The cryptography and idempotency paths have automated tests, but this project is not production-ready: payment settlement is a demo ledger, BLE hardware integration is absent, and bridge-node identity, production database operations, and PSP onboarding still need implementation.

---

## Honest limitations of the concept

I want this README to be useful to you when someone reviews the project, so let's be straight about what this design **does not** solve. These are not implementation bugs â€” they're inherent to "no internet, anywhere in the chain":

1. **The receiver has no way to verify the sender has the funds.** When sender hands receiver a phone showing "â‚¹500 sent," it's an IOU, not a settled payment. If the sender's account is empty when the packet finally reaches the backend, the settlement will be `REJECTED` and the receiver is out â‚¹500 with no recourse. *This is why real offline UPI (UPI Lite) uses a pre-funded hardware-backed wallet* â€” to give cryptographic proof of available funds offline.
2. **A malicious sender can double-spend offline.** With â‚¹500 in their account, they could send a packet to Bob in basement A, walk to basement B, and send another â‚¹500 to Carol. Whichever packet hits the backend first wins; the other gets `REJECTED`. Same root cause as #1.
3. **Bluetooth in real life is hard.** Background BLE on Android is heavily throttled since Android 8. iOS peripheral mode is locked down. Two strangers' phones reliably forming a GATT connection while the apps aren't actively open is genuinely difficult and a lot of energy. This demo skips that problem entirely by simulating the mesh.
4. **Privacy / liability.** A stranger carries your encrypted transaction packet on their phone. They can't read it, but its existence is metadata. In a real deployment you'd want to think about regulatory disclosures and what happens if a device is seized.

For a college / portfolio project: name the concept honestly as **"mesh-routed deferred settlement"** rather than "real-time offline UPI," and you'll have a much stronger pitch. The cryptography and idempotency work here is real engineering and worth showing off.

---

## Troubleshooting

**`java: command not found`** â€” Install JDK 17+. On Windows, `winget install EclipseAdoptium.Temurin.17.JDK` or download from adoptium.net.

**Port 8080 already in use** â€” Change `server.port` in `application.properties`.

**First `mvnw.cmd` run hangs for a long time** â€” It's downloading Maven (~10 MB) then dependencies (~80 MB). Give it 2â€“3 minutes on a normal connection. After that, startup is ~5 seconds.

**`mvnw.cmd : The term 'mvnw.cmd' is not recognized`** â€” On PowerShell you need to prefix with `.\`: `.\mvnw.cmd spring-boot:run`.

**Tests fail intermittently** â€” The concurrency test is timing-sensitive. If it ever flakes, run it 3x; if it consistently fails on your hardware, file the actual failure output.

---

## License

Demo code, no license. Use it however you want for learning.

### Verify MySQL migrations

Docker is unavailable in the current environment, so no MySQL migration test could be run here. The no-Docker migration script and manual validation steps are below. `SPRING_FLYWAY_LOCATIONS` can override the default `classpath:db/migration/{vendor}` when needed.

For the no-Docker path, create an empty MySQL 8.0 schema and a second schema containing the old Hibernate-created tables. Set `MYSQL_PWD` in the environment, then run `scripts/verify-mysql-migrations.ps1 -MySqlHost <host> -MySqlPort <port> -MySqlUser <user> -EmptyDatabase <empty_schema> -BaselineDatabase <legacy_schema>`. Start the app once against each schema with `SPRING_PROFILES_ACTIVE=prod`; startup applies Flyway and Hibernate `ddl-auto=validate` checks the resulting schema.
