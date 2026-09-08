package com.homerun.domain.coach.dto.response;

import com.homerun.domain.plan.type.PlanStage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** AI 코치 답변. ai-coach 의 응답 본문을 그대로 옮긴 모양이다. */
public record CoachAskResponse(
        @Schema(description = "코치 답변") String answer,
        @Schema(description = "답변이 기준으로 삼은 단계") PlanStage stage,

        @Schema(description = "ANSWER, CLARIFICATION, NO_EVIDENCE")
        String responseType,

        @Schema(description = "한 줄 결론") String summary,
        @Schema(description = "판단 근거") List<String> reasons,
        @Schema(description = "다음 행동") List<String> nextActions,
        @Schema(description = "주의사항") List<String> warnings,
        @Schema(description = "추가로 필요한 질문") String followUpQuestion,
        @Schema(description = "단기 대화 식별자") String conversationId,
        @Schema(description = "답변 근거 문서") List<CoachSource> sources) {

    public CoachAskResponse(String answer, PlanStage stage, List<CoachSource> sources) {
        this(answer, stage, "ANSWER", answer, List.of(), List.of(), List.of(), null, "main", sources);
    }

    /** 근거 출처. LLM 생성물이 아니라 retriever 가 돌려준 문서 메타데이터다. */
    public record CoachSource(String title, String source, String sourceUrl, String snippet) {}
}
