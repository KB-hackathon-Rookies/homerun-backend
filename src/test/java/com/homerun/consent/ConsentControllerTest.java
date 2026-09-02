package com.homerun.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.consent.ConsentDtos.ConsentOverview;
import com.homerun.consent.ConsentDtos.ConsentView;
import com.homerun.consent.ConsentDtos.IssueResponse;
import com.homerun.consent.ConsentDtos.MemberConsentStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ConsentControllerTest {

    private final ConsentService service = mock(ConsentService.class);

    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new ConsentController(service)),
            builder ->
                    builder.setControllerAdvice(new ConsentExceptionHandler()).build());

    @Test
    @DisplayName("발급 응답은 캐시되지 않도록 헤더를 붙인다")
    void should_set_no_store_headers_on_issue() {
        when(service.issue(any()))
                .thenReturn(new IssueResponse(1L, "raw-token", Instant.parse("2026-09-05T00:00:00Z")));

        assertThat(mvc.post()
                        .uri("/api/v1/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"memberId\":2,\"purpose\":\"소득 확인\"}"))
                .hasStatusOk()
                .hasHeader("Cache-Control", "no-store")
                .hasHeader("Referrer-Policy", "no-referrer");
    }

    @Test
    @DisplayName("목적이 비어 있으면 400 이다")
    void should_reject_blank_purpose() {
        assertThat(mvc.post()
                        .uri("/api/v1/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"memberId\":2,\"purpose\":\"\"}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("만료된 링크는 410 으로 재발급이 필요함을 알린다")
    void should_return_gone_for_expired_token() {
        when(service.view(anyString())).thenThrow(ConsentTokenException.expired());

        assertThat(mvc.get().uri("/api/v1/consents/some-token"))
                .hasStatus(410)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("CONSENT_TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("없거나 폐기된 링크는 404 로 같게 응답한다")
    void should_return_not_found_for_invalid_token() {
        when(service.view(anyString())).thenThrow(ConsentTokenException.invalid());

        assertThat(mvc.get().uri("/api/v1/consents/some-token"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("CONSENT_TOKEN_INVALID");
    }

    @Test
    @DisplayName("가구원 화면에는 관계와 목적만 담는다")
    void should_expose_relation_and_purpose_only() {
        when(service.view(anyString()))
                .thenReturn(new ConsentView("부", "소득 확인", Instant.parse("2026-09-05T00:00:00Z")));

        assertThat(mvc.get().uri("/api/v1/consents/some-token"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.relation")
                .isEqualTo("부");
    }

    @Test
    @DisplayName("응답 제출은 본문 없이 204 다")
    void should_return_no_content_on_response() {
        assertThat(mvc.post()
                        .uri("/api/v1/consents/some-token/response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true}"))
                .hasStatus(204);
    }

    @Test
    @DisplayName("이미 응답한 링크로 다시 제출하면 404 다")
    void should_reject_reused_token_on_response() {
        doThrow(ConsentTokenException.invalid()).when(service).respond(anyString(), anyBoolean());

        assertThat(mvc.post()
                        .uri("/api/v1/consents/some-token/response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true}"))
                .hasStatus(404);
    }

    @Test
    @DisplayName("확인이 남으면 allVerified 가 false 로 온다")
    void should_report_pending_verification() {
        when(service.overview(1L))
                .thenReturn(new ConsentOverview(
                        false, List.of(new MemberConsentStatus(2L, "부", true, false, ConsentStatus.PENDING))));

        assertThat(mvc.get().uri("/api/v1/consents?planId=1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.allVerified")
                .isEqualTo(false);
    }
}
