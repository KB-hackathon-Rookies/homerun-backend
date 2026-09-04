package com.homerun.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.verification.controller.VerificationController;
import com.homerun.domain.verification.dto.response.VerificationDtos.PendingCondition;
import com.homerun.domain.verification.dto.response.VerificationDtos.PendingConditionList;
import com.homerun.domain.verification.service.VerificationService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.exception.GlobalExceptionHandler;
import com.homerun.global.security.principal.MemberPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class VerificationControllerTest {

    private static final Long MEMBER_ID = 7L;

    private final VerificationService service = mock(VerificationService.class);

    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new VerificationController(service)),
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
    @DisplayName("미확인 조건 목록을 200으로 돌려준다")
    void should_return_pending_conditions() {
        when(service.listPending(eq(MEMBER_ID), eq(1L)))
                .thenReturn(new PendingConditionList(List.of(new PendingCondition(
                        "POL-001",
                        "청년전용 버팀목전세자금대출",
                        "NET_ASSET_LIMIT",
                        "순자산 기준",
                        "3.45억 이하",
                        "FCT-004",
                        "https://nhuf.molit.go.kr"))));

        assertThat(mvc.get().uri("/api/v1/plans/1/verifications"))
                .hasStatus(200)
                .bodyText()
                .contains("NET_ASSET_LIMIT");
    }

    @Test
    @DisplayName("남의 계획이면 403이다")
    void should_return_forbidden_for_other_plan() {
        when(service.listPending(eq(MEMBER_ID), eq(9L))).thenThrow(new BusinessException(ErrorCode.PLAN_ACCESS_DENIED));

        assertThat(mvc.get().uri("/api/v1/plans/9/verifications")).hasStatus(403);
    }
}
