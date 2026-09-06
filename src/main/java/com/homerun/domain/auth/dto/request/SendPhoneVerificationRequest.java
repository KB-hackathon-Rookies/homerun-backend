package com.homerun.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SendPhoneVerificationRequest(
        @NotBlank @Pattern(regexp = "^[0-9\\- ]{9,20}$", message = "휴대전화 번호 형식이 올바르지 않습니다.")
        String phone) {}
