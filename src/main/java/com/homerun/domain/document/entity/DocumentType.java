package com.homerun.domain.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 서류 한 종류.
 *
 * <p>발급방법별로 기관·수수료·준비물이 달라서 그쪽은 {@link DocumentIssueMethod} 가 가진다.
 * 여기 있는 {@code fee} 는 대표값일 뿐이라 판정에 쓰지 않는다.
 */
@Entity
@Table(name = "document_type")
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String issuer;

    @Column(name = "issue_url")
    private String issueUrl;

    @Column(name = "online_available", nullable = false)
    private boolean onlineAvailable;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Column(nullable = false)
    private int fee;

    @Column
    private String note;

    protected DocumentType() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public boolean isOnlineAvailable() {
        return onlineAvailable;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public int getFee() {
        return fee;
    }

    public String getNote() {
        return note;
    }
}
