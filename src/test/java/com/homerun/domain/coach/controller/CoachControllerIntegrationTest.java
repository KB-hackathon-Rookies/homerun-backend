package com.homerun.domain.coach.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.coach.dto.request.CoachAskRequest;
import com.homerun.domain.coach.dto.response.CoachAskResponse;
import com.homerun.domain.coach.dto.response.CoachAskResponse.CoachSource;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.terms.service.TermsService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.aicoach.AiCoachClient;
import com.homerun.global.security.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AI 코치 프록시 엔드포인트 테스트.
 *
 * <p>ai-coach 프로세스는 띄우지 않는다. 이 테스트가 지키려는 것은 인증·검증·오류 매핑이지
 * 업스트림의 답변 품질이 아니다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=coach-integration-test-secret-over-32-bytes",
            "app.jwt.access-token-expiration-seconds=3600"
        })
class CoachControllerIntegrationTest {

    private static final String ASK_URI = "/api/v1/coach/ask";
    private static final String ASK_BODY = """
            {"question":"전세 계약할 때 등기부등본은 언제 확인하나요?","stage":"FIRST"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    MemberRepository members;

    @Autowired
    JwtTokenProvider tokens;

    @MockitoBean
    TermsService terms;

    @MockitoBean
    AiCoachClient aiCoachClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        when(terms.hasAgreedAllRequired(anyLong())).thenReturn(true);
        Member member = members.save(Member.create(AuthProvider.KAKAO, "coach-" + System.nanoTime(), null, "tester"));
        bearer = "Bearer " + tokens.createAccessToken(member);
    }

    @Test
    @DisplayName("토큰 없이 호출하면 401 이다")
    void should_reject_request_without_token() throws Exception {
        mvc.perform(post(ASK_URI).contentType(MediaType.APPLICATION_JSON).content(ASK_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_TOKEN_REQUIRED.code()));
    }

    @Test
    @DisplayName("로그인한 회원은 근거가 붙은 답변을 받는다")
    void should_return_answer_with_sources() throws Exception {
        when(aiCoachClient.ask(any(), any()))
                .thenReturn(new CoachAskResponse(
                        "계약 직전과 잔금일에 각각 확인합니다.",
                        PlanStage.FIRST,
                        List.of(new CoachSource(
                                "전세 계약 체크리스트", "국토교통부", "https://example.test/guide", "등기부등본은 계약 직전에 다시 뗀다."))));

        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("계약 직전과 잔금일에 각각 확인합니다."))
                .andExpect(jsonPath("$.data.stage").value("FIRST"))
                .andExpect(jsonPath("$.data.sources[0].title").value("전세 계약 체크리스트"))
                .andExpect(jsonPath("$.data.sources[0].sourceUrl").value("https://example.test/guide"));
    }

    @Test
    @DisplayName("호출자의 Authorization 헤더를 그대로 ai-coach 로 넘긴다")
    void should_forward_caller_authorization_header() throws Exception {
        when(aiCoachClient.ask(any(), any())).thenReturn(new CoachAskResponse("답변", PlanStage.FIRST, List.of()));

        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASK_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<String> authorization = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CoachAskRequest> request = ArgumentCaptor.forClass(CoachAskRequest.class);
        verify(aiCoachClient).ask(request.capture(), authorization.capture());
        // 새 토큰을 만들지 않는다. ai-coach 가 같은 시크릿으로 이 토큰을 직접 검증한다.
        assertThat(authorization.getValue()).isEqualTo(bearer);
        assertThat(request.getValue().stage()).isEqualTo(PlanStage.FIRST);
    }

    @Test
    @DisplayName("ai-coach 가 죽어 있으면 503 COACH_001 로 내려간다")
    void should_map_upstream_down_to_service_unavailable() throws Exception {
        when(aiCoachClient.ask(any(), any())).thenThrow(new BusinessException(ErrorCode.AI_COACH_UNAVAILABLE));

        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASK_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COACH_001"));
    }

    @Test
    @DisplayName("ai-coach 응답이 늦으면 504 COACH_002 로 내려간다")
    void should_map_upstream_timeout_to_gateway_timeout() throws Exception {
        when(aiCoachClient.ask(any(), any())).thenThrow(new BusinessException(ErrorCode.AI_COACH_TIMEOUT));

        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASK_BODY))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("COACH_002"));
    }

    @Test
    @DisplayName("ai-coach 가 오류를 반환하면 502 COACH_003 이다")
    void should_map_upstream_error_to_bad_gateway() throws Exception {
        when(aiCoachClient.ask(any(), any())).thenThrow(new BusinessException(ErrorCode.AI_COACH_UPSTREAM_ERROR));

        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ASK_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COACH_003"))
                // 메시지는 우리가 정한 문구 그대로다. 업스트림 문구가 섞이지 않는다.
                .andExpect(jsonPath("$.message").value(ErrorCode.AI_COACH_UPSTREAM_ERROR.message()));
    }

    @Test
    @DisplayName("질문이 비어 있으면 400 이고 ai-coach 를 부르지 않는다")
    void should_reject_blank_question() throws Exception {
        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"   ","stage":"FIRST"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.code()));

        verify(aiCoachClient, never()).ask(any(), any());
    }

    @Test
    @DisplayName("질문 길이 상한을 넘으면 400 이고 ai-coach 를 부르지 않는다")
    void should_reject_oversized_question() throws Exception {
        String tooLong = "가".repeat(CoachAskRequest.MAX_QUESTION_LENGTH + 1);

        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + tooLong + "\",\"stage\":\"FIRST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.code()))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("question"));

        verify(aiCoachClient, never()).ask(any(), any());
    }

    @Test
    @DisplayName("모르는 단계 값은 400 으로 막는다")
    void should_reject_unknown_stage() throws Exception {
        mvc.perform(post(ASK_URI)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"질문","stage":"DUGOUT"}
                                """))
                .andExpect(status().isBadRequest());

        verify(aiCoachClient, never()).ask(any(), any());
    }
}
