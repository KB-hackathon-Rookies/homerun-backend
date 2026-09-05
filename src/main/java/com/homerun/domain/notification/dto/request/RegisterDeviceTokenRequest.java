package com.homerun.domain.notification.dto.request;

import com.homerun.domain.notification.type.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 디바이스 FCM 토큰 등록/갱신 요청. */
public record RegisterDeviceTokenRequest(
        @NotBlank(message = "FCM 토큰은 필수입니다.") String token,
        @NotNull(message = "플랫폼은 필수입니다.") DevicePlatform platform) {}
