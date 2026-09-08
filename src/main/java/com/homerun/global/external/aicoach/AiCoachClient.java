package com.homerun.global.external.aicoach;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.homerun.domain.coach.dto.request.CoachAskRequest;
import com.homerun.domain.coach.dto.response.CoachAskResponse;
import com.homerun.domain.coach.dto.response.CoachAskResponse.CoachSource;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.resilience.ExternalApiRestClientFactory;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * ai-coach(FastAPI) 호출 클라이언트.
 *
 * <p>ai-coach 는 Spring 이 발급한 JWT 를 같은 시크릿으로 직접 검증한다. 그래서 토큰을 새로 만들지
 * 않고 호출자의 {@code Authorization} 헤더를 그대로 넘긴다.
 *
 * <p>업스트림 실패는 전부 전용 {@link ErrorCode} 로 바꾼다. 업스트림 응답 본문은 스택트레이스나
 * 모델 프롬프트가 섞여 나올 수 있으므로 로그에만 남기고 클라이언트에는 내보내지 않는다.
 */
@Component
public class AiCoachClient {

    private static final Logger log = LoggerFactory.getLogger(AiCoachClient.class);
    private static final String ASK_PATH = "/coach/ask";

    private final RestClient restClient;

    @Autowired
    public AiCoachClient(AiCoachProperties properties, ExternalApiRestClientFactory restClientFactory) {
        this(restClientFactory.create(properties.baseUrl(), properties.readTimeout()));
    }

    AiCoachClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 질문을 ai-coach 로 넘기고 근거가 붙은 답변을 받아 온다.
     *
     * @param authorization 호출자가 보낸 {@code Authorization} 헤더 원문
     */
    public CoachAskResponse ask(CoachAskRequest request, String authorization) {
        AskPayload payload = new AskPayload(request.question(), request.stage().name(), request.context());
        try {
            // 재시도하지 않는다. LLM 호출은 비싸고 느려서 한 번 더 던지면 사용자 대기시간이 두 배가 된다.
            AskResult result = restClient
                    .post()
                    .uri(ASK_PATH)
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(AskResult.class);

            if (result == null || result.answer() == null || result.answer().isBlank()) {
                log.warn("AI 코치가 빈 응답을 반환했습니다.");
                throw new BusinessException(ErrorCode.AI_COACH_UPSTREAM_ERROR);
            }
            return toResponse(result, request.stage());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            // 본문에 업스트림 내부 정보가 담길 수 있어 로그에만 남긴다.
            log.warn("AI 코치가 HTTP {} 를 반환했습니다.", exception.getStatusCode().value());
            throw new BusinessException(ErrorCode.AI_COACH_UPSTREAM_ERROR);
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                log.warn("AI 코치 응답이 제한시간을 넘겼습니다.", exception);
                throw new BusinessException(ErrorCode.AI_COACH_TIMEOUT);
            }
            log.warn("AI 코치에 연결하지 못했습니다.", exception);
            throw new BusinessException(ErrorCode.AI_COACH_UNAVAILABLE);
        } catch (RuntimeException exception) {
            log.warn("AI 코치 호출에 실패했습니다.", exception);
            throw new BusinessException(ErrorCode.AI_COACH_UPSTREAM_ERROR);
        }
    }

    private CoachAskResponse toResponse(AskResult result, PlanStage requestedStage) {
        PlanStage stage = parseStage(result.stage(), requestedStage);
        List<CoachSource> sources = result.sources() == null
                ? List.of()
                : result.sources().stream()
                        .map(source ->
                                new CoachSource(source.title(), source.source(), source.sourceUrl(), source.snippet()))
                        .toList();
        return new CoachAskResponse(result.answer(), stage, sources);
    }

    /** 업스트림이 모르는 단계 값을 돌려줘도 요청 단계로 되돌린다. 답변 자체는 쓸 수 있기 때문이다. */
    private PlanStage parseStage(String stage, PlanStage fallback) {
        if (stage == null) {
            return fallback;
        }
        try {
            return PlanStage.valueOf(stage);
        } catch (IllegalArgumentException exception) {
            log.warn("AI 코치가 알 수 없는 단계 값을 반환했습니다.");
            return fallback;
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** ai-coach 요청 본문. 필드 이름이 계약이라 도메인 DTO 와 분리해 둔다. */
    private record AskPayload(String question, String stage, Map<String, Object> context) {}

    /** ai-coach 응답 본문. snake_case 라 도메인 DTO 로 그대로 쓸 수 없다. */
    private record AskResult(String answer, String stage, List<SourceResult> sources) {}

    private record SourceResult(
            String title,
            String source,
            @JsonProperty("source_url") String sourceUrl,
            String snippet) {}
}
