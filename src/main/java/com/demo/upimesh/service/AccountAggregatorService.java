package com.demo.upimesh.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountAggregatorService {
    public void requireProductionAccessAndConsent() {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Balance access is pending FIU production approval and an implemented consent-backed data-fetch flow.");
    }

    public String status() {
        return "PENDING_PRODUCTION_APPROVAL_AND_CONSENT_FLOW";
    }
}
