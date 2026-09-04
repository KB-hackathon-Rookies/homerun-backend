package com.homerun.domain.policy.controller;

import com.homerun.domain.policy.dto.response.GuaranteeAgencyResponse;
import com.homerun.domain.policy.repository.GuaranteeAgencyRepository;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 보증기관 비교(POL-03-08, GTE-01-03). 사용자별 상태가 없는 참조 데이터라 documents 처럼
 * 계획에 안 매단다. */
@RestController
@RequestMapping("/api/v1/guarantee-agencies")
@Tag(name = "보증기관 비교", description = "HF/HUG/SGI 한도 산정 기준과 보증료율 비교")
public class GuaranteeAgencyController {

    private final GuaranteeAgencyRepository repository;

    public GuaranteeAgencyController(GuaranteeAgencyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "보증기관 목록·비교", description = "HF(사람 기준)/HUG(집 기준)/SGI(자체 기준)의 한도 산정 기준과 보증료율을 코드순으로 비교한다.")
    public ApiResponse<List<GuaranteeAgencyResponse>> list() {
        return ApiResponse.success(repository.findAllByOrderByCodeAsc().stream()
                .map(GuaranteeAgencyResponse::from)
                .toList());
    }
}
