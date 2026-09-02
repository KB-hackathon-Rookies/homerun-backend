package com.homerun.domain.auth.dto.response;

public record EmailVerificationResponse(String verificationToken, long expiresInSeconds) {}
