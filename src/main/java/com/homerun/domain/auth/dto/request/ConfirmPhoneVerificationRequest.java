package com.homerun.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmPhoneVerificationRequest(
        @NotBlank @Pattern(regexp = "^[0-9\\- ]{9,20}$", message = "휴대전화 번호 형식이 올바르지 않습니다.")
        String phone,

        @NotBlank @Pattern(regexp = "\\d{6}", message = "인증번호는 숫자 6자리여야 합니다.")
        String code) {}
