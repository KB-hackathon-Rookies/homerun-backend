package com.homerun.global.external.aicoach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.homerun.domain.coach.dto.request.CoachAskRequest;
import com.homerun.domain.coach.dto.response.CoachAskResponse;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ai-coach 호출 클라이언트 테스트.
 *
 * <p>ai-coach 프로세스를 띄우지 않고 HTTP 경계만 흉내 낸다. 여기서 지키려는 것은 계약(경로·헤더·
 * 필드 이름)과 실패를 전용 에러 코드로 바꾸는 규칙이다.
 */
class AiCoachClientTest {

    private static final String BASE_URL = "http://ai-coach.test:8000";
    private static final String ASK_URL = BASE_URL + "/coach/ask";
    private static final String BEARER = "Bearer caller-issued-token";

    private static final CoachAskRequest REQUEST =
            new CoachAskRequest("등기부등본은 언제 확인하나요?", PlanStage.FIRST, Map.of("budget", 30000));

    private MockRestServiceServer server;
    private AiCoachClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiCoachClient(builder.build());
    }

    @Test
    @DisplayName("계약대로 POST /coach/ask 에 질문과 단계를 보낸다")
    void should_post_question_to_contract_path() {
        server.expect(requestTo(ASK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.question").value("등기부등본은 언제 확인하나요?"))
                .andExpect(jsonPath("$.stage").value("FIRST"))
                .andExpect(jsonPath("$.context.budget").value(30000))
                .andRespond(withSuccess("""
                        {"answer":"계약 직전에 확인합니다.","stage":"FIRST","sources":[]}
                        """, MediaType.APPLICATION_JSON));

        CoachAskResponse response = client.ask(REQUEST, BEARER);

        assertThat(response.answer()).isEqualTo("계약 직전에 확인합니다.");
        server.verify();
    }

    @Test
    @DisplayName("호출자의 Authorization 헤더를 그대로 싣는다")
    void should_forward_authorization_header_verbatim() {
        server.expect(requestTo(ASK_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, BEARER))
                .andRespond(withSuccess("""
                        {"answer":"답변","stage":"FIRST","sources":[]}
                        """, MediaType.APPLICATION_JSON));

        client.ask(REQUEST, BEARER);

        server.verify();
    }

    @Test
    @DisplayName("응답의 source_url 을 sourceUrl 로 읽는다")
    void should_map_snake_case_source_url() {
        server.expect(requestTo(ASK_URL)).andRespond(withSuccess("""
                        {"answer":"답변","stage":"SECOND","sources":[
                          {"title":"제목","source":"국토교통부","source_url":"https://example.test/a","snippet":"본문"},
                          {"title":"제목2","source":"출처2","source_url":null,"snippet":"본문2"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        CoachAskResponse response = client.ask(REQUEST, BEARER);

        assertThat(response.stage()).isEqualTo(PlanStage.SECOND);
        assertThat(response.sources()).hasSize(2);
        assertThat(response.sources().get(0).sourceUrl()).isEqualTo("https://example.test/a");
        assertThat(response.sources().get(1).sourceUrl()).isNull();
    }

    @Test
    @DisplayName("ai-coach 에 닿지 못하면 COACH_001 이다")
    void should_map_connection_failure_to_unavailable() {
        server.expect(requestTo(ASK_URL)).andRespond(request -> {
            throw new java.net.ConnectException("Connection refused");
        });

        assertThatThrownBy(() -> client.ask(REQUEST, BEARER))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_COACH_UNAVAILABLE);
    }

    @Test
    @DisplayName("응답이 제한시간을 넘기면 COACH_002 다")
    void should_map_read_timeout_to_timeout() {
        server.expect(requestTo(ASK_URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> client.ask(REQUEST, BEARER))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_COACH_TIMEOUT);
    }

    @Test
    @DisplayName("업스트림 5xx 는 COACH_003 이고 원문은 예외 메시지에 담기지 않는다")
    void should_map_server_error_without_leaking_body() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withServerError()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"OPENAI_API_KEY 가 유효하지 않습니다\"}"));

        assertThatThrownBy(() -> client.ask(REQUEST, BEARER))
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("OPENAI_API_KEY")
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_COACH_UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("업스트림 4xx 도 COACH_003 이다. 인증 실패를 사용자 401 로 흘리지 않는다")
    void should_map_client_error_to_upstream_error() {
        server.expect(requestTo(ASK_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"유효하지 않은 토큰입니다.\"}"));

        assertThatThrownBy(() -> client.ask(REQUEST, BEARER))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_COACH_UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("답변이 비어 있으면 COACH_003 이다")
    void should_map_blank_answer_to_upstream_error() {
        server.expect(requestTo(ASK_URL)).andRespond(withSuccess("""
                        {"answer":"   ","stage":"FIRST","sources":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.ask(REQUEST, BEARER))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.AI_COACH_UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("모르는 단계 값이 오면 요청한 단계로 되돌린다")
    void should_fall_back_to_requested_stage() {
        server.expect(requestTo(ASK_URL)).andRespond(withSuccess("""
                        {"answer":"답변","stage":"DUGOUT","sources":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.ask(REQUEST, BEARER).stage()).isEqualTo(PlanStage.FIRST);
    }
}
