package com.homerun.domain.property.entity;

import com.homerun.domain.property.type.CheckResult;
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
 * 매물 검증 항목 하나의 결과.
 *
 * <p>판정만 남기지 않고 근거({@code factCode})와 공식 출처({@code sourceUrl})를 함께 남긴다.
 * "왜 안 되는지"를 원문으로 보여줄 수 없으면 사용자가 다음 행동을 정할 수 없다.
 */
@Entity
@Table(name = "property_check")
public class PropertyCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "check_code", nullable = false, length = 50)
    private String checkCode;

    @Column(name = "check_label", nullable = false, length = 200)
    private String checkLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckResult result = CheckResult.UNKNOWN;

    @Column(name = "fact_code", length = 20)
    private String factCode;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "checked_at")
    private Instant checkedAt;

    protected PropertyCheck() {}

    public PropertyCheck(
            Long propertyId,
            String checkCode,
            String checkLabel,
            CheckResult result,
            String factCode,
            String sourceUrl,
            Instant checkedAt) {
        this.propertyId = propertyId;
        this.checkCode = checkCode;
        this.checkLabel = checkLabel;
        this.result = result;
        this.factCode = factCode;
        this.sourceUrl = sourceUrl;
        this.checkedAt = checkedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public String getCheckCode() {
        return checkCode;
    }

    public String getCheckLabel() {
        return checkLabel;
    }

    public CheckResult getResult() {
        return result;
    }

    public String getFactCode() {
        return factCode;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
