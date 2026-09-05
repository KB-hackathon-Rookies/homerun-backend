package com.homerun.domain.document.entity;

import com.homerun.domain.document.type.DocumentHoldingStatus;
import com.homerun.domain.document.type.DocumentPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자가 준비하는 서류 한 건(EVI-01-04).
 *
 * <p>{@code expiresAt} 은 <b>인정 기간을 아는 서류에만</b> 채운다. 모르는 것을 임의 기간으로
 * 채우면 아직 쓸 수 있는 서류를 만료로 보거나 그 반대가 된다(NFR-01-06).
 */
@Entity
@Table(name = "user_document")
public class UserDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentPurpose purpose = DocumentPurpose.GENERAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issue_options", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> issueOptions = Map.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentHoldingStatus status = DocumentHoldingStatus.NEEDED;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "submitted_at")
    private LocalDate submittedAt;

    protected UserDocument() {}

    public UserDocument(Long planId, Long documentTypeId) {
        this(planId, documentTypeId, DocumentPurpose.GENERAL);
    }

    public UserDocument(Long planId, Long documentTypeId, DocumentPurpose purpose) {
        this.planId = planId;
        this.documentTypeId = documentTypeId;
        this.purpose = purpose;
    }

    /**
     * 상태와 발급일을 기록한다.
     *
     * <p>인정 기간을 아는 서류만 만료일을 계산한다. 발급일을 지우면 만료일도 함께 지운다 —
     * 근거 없는 만료일이 남으면 그게 곧 틀린 재발급 안내가 된다.
     */
    public void record(DocumentHoldingStatus status, LocalDate issuedAt, Integer validityDays) {
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = issuedAt == null || validityDays == null ? null : issuedAt.plusDays(validityDays);
        this.submittedAt = status == DocumentHoldingStatus.SUBMITTED ? this.submittedAt : null;
    }

    public void recordIssueOptions(Map<String, String> issueOptions) {
        this.issueOptions = issueOptions == null ? Map.of() : Map.copyOf(issueOptions);
    }

    public void markSubmitted(LocalDate submittedAt) {
        this.submittedAt = submittedAt;
    }

    /**
     * 인정 기간이 지났는가(EVI-01-07).
     *
     * <p>만료일을 모르면 만료로 보지 않는다. 손에 없는 서류도 만료를 따질 일이 없다.
     */
    public boolean expiredOn(LocalDate today) {
        return status.held() && expiresAt != null && today.isAfter(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public Long getDocumentTypeId() {
        return documentTypeId;
    }

    public DocumentPurpose getPurpose() {
        return purpose;
    }

    public Map<String, String> getIssueOptions() {
        return Map.copyOf(issueOptions);
    }

    public DocumentHoldingStatus getStatus() {
        return status;
    }

    public LocalDate getIssuedAt() {
        return issuedAt;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public LocalDate getSubmittedAt() {
        return submittedAt;
    }
}
