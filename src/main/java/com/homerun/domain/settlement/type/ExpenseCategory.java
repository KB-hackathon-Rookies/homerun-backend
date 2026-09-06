package com.homerun.domain.settlement.type;

/**
 * 고정지출 종류(DR-20). DB CHECK 제약(ck_fixed_expense_category)과 값이 같아야 한다.
 *
 * <p>연체 알림은 이자(INTEREST) 항목이 있어야 동작한다(FR-H5-01).
 */
public enum ExpenseCategory {
    /** 대출 이자(상환일). */
    INTEREST,
    /** 관리비. */
    MGMT,
    /** 기타 고정지출. */
    OTHER
}
