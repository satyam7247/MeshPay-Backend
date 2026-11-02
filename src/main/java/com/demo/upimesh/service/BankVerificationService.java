package com.demo.upimesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class BankVerificationService {
    private static final String SANDBOX_URL = "https://sandbox.cashfree.com/verification/bank-account/sync";
    private static final String PRODUCTION_URL = "https://api.cashfree.com/verification/bank-account/sync";

    private final RestTemplate rest;
    private final String provider;
    private final String environment;
    private final String clientId;
    private final String clientSecret;
    private final BankDataEncryptionService encryption;

    @Autowired
    public BankVerificationService(
            RestTemplateBuilder restTemplateBuilder,
            BankDataEncryptionService encryption,
            @Value("${app.bank-verification.provider:none}") String provider,
            @Value("${app.bank-verification.cashfree.environment:}") String environment,
            @Value("${app.bank-verification.cashfree.client-id:}") String clientId,
            @Value("${app.bank-verification.cashfree.client-secret:}") String clientSecret) {
        this(restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(3))
                        .setReadTimeout(Duration.ofSeconds(10)).build(),
                encryption, provider, environment, clientId, clientSecret);
    }

    BankVerificationService(RestTemplate rest, BankDataEncryptionService encryption,
                            String provider, String environment, String clientId, String clientSecret) {
        this.rest = rest;
        this.encryption = encryption;
        this.provider = provider;
        this.environment = environment;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean isConfigured() {
        return "cashfree".equalsIgnoreCase(provider)
                && ("sandbox".equalsIgnoreCase(environment)
                || "production".equalsIgnoreCase(environment))
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && encryption.isConfigured();
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Bank verification is pending Cashfree credentials, environment selection, and bank-data encryption setup.");
        }
    }

    public VerificationResult verify(String accountHolderName, String accountNumber, String ifsc) {
        requireConfigured();

        Map<String, String> body = new LinkedHashMap<>();
        body.put("bank_account", accountNumber);
        body.put("ifsc", ifsc);
        body.put("name", accountHolderName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", clientId);
        headers.set("x-client-secret", clientSecret);
        String url = "sandbox".equalsIgnoreCase(environment) ? SANDBOX_URL : PRODUCTION_URL;

        ResponseEntity<JsonNode> response;
        try {
            response = rest.postForEntity(url, new HttpEntity<>(body, headers), JsonNode.class);
        } catch (HttpStatusCodeException error) {
            HttpStatus status = error.getStatusCode().value() == 429
                    || error.getStatusCode().is5xxServerError()
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status,
                    "Cashfree bank verification could not complete the request.");
        } catch (ResourceAccessException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cashfree bank verification could not be reached.");
        }

        JsonNode result = response.getBody();
        if (result == null) throw providerFailure();
        String accountStatus = result.path("account_status").asText("");
        String statusCode = result.path("account_status_code").asText("");
        String reference = result.path("reference_id").isMissingNode()
                || result.path("reference_id").isNull()
                ? null : result.path("reference_id").asText();

        if ("sandbox".equalsIgnoreCase(environment)) {
            if (!isKnownAccountStatus(accountStatus)) throw providerFailure();
            return new VerificationResult("PENDING", null, reference, null,
                    "CASHFREE_SANDBOX",
                    "Cashfree returned a sandbox result. This is test-only and does not verify a real bank account.");
        }

        return switch (accountStatus) {
            case "VALID" -> verifiedResult(result, statusCode, reference);
            case "INVALID", "FAILED", "REJECTED", "CANCELLED" ->
                    new VerificationResult("FAILED", null, reference, null,
                            "CASHFREE_PRODUCTION",
                            "Cashfree did not verify this bank account.");
            case "RECEIVED", "APPROVAL_PENDING", "PARTIALLY_APPROVED",
                    "IN_PROCESS", "PROCESSING" ->
                    new VerificationResult("PENDING", null, reference, null,
                            "CASHFREE_PRODUCTION",
                            "Cashfree has not completed bank account verification yet.");
            default -> throw providerFailure();
        };
    }

    public String status() {
        if (!isConfigured()) return "PROVIDER_CONFIGURATION_PENDING";
        return "sandbox".equalsIgnoreCase(environment)
                ? "SANDBOX_CONFIGURED_NOT_LIVE" : "CONFIGURED_NOT_LIVE_VALIDATED";
    }

    public String providerName() {
        return "Cashfree Secure ID Bank Account Verification";
    }

    public String configurationDetail() {
        if (!isConfigured()) {
            return "Requires Cashfree Secure ID API keys, an explicit environment, and a 32-byte bank-data encryption key.";
        }
        if ("sandbox".equalsIgnoreCase(environment)) {
            return "Sandbox only; responses never mark an account as verified.";
        }
        return "Requires production approval, production API keys, and a whitelisted static server IPv4 address.";
    }

    private VerificationResult verifiedResult(JsonNode result, String statusCode, String reference) {
        if (!"ACCOUNT_IS_VALID".equals(statusCode)) throw providerFailure();
        String match = result.path("name_match_result").asText("");
        String bankReportedName = result.path("name_at_bank").asText("");
        if ("DIRECT_MATCH".equals(match) || "GOOD_PARTIAL_MATCH".equals(match)) {
            if (bankReportedName.isBlank()) throw providerFailure();
            return new VerificationResult("VERIFIED", bankReportedName, reference,
                    result.path("bank_name").asText(null), "CASHFREE_PRODUCTION",
                    "Cashfree verified the bank account and returned a matching account holder name.");
        }
        if ("MODERATE_PARTIAL_MATCH".equals(match)) {
            return new VerificationResult("PENDING", null, reference, null,
                    "CASHFREE_PRODUCTION",
                    "Cashfree confirmed the account but the account holder name needs further review.");
        }
        if ("POOR_PARTIAL_MATCH".equals(match) || "NO_MATCH".equals(match)) {
            return new VerificationResult("FAILED", null, reference, null,
                    "CASHFREE_PRODUCTION",
                    "Cashfree could not match the entered account holder name.");
        }
        throw providerFailure();
    }

    private static boolean isKnownAccountStatus(String status) {
        return switch (status) {
            case "VALID", "INVALID", "RECEIVED", "FAILED", "REJECTED", "APPROVAL_PENDING",
                    "PARTIALLY_APPROVED", "IN_PROCESS", "PROCESSING", "CANCELLED" -> true;
            default -> false;
        };
    }

    private static ResponseStatusException providerFailure() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Cashfree returned an unsupported bank verification response.");
    }

    public record VerificationResult(String status, String verifiedAccountHolderName,
                                     String providerReference, String verifiedBankName,
                                     String verificationMode, String message) {}
}
