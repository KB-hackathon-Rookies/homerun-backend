package com.homerun.domain.document.entity;

import com.homerun.domain.document.type.IssueAgency;
import com.homerun.domain.document.type.IssueMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 서류 하나를 떼는 방법 하나(ISS-01).
 *
 * <p>수수료가 여기 붙는 게 핵심이다. 주민등록등본은 정부24 온라인이 무료고 주민센터 방문이
 * 400원이다. 온라인을 먼저 권하는 이유가 이 차이다.
 */
@Entity
@Table(name = "document_issue_method")
public class DocumentIssueMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueMethod method;

    @Column(length = 100)
    private String agency;

    @Enumerated(EnumType.STRING)
    @Column(name = "agency_code", nullable = false, length = 30)
    private IssueAgency agencyCode;

    @Column
    private String url;

    @Column(nullable = false)
    private int fee;

    @Column(name = "fee_note")
    private String feeNote;

    @Column
    private String requirements;

    @Column
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected DocumentIssueMethod() {}

    public Long getId() {
        return id;
    }

    public Long getDocumentTypeId() {
        return documentTypeId;
    }

    public IssueMethod getMethod() {
        return method;
    }

    public String getAgency() {
        return agency;
    }

    public IssueAgency getAgencyCode() {
        return agencyCode;
    }

    public String getUrl() {
        return url;
    }

    public int getFee() {
        return fee;
    }

    public String getFeeNote() {
        return feeNote;
    }

    public String getRequirements() {
        return requirements;
    }

    public String getNote() {
        return note;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean online() {
        return method == IssueMethod.ONLINE;
    }
}
