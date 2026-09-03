package com.homerun.domain.document.controller;

import com.homerun.domain.document.dto.DocumentDtos.DocumentIssueGuide;
import com.homerun.domain.document.dto.DocumentDtos.DocumentList;
import com.homerun.domain.document.service.DocumentIssueGuideService;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서류 발급 안내(ISS-01). 사용자별 상태가 없어 계획에 매달지 않는다. */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "서류 발급 안내", description = "발급방법, 공식 경로, 방문 준비물")
public class DocumentController {

    private final DocumentIssueGuideService service;

    public DocumentController(DocumentIssueGuideService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "서류 목록 조회")
    public ApiResponse<DocumentList> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{code}")
    @Operation(summary = "서류 발급 안내 조회", description = "온라인으로 뗄 수 있으면 온라인을 먼저 권한다.")
    public ApiResponse<DocumentIssueGuide> guide(@PathVariable String code) {
        return ApiResponse.success(service.guide(code));
    }
}
