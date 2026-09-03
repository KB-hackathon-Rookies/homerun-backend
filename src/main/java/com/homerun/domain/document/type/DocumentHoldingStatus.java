package com.homerun.domain.document.type;

/**
 * 서류 보유 상태(EVI-01-04). {@code user_document.ck_doc_status} 와 값이 같아야 한다.
 *
 * <p>{@code EXPIRED} 는 사용자가 고르는 값이 아니다. 발급일과 인정 기간으로 판정해서
 * 붙인다(EVI-01-07). 손으로 바꾸게 두면 만료된 서류가 유효한 것으로 남는다.
 */
public enum DocumentHoldingStatus {
    /** 아직 준비하지 않았다. */
    NEEDED("미준비"),
    /** 요청해 두고 기다리는 중. 재직증명서처럼 며칠 걸리는 것. */
    IN_PROGRESS("준비 중"),
    /** 발급받아 손에 있다. */
    ISSUED("준비 완료"),
    /** 제출까지 끝났다. */
    SUBMITTED("제출 완료"),
    /** 인정 기간이 지났다. 다시 떼야 한다. */
    EXPIRED("재발급 필요");

    private final String label;

    DocumentHoldingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 사용자가 직접 고를 수 있는 상태인가. 만료는 판정 결과라 고를 수 없다. */
    public boolean selectable() {
        return this != EXPIRED;
    }

    /** 손에 들고 있는가. 방문 계획에서 뺄지 판단할 때 쓴다. */
    public boolean held() {
        return this == ISSUED || this == SUBMITTED;
    }
}
