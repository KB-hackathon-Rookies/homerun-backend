package com.homerun.domain.plan.plan_jeonse.controller;


import com.homerun.domain.plan.plan_jeonse.dto.PlanCreateRequest;
import com.homerun.domain.plan.plan_jeonse.dto.PlanResponse;
import com.homerun.domain.plan.plan_jeonse.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    @PostMapping
    public ResponseEntity<PlanResponse> create(
            @RequestBody PlanCreateRequest request
    ) {

        PlanResponse response = planService.create(request);

        return ResponseEntity.ok(response);
    }
}