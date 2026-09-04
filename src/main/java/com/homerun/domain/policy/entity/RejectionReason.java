package com.homerun.domain.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * FAIL 판정의 탈락 사유 하나. {@code verdict_basis} 가 조건별 충족 여부를 남긴다면, 이건 그중
 * 사용자에게 "왜 안 되는지"로 보여줄 사유와 "그럼 뭘 보면 되는지" 대안 상품을 묶는다.
 */
@Entity
@Table(name = "rejection_reason")
public class RejectionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verdict_id", nullable = false)
    private Long verdictId;

    @Column(name = "reason_code", nullable = false, length = 50)
    private String reasonCode;

    @Column(name = "reason_label", nullable = false, length = 200)
    private String reasonLabel;

    @Column(name = "alternative_id")
    private Long alternativeId;

    protected RejectionReason() {}

    private RejectionReason(Long verdictId, String reasonCode, String reasonLabel, Long alternativeId) {
        this.verdictId = verdictId;
        this.reasonCode = reasonCode;
        this.reasonLabel = reasonLabel;
        this.alternativeId = alternativeId;
    }

    public static RejectionReason create(Long verdictId, String reasonCode, String reasonLabel, Long alternativeId) {
        return new RejectionReason(verdictId, reasonCode, reasonLabel, alternativeId);
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonLabel() {
        return reasonLabel;
    }

    public Long getAlternativeId() {
        return alternativeId;
    }
}
