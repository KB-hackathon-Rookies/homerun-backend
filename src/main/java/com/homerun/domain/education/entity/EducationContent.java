package com.homerun.domain.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 교육 모듈 콘텐츠(V1 education_content 재사용). 모듈 1개 = 콘텐츠 1개(단일 본문). */
@Entity
@Table(name = "education_content")
public class EducationContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String source;

    @Column
    private String body;

    @Column(name = "estimated_minutes")
    private Short estimatedMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected EducationContent() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getTopic() {
        return topic;
    }

    public String getSource() {
        return source;
    }

    public String getBody() {
        return body;
    }

    public Short getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public boolean isActive() {
        return active;
    }
}
