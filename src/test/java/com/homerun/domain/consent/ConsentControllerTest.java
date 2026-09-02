package com.homerun.domain.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.consent.controller.ConsentController;
import com.homerun.domain.consent.controller.PublicConsentController;
import com.homerun.domain.consent.dto.ConsentDtos.ConsentOverview;
import com.homerun.domain.consent.dto.ConsentDtos.ConsentView;
import com.homerun.domain.consent.dto.ConsentDtos.IssueResponse;
import com.homerun.domain.consent.dto.ConsentDtos.MemberConsentStatus;
import com.homerun.domain.consent.service.ConsentService;
import com.homerun.domain.consent.type.ConsentStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.GlobalExceptionHandler;
import com.homerun.global.security.principal.MemberPrincipal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ConsentControllerTest {

    private static final Long MEMBER_ID = 7L;

    private final ConsentService service = mock(ConsentService.class);

    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new ConsentController(service), new PublicConsentController(service)),
            builder -> builder.setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .build());

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(new MemberPrincipal(MEMBER_ID), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("발급 응답은 캐시되지 않도록 헤더를 붙인다")
    void should_set_no_store_headers_on_issue() {
        when(service.issue(eq(MEMBER_ID), eq(1L), any()))
                .thenReturn(new IssueResponse(1L, "raw-token", Instant.parse("2026-09-05T00:00:00Z")));

        assertThat(mvc.post()
                        .uri("/api/v1/plans/1/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":2,\"purpose\":\"소득 확인\"}"))
                .hasStatusOk()
                .hasHeader("Cache-Control", "no-store")
                .hasHeader("Referrer-Policy", "no-referrer");
    }

    @Test
    @DisplayName("목적이 비어 있으면 400 이다")
    void should_reject_blank_purpose() {
        assertThat(mvc.post()
                        .uri("/api/v1/plans/1/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":2,\"purpose\":\"\"}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("남의 계획이면 403 이다")
    void should_return_forbidden_for_other_plan() {
        when(service.overview(eq(MEMBER_ID), eq(9L))).thenThrow(new BusinessException(ErrorCode.PLAN_ACCESS_DENIED));

        assertThat(mvc.get().uri("/api/v1/plans/9/consents")).hasStatus(403);
    }

    @Test
    @DisplayName("만료된 링크는 재발급이 필요함을 알린다")
    void should_report_expired_token() {
        when(service.view(anyString())).thenThrow(new BusinessException(ErrorCode.CONSENT_TOKEN_EXPIRED));

        assertThat(mvc.get().uri("/api/v1/consents/some-token"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("TERMS_002");
    }

    @Test
    @DisplayName("없거나 폐기된 링크는 같은 코드로 응답한다")
    void should_not_leak_token_existence() {
        when(service.view(anyString())).thenThrow(new BusinessException(ErrorCode.CONSENT_TOKEN_INVALID));

        assertThat(mvc.get().uri("/api/v1/consents/some-token"))
                .hasStatus(401)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("TERMS_001");
    }

    @Test
    @DisplayName("가구원 화면에는 관계와 목적만 담는다")
    void should_expose_relation_and_purpose_only() {
        when(service.view(anyString()))
                .thenReturn(new ConsentView("부", "소득 확인", Instant.parse("2026-09-05T00:00:00Z")));

        assertThat(mvc.get().uri("/api/v1/consents/some-token"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.relation")
                .isEqualTo("부");
    }

    @Test
    @DisplayName("이미 응답한 링크로 다시 제출하면 막힌다")
    void should_reject_reused_token_on_response() {
        doThrow(new BusinessException(ErrorCode.CONSENT_TOKEN_INVALID))
                .when(service)
                .respond(anyString(), anyBoolean());

        assertThat(mvc.post()
                        .uri("/api/v1/consents/some-token/response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true}"))
                .hasStatus(401);
    }

    @Test
    @DisplayName("확인이 남으면 allVerified 가 false 로 온다")
    void should_report_pending_verification() {
        when(service.overview(eq(MEMBER_ID), eq(1L)))
                .thenReturn(new ConsentOverview(
                        false, List.of(new MemberConsentStatus(2L, "부", true, false, ConsentStatus.PENDING))));

        assertThat(mvc.get().uri("/api/v1/plans/1/consents"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.allVerified")
                .isEqualTo(false);
    }
}
