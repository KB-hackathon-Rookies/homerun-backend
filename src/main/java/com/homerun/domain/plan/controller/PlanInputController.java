package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.request.FinancialIncomeConfirmationRequest;
import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.request.PlanInputStepSaveRequest;
import com.homerun.domain.plan.dto.response.PlanInputResponse;
import com.homerun.domain.plan.dto.response.PlanInputResumeResponse;
import com.homerun.domain.plan.dto.response.PlanInputStepSaveResponse;
import com.homerun.domain.plan.service.PlanInputService;
import com.homerun.domain.plan.service.PlanInputStepService;
import com.homerun.domain.plan.type.DiagnosisInputStep;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans/{planId}/input")
@Tag(name = "계획 입력", description = "진단 입력 자동 저장과 모름 필드 관리")
public class PlanInputController {

    private final PlanInputService inputService;
    private final PlanInputStepService stepService;

    public PlanInputController(PlanInputService inputService, PlanInputStepService stepService) {
        this.inputService = inputService;
        this.stepService = stepService;
    }

    @PutMapping
    @Operation(
            summary = "계획 입력 저장",
            description = "미완성 상태를 포함한 전체 입력 스냅샷을 멱등하게 저장합니다. 전세 FIRST_DIAGNOSIS 완료에는"
                    + " 희망보증금·지역·본인 무주택·세대주·고용형태·월소득·순자산이 필요하며, 급여근로자만 기업규모·재직개월이 추가로 필요합니다."
                    + " 혼인·월세·관리비·면적·주택유형은 전세 진단 필수가 아닙니다. 확인하기 어려운 값은 값 없이 unknownFields에 전달합니다."
                    + " 모름이나 입력 완료는 자격 충족을 뜻하지 않습니다.")
    public ApiResponse<PlanInputResponse> save(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody PlanInputRequest request) {
        return ApiResponse.success(inputService.save(principal.memberId(), planId, request));
    }

    @GetMapping
    @Operation(summary = "저장된 계획 입력 조회")
    public ApiResponse<PlanInputResponse> get(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(inputService.get(principal.memberId(), planId));
    }

    @PutMapping("/steps/{stepCode}")
    @Operation(
            summary = "진단 입력 STEP 저장",
            description = "현재 STEP의 값만 병합 저장하고 다음 STEP을 이어하기 위치로 함께 기록합니다. expectedRevision으로 다른 기기의 동시 수정을 감지합니다.")
    public ApiResponse<PlanInputStepSaveResponse> saveStep(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @PathVariable DiagnosisInputStep stepCode,
            @Valid @RequestBody PlanInputStepSaveRequest request) {
        return ApiResponse.success(stepService.save(principal.memberId(), planId, stepCode, request));
    }

    @GetMapping("/resume")
    @Operation(summary = "진단 입력 이어하기", description = "저장된 답변과 완료 STEP을 복원하고 다음에 보여줄 STEP을 반환합니다.")
    public ApiResponse<PlanInputResumeResponse> resume(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long planId) {
        return ApiResponse.success(stepService.resume(principal.memberId(), planId));
    }

    @PutMapping("/financial-income")
    @Operation(
            summary = "금융 소득 확인 또는 수동 교체",
            description =
                    "CONFIRM_OPEN_BANKING은 서버에 동기화된 금액을 그대로 확인하며 monthlyIncome을 보내지 않습니다. USE_MANUAL은 monthlyIncome이 필수이고 출처를 MANUAL로 바꿉니다. 다른 입력은 변경하지 않습니다.")
    public ApiResponse<PlanInputResponse> confirmFinancialIncome(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody FinancialIncomeConfirmationRequest request) {
        return ApiResponse.success(inputService.confirmFinancialIncome(principal.memberId(), planId, request));
    }
}
