package com.demo.upimesh.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioVerifySmsProviderServiceTest {
    private static final String ACCOUNT_SID = "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SERVICE_SID = "VA11111111111111111111111111111111";

    @Test
    void startsSmsVerificationUsingTwilioAndDoesNotReturnTheOtp() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (ACCOUNT_SID + ":secret").getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("https://verify.twilio.com/v2/Services/" + SERVICE_SID + "/Verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", basicAuth))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Channel=sms")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("To=%2B919876543210")))
                .andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON));
        TwilioVerifySmsProviderService provider = new TwilioVerifySmsProviderService(
                rest, "twilio", ACCOUNT_SID, "secret", SERVICE_SID);

        provider.sendVerification("+919876543210");

        server.verify();
    }

    @Test
    void verifiesOnlyProviderApprovedCodes() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://verify.twilio.com/v2/Services/" + SERVICE_SID + "/VerificationCheck"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Code=123456")))
                .andRespond(withSuccess("{\"status\":\"approved\"}", MediaType.APPLICATION_JSON));
        TwilioVerifySmsProviderService provider = new TwilioVerifySmsProviderService(
                rest, "twilio", ACCOUNT_SID, "secret", SERVICE_SID);

        assertTrue(provider.verifyCode("+919876543210", "123456"));
        server.verify();
    }

    @Test
    void doesNotActivateWhenProviderSelectionOrCredentialsAreMissing() {
        TwilioVerifySmsProviderService provider = new TwilioVerifySmsProviderService(
                new RestTemplate(), "none", "", "", "");

        assertFalse(provider.isConfigured());
    }
}
