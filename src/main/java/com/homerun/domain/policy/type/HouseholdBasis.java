package com.homerun.domain.policy.type;

/**
 * 조건 하나를 어느 가구 기준으로 보는가(POL-01-03). {@code verdict_basis.ck_verdict_basis_household}
 * 와 값이 같아야 한다.
 *
 * <p>정책 판정을 두 벌로 쪼개지 않고 <b>조건</b>에 축을 붙인다. 실제로 갈리는 것은 소득·자산
 * 조건뿐이고 연령·무주택·보증금 상한은 가구 기준과 무관하기 때문이다. 판정을 통째로 두 개
 * 만들면 같은 조건을 두 번 평가하게 되고 {@code uq_policy_verdict} 에도 걸린다.
 */
public enum HouseholdBasis {
    /**
     * 본인(과 배우자)만으로 이루어진 가구. 지금까지의 모든 판정이 암묵적으로 써 온 기준이라
     * {@code rule_json} 에 축이 없으면 이것으로 본다. 버팀목의 "부부합산"도 부모가 끼지 않으므로
     * 여기에 해당한다.
     */
    SELF("독립가구"),

    /**
     * 부모를 포함한, 주민등록상 원래 속한 가구. 청년이 독립해 나와도 일부 정책은 여전히 부모
     * 가구의 소득으로 자격을 본다(예: 청년 주거급여 분리지급 — {@code FCT-044}).
     */
    ORIGIN("원가구");

    private final String label;

    HouseholdBasis(String label) {
        this.label = label;
    }

    /** 화면에 그대로 쓸 수 있는 이름. */
    public String label() {
        return label;
    }
}
