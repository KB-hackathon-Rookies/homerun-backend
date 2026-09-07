package com.homerun.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UnregisterDeviceTokenRequest(@NotBlank String token) {}
