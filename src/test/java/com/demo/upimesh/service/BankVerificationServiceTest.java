package com.demo.upimesh.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BankVerificationServiceTest {
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String CLIENT_ID = "cashfree-test-client";
    private static final String CLIENT_SECRET = "cashfree-test-secret";

    @Test
    void verifiesOnlyProviderConfirmedProductionAccountAndNameMatch() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://api.cashfree.com/verification/bank-account/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-client-id", CLIENT_ID))
                .andExpect(header("x-client-secret", CLIENT_SECRET))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "bank_account":"1234567890123456",
                          "ifsc":"HDFC0000240",
                          "name":"Example User"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "reference_id":234,
                          "name_at_bank":"EXAMPLE USER",
                          "bank_name":"HDFC BANK",
                          "name_match_result":"DIRECT_MATCH",
                          "account_status":"VALID",
                          "account_status_code":"ACCOUNT_IS_VALID"
                        }
                        """, MediaType.APPLICATION_JSON));
        BankVerificationService service = service(rest, "production");

        var result = service.verify("Example User", "1234567890123456", "HDFC0000240");

        assertEquals("VERIFIED", result.status());
        assertEquals("EXAMPLE USER", result.verifiedAccountHolderName());
        assertEquals("HDFC BANK", result.verifiedBankName());
        assertEquals("234", result.providerReference());
        assertEquals("CASHFREE_PRODUCTION", result.verificationMode());
        server.verify();
    }

    @Test
    void sandboxResponsesNeverMarkRealAccountsVerified() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://sandbox.cashfree.com/verification/bank-account/sync"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "reference_id":321,
                          "name_at_bank":"SANDBOX USER",
                          "name_match_result":"DIRECT_MATCH",
                          "account_status":"VALID",
                          "account_status_code":"ACCOUNT_IS_VALID"
                        }
                        """, MediaType.APPLICATION_JSON));
        BankVerificationService service = service(rest, "sandbox");

        var result = service.verify("Example User", "1234567890123456", "HDFC0000240");

        assertEquals("PENDING", result.status());
        assertNull(result.verifiedAccountHolderName());
        assertEquals("CASHFREE_SANDBOX", result.verificationMode());
        assertTrue(result.message().contains("does not verify a real bank account"));
        server.verify();
    }

    @Test
    void rejectsNameMismatchEvenWhenAccountExists() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://api.cashfree.com/verification/bank-account/sync"))
                .andRespond(withSuccess("""
                        {
                          "name_at_bank":"DIFFERENT PERSON",
                          "name_match_result":"NO_MATCH",
                          "account_status":"VALID",
                          "account_status_code":"ACCOUNT_IS_VALID"
                        }
                        """, MediaType.APPLICATION_JSON));
        BankVerificationService service = service(rest, "production");

        var result = service.verify("Example User", "1234567890123456", "HDFC0000240");

        assertEquals("FAILED", result.status());
        assertNull(result.verifiedAccountHolderName());
        server.verify();
    }

    @Test
    void failsClosedWhenCredentialsOrEncryptionKeyAreMissing() {
        BankVerificationService service = new BankVerificationService(new RestTemplate(),
                new BankDataEncryptionService(""), "cashfree", "production", CLIENT_ID, CLIENT_SECRET);

        assertFalse(service.isConfigured());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify("Example User", "1234567890123456", "HDFC0000240"));
        assertEquals(503, error.getStatusCode().value());
    }

    @Test
    void doesNotVerifyResponsesMissingProviderNameMatchEvidence() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://api.cashfree.com/verification/bank-account/sync"))
                .andRespond(withSuccess("""
                        {
                          "account_status":"VALID",
                          "account_status_code":"ACCOUNT_IS_VALID"
                        }
                        """, MediaType.APPLICATION_JSON));
        BankVerificationService service = service(rest, "production");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify("Example User", "1234567890123456", "HDFC0000240"));

        assertEquals(502, error.getStatusCode().value());
        server.verify();
    }

    private static BankVerificationService service(RestTemplate rest, String environment) {
        return new BankVerificationService(rest, new BankDataEncryptionService(ENCRYPTION_KEY),
                "cashfree", environment, CLIENT_ID, CLIENT_SECRET);
    }
}
