package com.homerun.domain.document.controller;

import com.homerun.domain.document.dto.DocumentDtos.DocumentIssueGuide;
import com.homerun.domain.document.dto.DocumentDtos.DocumentList;
import com.homerun.domain.document.dto.VisitPlanDtos.VisitPlan;
import com.homerun.domain.document.dto.VisitPlanDtos.VisitPlanRequest;
import com.homerun.domain.document.service.DocumentIssueGuideService;
import com.homerun.domain.document.service.VisitPlanner;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서류 발급 안내(ISS-01). 사용자별 상태가 없어 계획에 매달지 않는다. */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "서류 발급 안내", description = "발급방법, 공식 경로, 방문 준비물")
public class DocumentController {

    private final DocumentIssueGuideService service;
    private final VisitPlanner planner;

    public DocumentController(DocumentIssueGuideService service, VisitPlanner planner) {
        this.service = service;
        this.planner = planner;
    }

    @GetMapping
    @Operation(summary = "서류 목록 조회")
    public ApiResponse<DocumentList> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping("/visit-plan")
    @Operation(summary = "발급처별 방문 계획", description = "필요한 서류를 넣으면 같은 곳에서 끝나는 것끼리 묶는다. 집에서 되는 것은 방문에서 뺀다.")
    public ApiResponse<VisitPlan> visitPlan(@Valid @RequestBody VisitPlanRequest request) {
        return ApiResponse.success(planner.plan(request.documentCodes(), request.onlineFirst()));
    }

    @GetMapping("/{code}")
    @Operation(summary = "서류 발급 안내 조회", description = "온라인으로 뗄 수 있으면 온라인을 먼저 권한다.")
    public ApiResponse<DocumentIssueGuide> guide(@PathVariable String code) {
        return ApiResponse.success(service.guide(code));
    }
}
