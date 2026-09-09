package com.homerun.domain.openbanking.type;

/**
 * 데모용 가짜 오픈뱅킹 자산 프로필. 사업자 미등록으로 실연동이 막혀, 화면 시연을 위해
 * 미리 정한 자산 값을 {@code financial_snapshot} 에 적재한다. 금액 단위는 원(₩).
 *
 * <p>전세 트랙 기준으로, 세 축(소득·자산·부채)으로 판정 시나리오를 갈라 보여 준다.
 * <ul>
 *   <li>{@link #KIM_KUKMIN} 표준 사회초년생 — 청년 버팀목 전세대출·반환보증 다 되는 밝은 기준선.
 *       월소득은 <b>연 단위 평균</b>이다. 실제 입금은 기본급·시간외수당·분기 성과급으로 달마다
 *       달라지고, 그 평균이 이 값이다({@code MockPersonaFixtures})</li>
 *   <li>{@link #LEE_TIGHT} 저소득·학자금 — 소득요건은 되나 자산 부족으로 부족자금 큼, 대안경로</li>
 *   <li>{@link #PARK_SENIOR} 고소득 경계 — 청년 버팀목 소득기준(5천) 초과, 일반 버팀목·SGI로 우회</li>
 * </ul>
 */
public enum Persona {
    KIM_KUKMIN("김국민", 42_000_000L, 3_800_000L, 1_500_000L, 0L, 0L),
    LEE_TIGHT("이빠듯", 3_000_000L, 1_300_000L, 1_100_000L, 12_000_000L, 150_000L),
    PARK_SENIOR("박사회", 40_000_000L, 4_200_000L, 2_000_000L, 20_000_000L, 400_000L);

    private final String label;
    private final long financialAsset;
    private final long monthlyIncome;
    private final long monthlyExpense;
    private final long loanBalance;
    private final long monthlyDebtPayment;

    Persona(
            String label,
            long financialAsset,
            long monthlyIncome,
            long monthlyExpense,
            long loanBalance,
            long monthlyDebtPayment) {
        this.label = label;
        this.financialAsset = financialAsset;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpense = monthlyExpense;
        this.loanBalance = loanBalance;
        this.monthlyDebtPayment = monthlyDebtPayment;
    }

    public String getLabel() {
        return label;
    }

    public long getFinancialAsset() {
        return financialAsset;
    }

    public long getMonthlyIncome() {
        return monthlyIncome;
    }

    public long getMonthlyExpense() {
        return monthlyExpense;
    }

    public long getLoanBalance() {
        return loanBalance;
    }

    public long getMonthlyDebtPayment() {
        return monthlyDebtPayment;
    }
}
