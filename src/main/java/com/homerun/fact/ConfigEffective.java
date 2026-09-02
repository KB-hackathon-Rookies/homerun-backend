package com.homerun.fact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 팩트 레지스트리 한 행. 매년 바뀌는 기준 수치의 단일 출처다. */
@Entity
@Table(name = "config_effective")
public class ConfigEffective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fact_code", nullable = false)
    private String factCode;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String item;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_num")
    private BigDecimal valueNum;

    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confidence confidence;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    protected ConfigEffective() {}

    public String factCode() {
        return factCode;
    }

    public String item() {
        return item;
    }

    public String valueText() {
        return valueText;
    }

    public BigDecimal valueNum() {
        return valueNum;
    }

    public String unit() {
        return unit;
    }

    public Confidence confidence() {
        return confidence;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public LocalDate effectiveFrom() {
        return effectiveFrom;
    }
}
