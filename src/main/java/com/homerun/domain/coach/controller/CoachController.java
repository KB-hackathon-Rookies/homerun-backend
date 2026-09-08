package com.homerun.domain.coach.controller;

import com.homerun.domain.coach.dto.request.CoachAskRequest;
import com.homerun.domain.coach.dto.response.CoachAskResponse;
import com.homerun.domain.coach.service.CoachService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 코치 프록시 엔드포인트.
 *
 * <p>프론트는 {@code :8080} 하나만 부른다. ai-coach 를 브라우저에서 직접 호출하면 오리진이 하나 더
 * 늘고 그 서비스를 외부에 공개해야 한다.
 */
@RestController
@Validated
@RequestMapping("/api/v1/coach")
@Tag(name = "AI 코치", description = "단계별 질문에 근거를 붙여 답하는 AI 코치(내부 ai-coach 서비스 프록시)")
public class CoachController {

    private final CoachService coachService;

    public CoachController(CoachService coachService) {
        this.coachService = coachService;
    }

    @PostMapping("/ask")
    @Operation(summary = "AI 코치에게 질문", description = "질문과 현재 단계를 넘기면 근거 문서가 붙은 답변을 돌려준다.")
    public ApiResponse<CoachAskResponse> ask(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody CoachAskRequest request) {
        return ApiResponse.success(coachService.ask(principal.memberId(), request, authorization));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "AI 코치 대화 기록 삭제", description = "Redis에 보관된 현재 회원의 단기 대화 문맥을 즉시 삭제한다.")
    public ApiResponse<Void> clearConversation(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String conversationId) {
        coachService.clearConversation(principal.memberId(), conversationId, authorization);
        return ApiResponse.success(null);
    }
}
