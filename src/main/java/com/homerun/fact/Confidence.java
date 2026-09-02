package com.homerun.fact;

/**
 * 기준 수치의 확정도.
 *
 * <p>판정에 쓸 수 있는지가 값마다 다르다. 확정되지 않은 수치로 가능·불가를 단정하지 않고
 * 추가확인으로 넘기는 것이 원칙이다(NFR-01-06).
 */
public enum Confidence {
    /** 원문으로 확인됨. 그대로 쓴다. */
    CONFIRMED(true, false),
    /** 조사 중 정정된 값. 구 문서를 믿지 말 것. 그대로 쓴다. */
    CORRECTED(true, false),
    /** 쓰되 화면에 "변경 가능"을 표시한다. */
    REVIEW(true, true),
    /** 값을 모른다. 판정 금지. */
    UNKNOWN(false, false),
    /** 문서 간 값이 엇갈린다. 판정 금지. */
    CONFLICT(false, false),
    /** 폐지된 제도. 판정 금지하고 대체 상품을 안내한다. */
    RETIRED(false, false);

    private final boolean usable;
    private final boolean provisional;

    Confidence(boolean usable, boolean provisional) {
        this.usable = usable;
        this.provisional = provisional;
    }

    /** 이 수치로 정책 가능·불가를 판정해도 되는가. */
    public boolean usable() {
        return usable;
    }

    /** 쓸 수는 있지만 바뀔 수 있다고 사용자에게 알려야 하는가. */
    public boolean provisional() {
        return provisional;
    }
}
