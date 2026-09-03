package com.homerun.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.document.controller.DocumentController;
import com.homerun.domain.document.dto.DocumentDtos.DocumentIssueGuide;
import com.homerun.domain.document.dto.DocumentDtos.DocumentList;
import com.homerun.domain.document.dto.DocumentDtos.DocumentSummary;
import com.homerun.domain.document.dto.DocumentDtos.IssueMethodView;
import com.homerun.domain.document.dto.VisitPlanDtos.VisitPlan;
import com.homerun.domain.document.service.DocumentIssueGuideService;
import com.homerun.domain.document.service.VisitPlanner;
import com.homerun.domain.document.type.IssueMethod;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * HTTP 경계만 본다. 판정은 서비스 테스트가 맡는다.
 *
 * <p>{@code /visit-plan} 이 {@code /{code}} 라우팅에 먹히지 않는지, {@code preferOnline} 이
 * JSON 에서 제대로 붙는지, 잘못된 입력이 400 으로 떨어지는지는 여기서만 잡힌다.
 */
class DocumentControllerTest {

    private final DocumentIssueGuideService guideService = mock(DocumentIssueGuideService.class);
    private final VisitPlanner planner = mock(VisitPlanner.class);

    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new DocumentController(guideService, planner)),
            builder -> builder.setControllerAdvice(new GlobalExceptionHandler()).build());

    private static VisitPlan emptyPlan() {
        return new VisitPlan(List.of(), List.of(), List.of(), List.of(), 0);
    }

    @Test
    @DisplayName("서류 목록은 200 이고 래퍼 안에 담긴다")
    void should_return_document_list() {
        when(guideService.list())
                .thenReturn(new DocumentList(
                        List.of(new DocumentSummary("REGISTRY_CERT", "등기사항전부증명서", "인터넷등기소", true, 1000, 30))));

        assertThat(mvc.get().uri("/api/v1/documents"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.documents[0].code")
                .isEqualTo("REGISTRY_CERT");
    }

    @Test
    @DisplayName("서류 상세는 발급방법을 함께 준다")
    void should_return_issue_guide() {
        when(guideService.guide("REGISTRY_CERT"))
                .thenReturn(new DocumentIssueGuide(
                        "REGISTRY_CERT",
                        "등기사항전부증명서",
                        "인터넷등기소",
                        30,
                        null,
                        null,
                        false,
                        List.of(new IssueMethodView(
                                IssueMethod.ONLINE,
                                "온라인",
                                "인터넷등기소",
                                "https://www.iros.go.kr",
                                1000,
                                null,
                                null,
                                null,
                                true))));

        assertThat(mvc.get().uri("/api/v1/documents/REGISTRY_CERT"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.methods[0].method")
                .isEqualTo("ONLINE");
    }

    @Test
    @DisplayName("없는 서류는 404 다")
    void should_return_not_found() {
        when(guideService.guide("NOPE")).thenThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        assertThat(mvc.get().uri("/api/v1/documents/NOPE")).hasStatus(404);
    }

    @Test
    @DisplayName("visit-plan 이 서류 상세 라우팅에 먹히지 않는다")
    void should_route_visit_plan_before_code_path() {
        when(planner.plan(anyList(), eq(true))).thenReturn(emptyPlan());

        assertThat(mvc.post()
                        .uri("/api/v1/documents/visit-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentCodes\":[\"RESIDENT_LIST\"]}"))
                .hasStatusOk();
    }

    @Test
    @DisplayName("preferOnline 을 안 보내면 온라인 우선으로 본다")
    void should_default_prefer_online_to_true() {
        when(planner.plan(anyList(), eq(true))).thenReturn(emptyPlan());

        assertThat(mvc.post()
                        .uri("/api/v1/documents/visit-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentCodes\":[\"RESIDENT_LIST\"]}"))
                .hasStatusOk();

        verify(planner).plan(List.of("RESIDENT_LIST"), true);
    }

    @Test
    @DisplayName("preferOnline false 가 그대로 전달된다")
    void should_pass_prefer_online_false() {
        when(planner.plan(anyList(), eq(false))).thenReturn(emptyPlan());

        assertThat(mvc.post()
                        .uri("/api/v1/documents/visit-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentCodes\":[\"RESIDENT_LIST\"],\"preferOnline\":false}"))
                .hasStatusOk();

        verify(planner).plan(List.of("RESIDENT_LIST"), false);
    }

    @Test
    @DisplayName("빈 목록은 400 이다")
    void should_reject_empty_document_list() {
        assertThat(mvc.post()
                        .uri("/api/v1/documents/visit-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentCodes\":[]}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("빈 문자열 코드는 400 이다")
    void should_reject_blank_code() {
        assertThat(mvc.post()
                        .uri("/api/v1/documents/visit-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentCodes\":[\"  \"]}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("서류를 너무 많이 넣으면 400 이다")
    void should_reject_too_many_documents() {
        String codes = java.util.stream.IntStream.rangeClosed(0, 40)
                .mapToObj(index -> "\"CODE_" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        assertThat(mvc.post()
                        .uri("/api/v1/documents/visit-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentCodes\":[" + codes + "]}"))
                .hasStatus(400);
    }
}
