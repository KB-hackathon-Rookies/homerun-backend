package com.homerun.domain.plan.controller;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.response.PlanInputResponse;
import com.homerun.domain.plan.service.PlanInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plans")
@Tag(name = "사용자 입력", description = "정책 매칭을 위한 사용자 , 주거 조건")
public class PlanInputController {

    private final PlanInputService planInputService;

    @PostMapping("/{planId}/input")
    @Operation(summary = "사용자 입력")
    public ResponseEntity<PlanInputResponse> save(@PathVariable Long planId, @RequestBody PlanInputRequest request) {
        PlanInputResponse response = planInputService.save(planId, request);

        return ResponseEntity.ok(response);
    }
}
