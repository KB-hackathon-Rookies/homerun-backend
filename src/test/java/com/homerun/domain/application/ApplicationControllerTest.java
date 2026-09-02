package com.homerun.domain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.application.controller.ApplicationController;
import com.homerun.domain.application.dto.ApplicationDtos;
import com.homerun.domain.application.service.ApplicationService;
import com.homerun.domain.application.service.RejectGuidance;
import com.homerun.domain.application.type.ApplicationStatus;
import com.homerun.domain.application.type.RejectStage;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.GlobalExceptionHandler;
import com.homerun.global.security.principal.MemberPrincipal;
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

class ApplicationControllerTest {

    private static final Long MEMBER_ID = 7L;

    private final ApplicationService service = mock(ApplicationService.class);

    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new ApplicationController(service)),
            builder -> builder.setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .build());

    /** @AuthenticationPrincipal 은 SecurityContext 에서 읽는다. 컨텍스트 없이 세우면 null 이 주입된다. */
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

    private static ApplicationDtos.ApplicationView view(ApplicationStatus status, RejectStage stage) {
        return new ApplicationDtos.ApplicationView(
                1L, 1L, 2L, "복지로", status, null, null, stage, null, null, null, RejectGuidance.forStage(stage));
    }

    @Test
    @DisplayName("신청 건 생성은 201 이다")
    void should_return_created() {
        when(service.create(eq(MEMBER_ID), eq(1L), any())).thenReturn(view(ApplicationStatus.PREPARING, null));

        assertThat(mvc.post()
                        .uri("/api/v1/plans/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":2,\"channel\":\"복지로\"}"))
                .hasStatus(201);
    }

    @Test
    @DisplayName("중복 신청은 409 다")
    void should_return_conflict_on_duplicate() {
        when(service.create(eq(MEMBER_ID), eq(1L), any()))
                .thenThrow(new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS));

        assertThat(mvc.post()
                        .uri("/api/v1/plans/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":2}"))
                .hasStatus(409);
    }

    @Test
    @DisplayName("남의 계획이면 403 이다")
    void should_return_forbidden_for_other_plan() {
        when(service.list(eq(MEMBER_ID), eq(9L))).thenThrow(new BusinessException(ErrorCode.PLAN_ACCESS_DENIED));

        assertThat(mvc.get().uri("/api/v1/plans/9/applications")).hasStatus(403);
    }

    @Test
    @DisplayName("policyId 가 없으면 400 이다")
    void should_require_policy_id() {
        assertThat(mvc.post()
                        .uri("/api/v1/plans/1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("거절 단계에 맞는 다음 행동을 함께 준다")
    void should_return_next_action_for_reject_stage() {
        when(service.update(eq(MEMBER_ID), eq(1L), eq(5L), any()))
                .thenReturn(view(ApplicationStatus.REJECTED, RejectStage.GUARANTEE));

        assertThat(mvc.patch()
                        .uri("/api/v1/plans/1/applications/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"rejectStage\":\"GUARANTEE\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.nextAction")
                .asString()
                .contains("은행을 바꿔도");
    }

    @Test
    @DisplayName("거절인데 단계가 없으면 400 이다")
    void should_reject_without_stage() {
        when(service.update(eq(MEMBER_ID), eq(1L), eq(5L), any()))
                .thenThrow(new BusinessException(ErrorCode.REJECT_STAGE_REQUIRED));

        assertThat(mvc.patch()
                        .uri("/api/v1/plans/1/applications/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("없는 신청 건은 404 다")
    void should_return_not_found() {
        when(service.update(eq(MEMBER_ID), eq(1L), eq(9L), any()))
                .thenThrow(new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        assertThat(mvc.patch()
                        .uri("/api/v1/plans/1/applications/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .hasStatus(404);
    }

    @Test
    @DisplayName("계획별 목록을 조회한다")
    void should_list_by_plan() {
        when(service.list(eq(MEMBER_ID), eq(1L)))
                .thenReturn(new ApplicationDtos.ApplicationList(List.of(view(ApplicationStatus.PREPARING, null))));

        assertThat(mvc.get().uri("/api/v1/plans/1/applications"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.applications")
                .asList()
                .hasSize(1);
    }
}
