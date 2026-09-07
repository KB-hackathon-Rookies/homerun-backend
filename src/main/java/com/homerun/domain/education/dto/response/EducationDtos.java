package com.homerun.domain.education.dto.response;

import java.util.List;

/** 교육 모듈 응답 DTO 모음. 퀴즈 정답(answer_index)은 어디에도 노출하지 않는다. */
public final class EducationDtos {

    private EducationDtos() {}

    /** 목록 카드 한 건. */
    public record ModuleSummary(String code, String title, Short estimatedMinutes, String status, Integer quizScore) {}

    /** 모듈 상세: 본문 + 퀴즈 문항(정답 제외). */
    public record ModuleDetail(
            String code, String title, String body, String status, int progressPct, List<QuizQuestion> quiz) {}

    /** 퀴즈 문항(정답 없음). */
    public record QuizQuestion(Long id, String question, List<String> options, int sortOrder) {}

    /** 채점 결과. */
    public record QuizSubmitResult(int score, int total, boolean passed, String status, List<GradedAnswer> answers) {}

    public record GradedAnswer(Long questionId, int choiceIndex, boolean correct, String explanation) {}
}
