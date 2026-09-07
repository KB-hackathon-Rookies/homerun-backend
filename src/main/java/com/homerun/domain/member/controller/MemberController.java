package com.homerun.domain.member.controller;

import com.homerun.domain.auth.config.RefreshTokenCookieFactory;
import com.homerun.domain.member.dto.request.UpdateDiagnosisProfileRequest;
import com.homerun.domain.member.dto.request.UpdateMemberProfileRequest;
import com.homerun.domain.member.dto.response.DiagnosisProfileResponse;
import com.homerun.domain.member.dto.response.MemberProfileResponse;
import com.homerun.domain.member.service.MemberService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me")
@Tag(name = "사용자 정보", description = "로그인 사용자 계정 정보 조회·수정·탈퇴")
public class MemberController {

    private final MemberService memberService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public MemberController(MemberService memberService, RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.memberService = memberService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    @GetMapping
    @Operation(summary = "내 정보 조회")
    public ApiResponse<MemberProfileResponse> getMyProfile(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(memberService.getMyProfile(principal.memberId()));
    }

    @PatchMapping
    @Operation(summary = "내 정보 수정", description = "현재는 서비스에서 사용할 이름을 수정할 수 있습니다.")
    public ApiResponse<MemberProfileResponse> updateMyProfile(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody UpdateMemberProfileRequest request) {
        return ApiResponse.success(memberService.updateMyProfile(principal.memberId(), request));
    }

    @DeleteMapping
    @Operation(summary = "회원 탈퇴", description = "계정을 소프트 삭제하고 발급된 모든 Refresh Token을 폐기합니다.")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal MemberPrincipal principal) {
        memberService.withdraw(principal.memberId());
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookieFactory.expire().toString())
                .build();
    }

    @GetMapping("/diagnosis-profile")
    @Operation(summary = "진단용 회원정보 조회", description = "생년월일과 사용자가 확인한 병역기간을 조회합니다. 미확인 값은 null입니다.")
    public ApiResponse<DiagnosisProfileResponse> getDiagnosisProfile(
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(memberService.getDiagnosisProfile(principal.memberId()));
    }

    @PutMapping("/diagnosis-profile")
    @Operation(
            summary = "진단용 회원정보 저장",
            description = "전체 스냅샷을 저장합니다. 생략/null은 초기화입니다. 기존 계획 입력은 변경하지 않으며 기관 검증이나 대출 승인을 뜻하지 않습니다.")
    public ApiResponse<DiagnosisProfileResponse> updateDiagnosisProfile(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody UpdateDiagnosisProfileRequest request) {
        return ApiResponse.success(memberService.updateDiagnosisProfile(principal.memberId(), request));
    }
}
