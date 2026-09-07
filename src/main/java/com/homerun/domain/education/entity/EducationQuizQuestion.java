package com.homerun.domain.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 교육 모듈 퀴즈 문항. answer_index 는 서버 전용(응답 DTO 에 넣지 않는다). */
@Entity
@Table(name = "education_quiz_question")
public class EducationQuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(nullable = false)
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> options;

    @Column(name = "answer_index", nullable = false)
    private int answerIndex;

    @Column
    private String explanation;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected EducationQuizQuestion() {}

    /** 테스트·재구성용 팩토리. */
    public static EducationQuizQuestion of(
            Long id,
            Long contentId,
            String question,
            List<String> options,
            int answerIndex,
            String explanation,
            int sortOrder) {
        EducationQuizQuestion q = new EducationQuizQuestion();
        q.id = id;
        q.contentId = contentId;
        q.question = question;
        q.options = options;
        q.answerIndex = answerIndex;
        q.explanation = explanation;
        q.sortOrder = sortOrder;
        return q;
    }

    public Long getId() {
        return id;
    }

    public Long getContentId() {
        return contentId;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getAnswerIndex() {
        return answerIndex;
    }

    public String getExplanation() {
        return explanation;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
