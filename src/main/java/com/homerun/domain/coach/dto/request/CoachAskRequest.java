package com.homerun.domain.coach.dto.request;

import com.homerun.domain.plan.type.PlanStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * AI 코치 질문.
 *
 * <p>{@code question} 은 그대로 LLM 프롬프트로 들어간다. 길이를 열어두면 토큰 비용과 응답 시간이
 * 사용자 입력에 그대로 끌려가므로 서버에서 먼저 자른다.
 */
public record CoachAskRequest(
        @NotBlank
        @Size(max = CoachAskRequest.MAX_QUESTION_LENGTH)
        @Schema(description = "사용자 질문", example = "전세 계약할 때 등기부등본은 언제 확인하나요?")
        String question,

        @NotNull @Schema(description = "현재 단계", example = "FIRST")
        PlanStage stage,

        @Schema(description = "선택. 프론트가 넘기는 사용자 요약(진단·정책 등)")
        Map<String, Object> context,

        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$")
        @Schema(description = "단기 대화 식별자. 생략하면 main", example = "loan-diagnosis")
        String conversationId) {

    public CoachAskRequest(String question, PlanStage stage, Map<String, Object> context) {
        this(question, stage, context, null);
    }

    /** 질문 길이 상한(자). 실제 사용자 질문은 한두 문장이라 넉넉하면서도 프롬프트 폭주를 막는 값. */
    public static final int MAX_QUESTION_LENGTH = 1000;
}
