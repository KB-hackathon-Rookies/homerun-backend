package com.homerun.domain.terms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "terms")
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "content_url")
    private String contentUrl;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected Term() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public boolean isRequired() {
        return required;
    }

    public String getContentUrl() {
        return contentUrl;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }
}
