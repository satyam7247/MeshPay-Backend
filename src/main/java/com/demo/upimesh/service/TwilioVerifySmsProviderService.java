package com.demo.upimesh.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.regex.Pattern;

@Service
public class TwilioVerifySmsProviderService implements SmsProviderService {
    private static final String VERIFY_API = "https://verify.twilio.com/v2/Services/";
    private static final Pattern ACCOUNT_SID = Pattern.compile("AC[0-9a-fA-F]{32}");
    private static final Pattern SERVICE_SID = Pattern.compile("VA[0-9a-fA-F]{32}");

    private final RestTemplate rest;
    private final String accountSid;
    private final String authToken;
    private final String serviceSid;
    private final String provider;

    @Autowired
    public TwilioVerifySmsProviderService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.sms.provider:none}") String provider,
            @Value("${app.sms.twilio.account-sid:}") String accountSid,
            @Value("${app.sms.twilio.auth-token:}") String authToken,
            @Value("${app.sms.twilio.verify-service-sid:}") String serviceSid) {
        this(restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8)).build(),
                provider, accountSid, authToken, serviceSid);
    }

    TwilioVerifySmsProviderService(RestTemplate rest, String provider, String accountSid,
                                   String authToken, String serviceSid) {
        this.rest = rest;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.serviceSid = serviceSid;
        this.provider = provider;
    }

    @Override
    public boolean isConfigured() {
        return "twilio".equalsIgnoreCase(provider)
                && ACCOUNT_SID.matcher(accountSid).matches()
                && !authToken.isBlank()
                && SERVICE_SID.matcher(serviceSid).matches();
    }

    @Override
    public void sendVerification(String phoneNumber) {
        ensureConfigured();
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", phoneNumber);
        body.add("Channel", "sms");
        JsonNode response = post("Verifications", body);
        if (!"pending".equals(response.path("status").asText())) {
            throw providerFailure();
        }
    }

    @Override
    public boolean verifyCode(String phoneNumber, String code) {
        ensureConfigured();
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", phoneNumber);
        body.add("Code", code);
        String status = post("VerificationCheck", body).path("status").asText();
        if ("approved".equals(status)) return true;
        if ("pending".equals(status)) return false;
        throw providerFailure();
    }

    @Override
    public String providerName() {
        return "Twilio Verify";
    }

    private JsonNode post(String operation, LinkedMultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(accountSid, authToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String url = UriComponentsBuilder.fromUriString(VERIFY_API)
                .pathSegment(serviceSid, operation)
                .build()
                .toUriString();
        try {
            JsonNode response = rest.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
            if (response == null) throw providerFailure();
            return response;
        } catch (HttpStatusCodeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "SMS verification provider rejected the request.");
        } catch (ResourceAccessException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SMS verification provider could not be reached.");
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SMS verification is pending Twilio Verify credentials and service setup.");
        }
    }

    private static ResponseStatusException providerFailure() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "SMS verification provider returned an unsupported response.");
    }
}
