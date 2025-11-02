package com.nlb.interfaces;

public interface RegistrationService {
    RegistrationResult register(String email, String fullName, long tokenHours);

    record RegistrationResult(java.util.UUID userId, java.util.UUID accountId, String token) {}
}