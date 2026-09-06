package com.homerun.domain.settlement.type;

/**
 * 대출 상환 방식. DB CHECK 제약(ck_loan_account_repayment)과 값이 같아야 한다.
 *
 * <p>소득공제(BR-29)는 만기일시상환이면 이자만 대상이라 이 값이 갈린다.
 */
public enum RepaymentType {
    /** 만기일시상환. 매달 이자만, 원금은 만기에. 버팀목 등 전세대출 대부분. */
    MATURITY_LUMP_SUM,
    /** 원리금균등분할상환. */
    EQUAL_INSTALLMENT,
    /** 미상. */
    UNKNOWN
}
