package com.homerun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.application.ApplicationDtos.ApplicationView;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ApplicationControllerTest {

    private final ApplicationService service = mock(ApplicationService.class);

    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new ApplicationController(service)),
            builder -> builder.setControllerAdvice(new ApplicationExceptionHandler())
                    .build());

    private static ApplicationView view(ApplicationStatus status, RejectStage stage) {
        return new ApplicationView(
                1L, 1L, 2L, "복지로", status, null, null, stage, null, null, null, RejectGuidance.forStage(stage));
    }

    @Test
    @DisplayName("신청 건 생성은 201 이다")
    void should_return_created() {
        when(service.create(any())).thenReturn(view(ApplicationStatus.PREPARING, null));

        assertThat(mvc.post()
                        .uri("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"policyId\":2,\"channel\":\"복지로\"}"))
                .hasStatus(201);
    }

    @Test
    @DisplayName("중복 신청은 409 다")
    void should_return_conflict_on_duplicate() {
        when(service.create(any())).thenThrow(new DuplicateApplicationException(2L));

        assertThat(mvc.post()
                        .uri("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"policyId\":2}"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("APPLICATION_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("planId 가 없으면 400 이다")
    void should_require_plan_id() {
        assertThat(mvc.post()
                        .uri("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyId\":2}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("거절 단계에 맞는 다음 행동을 함께 준다")
    void should_return_next_action_for_reject_stage() {
        when(service.update(eq(1L), any())).thenReturn(view(ApplicationStatus.REJECTED, RejectStage.GUARANTEE));

        assertThat(mvc.patch()
                        .uri("/api/v1/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"rejectStage\":\"GUARANTEE\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.nextAction")
                .asString()
                .contains("은행을 바꿔도");
    }

    @Test
    @DisplayName("거절인데 단계가 없으면 400 이다")
    void should_reject_without_stage() {
        when(service.update(eq(1L), any())).thenThrow(new IllegalArgumentException("거절은 어느 단계에서 막혔는지를 함께 기록해야 한다"));

        assertThat(mvc.patch()
                        .uri("/api/v1/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .hasStatus(400);
    }

    @Test
    @DisplayName("없는 신청 건은 404 다")
    void should_return_not_found() {
        when(service.update(eq(9L), any())).thenThrow(new ApplicationNotFoundException(9L));

        assertThat(mvc.patch()
                        .uri("/api/v1/applications/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .hasStatus(404);
    }

    @Test
    @DisplayName("계획별 목록을 조회한다")
    void should_list_by_plan() {
        when(service.list(1L))
                .thenReturn(new ApplicationDtos.ApplicationList(List.of(view(ApplicationStatus.PREPARING, null))));

        assertThat(mvc.get().uri("/api/v1/applications?planId=1"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.applications")
                .asList()
                .hasSize(1);
    }
}
