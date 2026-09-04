package com.homerun.domain.contract.controller;

import com.homerun.domain.contract.dto.response.ContractChecklistGuideResponse;
import com.homerun.domain.contract.service.ContractChecklistGuideService;
import com.homerun.domain.contract.type.ContractChecklistPhase;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contract-checklist")
@Tag(name = "계약 체크리스트 안내", description = "COM-12-01 계획·계약 생성 없이 조회하는 상시 안내")
public class ContractChecklistGuideController {
    private final ContractChecklistGuideService service;

    public ContractChecklistGuideController(ContractChecklistGuideService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "계약 체크리스트 상시 조회",
            description = "로그인과 필수 약관 동의가 필요합니다. planId는 필요하지 않습니다. phase를 생략하면 전체 항목을 조회합니다. 개인별 체크 상태는 저장하지 않습니다.")
    public ApiResponse<ContractChecklistGuideResponse> get(
            @RequestParam(required = false) ContractChecklistPhase phase) {
        return ApiResponse.success(service.get(phase));
    }
}
