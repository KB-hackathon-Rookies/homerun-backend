package com.homerun.domain.education.controller;

import com.homerun.domain.education.dto.request.QuizSubmitRequest;
import com.homerun.domain.education.dto.response.EducationDtos.ModuleDetail;
import com.homerun.domain.education.dto.response.EducationDtos.ModuleSummary;
import com.homerun.domain.education.dto.response.EducationDtos.QuizSubmitResult;
import com.homerun.domain.education.service.EducationService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/education")
@Tag(name = "교육과 예방", description = "홈 4-7 교육 모듈 콘텐츠·퀴즈·진행률(회원 스코프)")
public class EducationController {

    private final EducationService educationService;

    public EducationController(EducationService educationService) {
        this.educationService = educationService;
    }

    @GetMapping("/modules")
    @Operation(summary = "교육 모듈 목록", description = "6개 모듈과 내 진행 상태를 조회한다.")
    public ApiResponse<List<ModuleSummary>> listModules(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(educationService.listModules(principal.memberId()));
    }

    @GetMapping("/modules/{code}")
    @Operation(summary = "교육 모듈 상세", description = "본문과 퀴즈 문항(정답 제외)을 조회한다.")
    public ApiResponse<ModuleDetail> getModule(
            @AuthenticationPrincipal MemberPrincipal principal, @PathVariable String code) {
        return ApiResponse.success(educationService.getModule(principal.memberId(), code));
    }

    @PostMapping("/modules/{code}/read")
    @Operation(summary = "모듈 본문 읽음 표시", description = "콘텐츠를 다 읽었음을 저장한다(진행률 100%).")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable String code) {
        educationService.markRead(principal.memberId(), code);
        return ApiResponse.success(null);
    }

    @PostMapping("/modules/{code}/quiz/submit")
    @Operation(summary = "퀴즈 제출·채점", description = "모든 문항을 제출하면 채점 결과를 돌려주고, 본문 읽음 + 60% 이상이면 DONE 처리한다.")
    public ApiResponse<QuizSubmitResult> submitQuiz(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable String code,
            @Valid @RequestBody QuizSubmitRequest request) {
        return ApiResponse.success(educationService.submitQuiz(principal.memberId(), code, request));
    }
}
