package com.homerun.domain.plan.type;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 관문(PlanGate)별 기본 할 일 목록.
 *
 * <p>보증금이 걸린 계약은 전세든 반전세든 같은 검증을 받는다(도메인 규칙). 그래서
 * 등기부·반환보증처럼 보증금 전제인 할 일은 JEONSE 와 BANJEONSE 에만 붙인다.
 *
 * <p>순서(sequence)는 관문 안에서만 유일하면 된다.
 */
public enum StepTaskTemplate {
    AGREE_TERMS("AGREE_TERMS", "약관 동의", PlanGate.BENCH_ONBOARDING, 1, false),
    INPUT_BASIC_PROFILE("INPUT_BASIC_PROFILE", "기본정보 입력", PlanGate.BENCH_ONBOARDING, 2, false),

    INPUT_INCOME_ASSET("INPUT_INCOME_ASSET", "소득·자산 입력", PlanGate.FIRST_DIAGNOSIS, 1, false),
    INPUT_HOUSING_CONDITION("INPUT_HOUSING_CONDITION", "주거조건 입력", PlanGate.FIRST_DIAGNOSIS, 2, false),
    REVIEW_DIAGNOSIS("REVIEW_DIAGNOSIS", "독립 가능성 진단 확인", PlanGate.FIRST_DIAGNOSIS, 3, false),

    BANK_CONSULTATION("BANK_CONSULTATION", "은행 사전상담", PlanGate.SECOND_POLICY_SELECTION, 1, false, deposit()),
    LOAN_LIMIT_CHECK("LOAN_LIMIT_CHECK", "전세대출 한도 확인", PlanGate.SECOND_POLICY_SELECTION, 2, false, deposit()),
    MONTHLY_SUPPORT_CHECK(
            "MONTHLY_SUPPORT_CHECK",
            "월세 지원 정책 확인",
            PlanGate.SECOND_POLICY_SELECTION,
            3,
            false,
            EnumSet.of(LeaseType.WOLSE, LeaseType.BANJEONSE)),
    DOCUMENT_CHECK("DOCUMENT_CHECK", "필요 서류 확인", PlanGate.SECOND_POLICY_SELECTION, 4, false),

    PROPERTY_SEARCH("PROPERTY_SEARCH", "매물 확인", PlanGate.THIRD_EXECUTION, 1, false),
    BUILDING_REGISTER_CHECK("BUILDING_REGISTER_CHECK", "건축물대장 확인", PlanGate.THIRD_EXECUTION, 2, false),
    ACTUAL_PRICE_CHECK("ACTUAL_PRICE_CHECK", "실거래가 확인", PlanGate.THIRD_EXECUTION, 3, false),
    REGISTER_CHECK("REGISTER_CHECK", "등기부등본 확인", PlanGate.THIRD_EXECUTION, 4, false, deposit()),
    CONTRACT_CHECK("CONTRACT_CHECK", "계약서 확인", PlanGate.THIRD_EXECUTION, 5, false),
    SPECIAL_CLAUSE_CHECK("SPECIAL_CLAUSE_CHECK", "특약 확인", PlanGate.THIRD_EXECUTION, 6, false),
    DEPOSIT_PAYMENT("DEPOSIT_PAYMENT", "계약금 지급", PlanGate.THIRD_EXECUTION, 7, true),
    // 잔금·전입신고·확정일자는 같은 날 처리하고, 셋 다 되돌릴 수 없다.
    BALANCE_PAYMENT("BALANCE_PAYMENT", "잔금 지급", PlanGate.THIRD_EXECUTION, 8, true),
    MOVE_IN_REPORT("MOVE_IN_REPORT", "전입신고", PlanGate.THIRD_EXECUTION, 9, true),
    FIXED_DATE("FIXED_DATE", "확정일자 받기", PlanGate.THIRD_EXECUTION, 10, true),

    GUARANTEE_CHECK("GUARANTEE_CHECK", "전세보증금 반환보증 가입", PlanGate.HOME_SETTLEMENT, 1, false, deposit()),
    REGISTER_FIXED_EXPENSE("REGISTER_FIXED_EXPENSE", "고정지출 등록", PlanGate.HOME_SETTLEMENT, 2, false),
    FIRST_MONTH_CHECKIN("FIRST_MONTH_CHECKIN", "첫 달 실적 입력", PlanGate.HOME_SETTLEMENT, 3, false);

    private final String code;
    private final String displayName;
    private final PlanGate gate;
    private final int sequence;
    private final boolean irreversible;
    private final Set<LeaseType> leaseTypes;

    StepTaskTemplate(String code, String displayName, PlanGate gate, int sequence, boolean irreversible) {
        this(code, displayName, gate, sequence, irreversible, EnumSet.allOf(LeaseType.class));
    }

    StepTaskTemplate(
            String code,
            String displayName,
            PlanGate gate,
            int sequence,
            boolean irreversible,
            Set<LeaseType> leaseTypes) {
        this.code = code;
        this.displayName = displayName;
        this.gate = gate;
        this.sequence = sequence;
        this.irreversible = irreversible;
        this.leaseTypes = leaseTypes;
    }

    private static Set<LeaseType> deposit() {
        return EnumSet.of(LeaseType.JEONSE, LeaseType.BANJEONSE);
    }

    public static List<StepTaskTemplate> of(PlanGate gate, LeaseType leaseType) {
        return Arrays.stream(values())
                .filter(template -> template.gate == gate)
                .filter(template -> template.leaseTypes.contains(leaseType))
                .sorted(java.util.Comparator.comparingInt(StepTaskTemplate::sequence))
                .toList();
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public PlanGate gate() {
        return gate;
    }

    public int sequence() {
        return sequence;
    }

    public boolean irreversible() {
        return irreversible;
    }
}
