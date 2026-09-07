package com.homerun.domain.education.entity;

import com.homerun.domain.education.type.EducationProgressStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원×콘텐츠 교육 진행률(V1 education_progress 재사용). user_id 가 곧 member id 다.
 *
 * <p>콘텐츠를 읽으면 progress_pct = 100 이 되고, 콘텐츠 읽음 + 퀴즈 60% 통과이면 DONE.
 */
@Entity
@Table(name = "education_progress")
public class EducationProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long memberId;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EducationProgressStatus status;

    @Column(name = "progress_pct", nullable = false)
    private short progressPct;

    @Column(name = "quiz_score")
    private Short quizScore;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EducationProgress() {}

    public static EducationProgress start(Long memberId, Long contentId) {
        EducationProgress p = new EducationProgress();
        p.memberId = memberId;
        p.contentId = contentId;
        p.status = EducationProgressStatus.NOT_STARTED;
        p.progressPct = 0;
        p.updatedAt = Instant.now();
        return p;
    }

    /**
     * 콘텐츠 읽음 표시. 퀴즈가 없는 모듈은 읽음만으로 DONE 이 되고, 퀴즈가 있는 모듈은 퀴즈 통과 전까지
     * 최소 IN_PROGRESS 로만 올린다. 이미 DONE 이면 유지한다.
     */
    public void markRead(boolean hasQuiz) {
        this.progressPct = 100;
        if (!hasQuiz) {
            if (status != EducationProgressStatus.DONE) {
                this.completedAt = Instant.now();
            }
            this.status = EducationProgressStatus.DONE;
        } else if (status != EducationProgressStatus.DONE) {
            this.status = EducationProgressStatus.IN_PROGRESS;
        }
        this.updatedAt = Instant.now();
    }

    /** 퀴즈 결과 반영. 콘텐츠를 다 읽었고(100%) 통과했으면 DONE, 아니면 IN_PROGRESS. */
    public void applyQuiz(int score, boolean passed) {
        this.quizScore = (short) score;
        if (progressPct >= 100 && passed) {
            this.status = EducationProgressStatus.DONE;
            this.completedAt = Instant.now();
        } else if (status == EducationProgressStatus.NOT_STARTED) {
            this.status = EducationProgressStatus.IN_PROGRESS;
        }
        this.updatedAt = Instant.now();
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getContentId() {
        return contentId;
    }

    public EducationProgressStatus getStatus() {
        return status;
    }

    public int getProgressPct() {
        return progressPct;
    }

    public Integer getQuizScore() {
        return quizScore == null ? null : quizScore.intValue();
    }
}
