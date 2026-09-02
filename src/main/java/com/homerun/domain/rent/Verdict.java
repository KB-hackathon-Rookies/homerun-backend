package com.homerun.domain.rent;

/**
 * 3단계 판정.
 *
 * <p>확정되지 않은 기준이나 "모름" 입력은 불가가 아니라 추가확인이다(NFR-01-06, COM-05-04).
 */
public enum Verdict {
    ELIGIBLE,
    NEEDS_CHECK,
    INELIGIBLE
}
