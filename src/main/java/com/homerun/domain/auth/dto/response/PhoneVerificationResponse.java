package com.homerun.domain.auth.dto.response;

public record PhoneVerificationResponse(String verificationToken, long expiresInSeconds) {}
