package com.homerun.domain.auth.controller;

import com.homerun.domain.auth.dto.request.ConfirmPhoneVerificationRequest;
import com.homerun.domain.auth.dto.request.SendPhoneVerificationRequest;
import com.homerun.domain.auth.dto.response.PhoneVerificationResponse;
import com.homerun.domain.auth.service.PhoneVerificationService;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/phone")
@Tag(name = "휴대전화 인증", description = "Redis 인증번호 기반 휴대전화 본인 확인")
public class PhoneAuthController {

    private final PhoneVerificationService verificationService;

    public PhoneAuthController(PhoneVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/verification/send")
    @Operation(summary = "회원가입 휴대전화 인증번호 발송")
    public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody SendPhoneVerificationRequest request) {
        verificationService.sendCode(request.phone());
        return ApiResponse.success(null);
    }

    @PostMapping("/verification/confirm")
    @Operation(summary = "회원가입 휴대전화 인증번호 확인")
    public ApiResponse<PhoneVerificationResponse> confirmVerificationCode(
            @Valid @RequestBody ConfirmPhoneVerificationRequest request) {
        return ApiResponse.success(verificationService.confirmCode(request.phone(), request.code()));
    }
}
