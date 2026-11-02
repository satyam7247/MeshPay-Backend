package com.demo.upimesh.service;

public interface SmsProviderService {
    boolean isConfigured();
    void sendVerification(String phoneNumber);
    boolean verifyCode(String phoneNumber, String code);
    String providerName();
}
