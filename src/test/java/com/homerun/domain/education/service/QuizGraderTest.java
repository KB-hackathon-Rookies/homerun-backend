package com.homerun.domain.education.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.domain.education.entity.EducationQuizQuestion;
import com.homerun.domain.education.service.QuizGrader.SubmittedAnswer;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizGraderTest {

    private final QuizGrader grader = new QuizGrader();

    private EducationQuizQuestion q(long id, int answer) {
        return EducationQuizQuestion.of(id, 1L, "q" + id, List.of("a", "b", "c"), answer, "why" + id, (int) id);
    }

    @Test
    @DisplayName("정답 수와 통과 여부를 계산한다(60% 임계값)")
    void grades_and_marks_pass() {
        var questions = List.of(q(1, 0), q(2, 1), q(3, 2));
        var result = grader.grade(
                questions, List.of(new SubmittedAnswer(1L, 0), new SubmittedAnswer(2L, 1), new SubmittedAnswer(3L, 0)));

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isTrue(); // 2/3 = 66% >= 60
        assertThat(result.answers())
                .extracting(QuizGrader.GradedAnswer::correct)
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("정답률이 60% 미만이면 통과가 아니다")
    void fails_below_threshold() {
        var questions = List.of(q(1, 0), q(2, 1), q(3, 2));
        var result = grader.grade(
                questions, List.of(new SubmittedAnswer(1L, 0), new SubmittedAnswer(2L, 0), new SubmittedAnswer(3L, 0)));
        assertThat(result.score()).isEqualTo(1); // 1/3 = 33%
        assertThat(result.passed()).isFalse();
    }

    @Test
    @DisplayName("문항 누락 제출은 거부한다")
    void rejects_incomplete_submission() {
        var questions = List.of(q(1, 0), q(2, 1));
        assertThatThrownBy(() -> grader.grade(questions, List.of(new SubmittedAnswer(1L, 0))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID));
    }

    @Test
    @DisplayName("같은 문항을 중복 제출하면 거부한다")
    void rejects_duplicate_submission() {
        var questions = List.of(q(1, 0), q(2, 1));
        assertThatThrownBy(
                        () -> grader.grade(questions, List.of(new SubmittedAnswer(1L, 0), new SubmittedAnswer(1L, 1))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID));
    }

    @Test
    @DisplayName("보기 범위를 벗어난 choiceIndex 는 거부한다")
    void rejects_out_of_range_choice() {
        var questions = List.of(q(1, 0));
        assertThatThrownBy(() -> grader.grade(questions, List.of(new SubmittedAnswer(1L, 5))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.EDUCATION_QUIZ_SUBMISSION_INVALID));
    }
}
