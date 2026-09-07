package com.homerun.domain.education.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/** 퀴즈 제출. 해당 모듈의 모든 문항을 정확히 한 번씩 포함해야 한다(서버가 검증). */
public record QuizSubmitRequest(@NotEmpty @Valid List<AnswerItem> answers) {

    public record AnswerItem(
            @NotNull Long questionId,
            @NotNull @PositiveOrZero Integer choiceIndex) {}
}
