package com.homerun.domain.education.service;

import com.homerun.domain.education.entity.EducationQuizQuestion;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 퀴즈 채점기(순수 로직). 제출은 해당 모듈의 모든 문항을 정확히 한 번씩 포함해야 하고, 각
 * choiceIndex 는 그 문항 보기 범위 안이어야 한다. 어기면 {@link ErrorCode#EDUCATION_QUIZ_SUBMISSION_INVALID}.
 */
@Component
public class QuizGrader {

    public record SubmittedAnswer(Long questionId, int choiceIndex) {}

    public record GradedAnswer(Long questionId, int choiceIndex, boolean correct, String explanation) {}

    public record QuizResult(int score, int total, List<GradedAnswer> answers) {
        public boolean passed() {
            return total > 0 && score * 100 >= total * 60;
        }
    }

    public QuizResult grade(List<EducationQuizQuestion> questions, List<SubmittedAnswer> answers) {
        Map<Long, EducationQuizQuestion> byId =
                questions.stream().collect(Collectors.toMap(EducationQuizQuestion::getId, Function.identity()));
        if (answers == null || answers.size() != byId.size()) {
            throw new BusinessException(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID);
        }
        // 제출 문항 집합이 모듈 문항 집합과 정확히 일치(중복·누락·타 문항 금지).
        long distinct =
                answers.stream().map(SubmittedAnswer::questionId).distinct().count();
        if (distinct != byId.size()) {
            throw new BusinessException(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID);
        }

        List<GradedAnswer> graded =
                answers.stream().map(answer -> gradeOne(byId, answer)).toList();
        int score = (int) graded.stream().filter(GradedAnswer::correct).count();
        return new QuizResult(score, byId.size(), graded);
    }

    private GradedAnswer gradeOne(Map<Long, EducationQuizQuestion> byId, SubmittedAnswer answer) {
        EducationQuizQuestion question = byId.get(answer.questionId());
        if (question == null
                || answer.choiceIndex() < 0
                || answer.choiceIndex() >= question.getOptions().size()) {
            throw new BusinessException(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID);
        }
        boolean correct = question.getAnswerIndex() == answer.choiceIndex();
        return new GradedAnswer(question.getId(), answer.choiceIndex(), correct, question.getExplanation());
    }
}
