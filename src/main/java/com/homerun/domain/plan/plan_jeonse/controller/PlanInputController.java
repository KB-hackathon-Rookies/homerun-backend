package com.homerun.domain.plan.plan_J.controller;

import com.homerun.domain.plan.plan_J.dto.PlanInputRequest;
import com.homerun.domain.plan.plan_J.dto.PlanInputResponse;
import com.homerun.domain.plan.plan_J.service.PlanInputService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plans")
public class PlanInputController {

    private final PlanInputService planInputService;

    @PostMapping("/{planId}/input")
    public ResponseEntity<PlanInputResponse> save(
            @PathVariable Long planId,
            @RequestBody PlanInputRequest request
    ) {
        PlanInputResponse response =
                planInputService.save(planId, request);

        return ResponseEntity.ok(response);
    }
}