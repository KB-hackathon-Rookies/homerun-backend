package com.homerun.domain.property.type;

/** 은행 사전상담에서 들은 대출 가능 여부. 확정 승인이 아니라 상담 당시 답변이다. */
public enum ConsultationResultStatus {
    POSSIBLE,
    DIFFICULT,
    DOCUMENT_REVIEW_REQUIRED,
    NOT_HEARD
}
