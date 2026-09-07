package com.homerun.domain.education.service;

import com.homerun.domain.education.dto.request.QuizSubmitRequest;
import com.homerun.domain.education.dto.response.EducationDtos.GradedAnswer;
import com.homerun.domain.education.dto.response.EducationDtos.ModuleDetail;
import com.homerun.domain.education.dto.response.EducationDtos.ModuleSummary;
import com.homerun.domain.education.dto.response.EducationDtos.QuizQuestion;
import com.homerun.domain.education.dto.response.EducationDtos.QuizSubmitResult;
import com.homerun.domain.education.entity.EducationContent;
import com.homerun.domain.education.entity.EducationProgress;
import com.homerun.domain.education.entity.EducationQuizQuestion;
import com.homerun.domain.education.repository.EducationContentRepository;
import com.homerun.domain.education.repository.EducationProgressRepository;
import com.homerun.domain.education.repository.EducationQuizQuestionRepository;
import com.homerun.domain.education.service.QuizGrader.QuizResult;
import com.homerun.domain.education.service.QuizGrader.SubmittedAnswer;
import com.homerun.domain.education.type.EducationProgressStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EducationService {

    private final EducationContentRepository contents;
    private final EducationQuizQuestionRepository questions;
    private final EducationProgressRepository progresses;
    private final QuizGrader quizGrader;

    public EducationService(
            EducationContentRepository contents,
            EducationQuizQuestionRepository questions,
            EducationProgressRepository progresses,
            QuizGrader quizGrader) {
        this.contents = contents;
        this.questions = questions;
        this.progresses = progresses;
        this.quizGrader = quizGrader;
    }

    @Transactional(readOnly = true)
    public List<ModuleSummary> listModules(Long memberId) {
        Map<Long, EducationProgress> byContent = progresses.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(EducationProgress::getContentId, Function.identity(), (a, b) -> a));
        return contents.findByActiveTrueOrderByIdAsc().stream()
                .map(content -> {
                    EducationProgress progress = byContent.get(content.getId());
                    return new ModuleSummary(
                            content.getCode(),
                            content.getTitle(),
                            content.getEstimatedMinutes(),
                            statusName(progress),
                            progress == null ? null : progress.getQuizScore());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ModuleDetail getModule(Long memberId, String code) {
        EducationContent content = activeContent(code);
        EducationProgress progress =
                progresses.findByMemberIdAndContentId(memberId, content.getId()).orElse(null);
        List<QuizQuestion> quiz = questions.findByContentIdOrderBySortOrderAsc(content.getId()).stream()
                .map(q -> new QuizQuestion(q.getId(), q.getQuestion(), q.getOptions(), q.getSortOrder()))
                .toList();
        return new ModuleDetail(
                content.getCode(),
                content.getTitle(),
                content.getBody(),
                statusName(progress),
                progress == null ? 0 : progress.getProgressPct(),
                quiz);
    }

    @Transactional
    public void markRead(Long memberId, String code) {
        EducationContent content = activeContent(code);
        EducationProgress progress = progressFor(memberId, content.getId());
        boolean hasQuiz = questions.existsByContentId(content.getId());
        progress.markRead(hasQuiz);
        progresses.save(progress);
    }

    @Transactional
    public QuizSubmitResult submitQuiz(Long memberId, String code, QuizSubmitRequest request) {
        EducationContent content = activeContent(code);
        List<EducationQuizQuestion> moduleQuestions = questions.findByContentIdOrderBySortOrderAsc(content.getId());
        List<SubmittedAnswer> submitted = request.answers().stream()
                .map(a -> new SubmittedAnswer(a.questionId(), a.choiceIndex()))
                .toList();

        QuizResult result = quizGrader.grade(moduleQuestions, submitted);

        EducationProgress progress = progressFor(memberId, content.getId());
        progress.applyQuiz(result.score(), result.passed());
        progresses.save(progress);

        List<GradedAnswer> answers = result.answers().stream()
                .map(a -> new GradedAnswer(a.questionId(), a.choiceIndex(), a.correct(), a.explanation()))
                .toList();
        return new QuizSubmitResult(
                result.score(),
                result.total(),
                result.passed(),
                progress.getStatus().name(),
                answers);
    }

    private EducationContent activeContent(String code) {
        return contents.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.EDUCATION_CONTENT_NOT_FOUND));
    }

    private EducationProgress progressFor(Long memberId, Long contentId) {
        return progresses
                .findByMemberIdAndContentId(memberId, contentId)
                .orElseGet(() -> EducationProgress.start(memberId, contentId));
    }

    private String statusName(EducationProgress progress) {
        return (progress == null ? EducationProgressStatus.NOT_STARTED : progress.getStatus()).name();
    }
}
