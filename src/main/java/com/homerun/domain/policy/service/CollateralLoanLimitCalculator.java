package com.homerun.domain.policy.service;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.type.MaritalStatus;
import com.homerun.domain.policy.dto.response.CollateralLoanLimitListResponse;
import com.homerun.domain.policy.dto.response.CollateralLoanLimitResponse;
import com.homerun.domain.policy.model.CollateralType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 일반 전세대출의 담보별 한도를 계산한다(BR-19). 은행이 담보를 정하지만 방식마다 공식이 달라
 * HF·HUG·SGI 세 가지를 모두 계산해 범위로 보여준다.
 *
 * <p>정책 판정이 아니다. 사용자 입력으로 한도를 산술 계산할 뿐이라 저장하지 않는다. 기준 수치는
 * 전부 config_effective 에서 읽는다 — 한도·비율을 코드에 박지 않는다.
 */
@Service
public class CollateralLoanLimitCalculator {

    private static final String HF_CAP = "FCT-211";
    private static final String HUG_CAP = "FCT-212";
    private static final String SGI_CAP = "FCT-213";
    private static final String HUG_YOUTH_RATIO = "FCT-214";
    private static final String HF_INCOME_MULTIPLE = "FCT-059";

    /** 기본 보증금 대비 비율. HUG 청년·신혼만 FCT-214(90%)로 올라간다. */
    private static final BigDecimal BASE_RATIO = new BigDecimal("0.8");

    private static final int HUG_YOUTH_MAX_AGE = 34;

    private final FactRegistry facts;
    private final Clock clock;

    public CollateralLoanLimitCalculator(FactRegistry facts, Clock clock) {
        this.facts = facts;
        this.clock = clock;
    }

    public CollateralLoanLimitListResponse calculate(PlanInput input) {
        List<CollateralLoanLimitResponse> rows = new ArrayList<>();
        rows.add(hf(input));
        rows.add(hug(input));
        rows.add(sgi(input));

        List<Long> known = rows.stream()
                .map(CollateralLoanLimitResponse::limit)
                .filter(l -> l != null)
                .toList();
        Long min = known.stream().min(Long::compareTo).orElse(null);
        Long max = known.stream().max(Long::compareTo).orElse(null);
        return new CollateralLoanLimitListResponse(rows, min, max);
    }

    /** HF = min(보증금 × 0.8, 증빙소득 × 3.5, 2.22억). 증빙소득을 모르면 계산할 수 없다. */
    private CollateralLoanLimitResponse hf(PlanInput input) {
        Long deposit = input == null ? null : input.getHopeDeposit();
        Long monthlyIncome = input == null ? null : input.getMonthlyIncome();
        if (deposit == null || monthlyIncome == null) {
            return unknown(CollateralType.HF, "보증금과 증빙소득이 있어야 한도를 계산할 수 있어요.");
        }
        Long cap = won(HF_CAP);
        BigDecimal multiple = number(HF_INCOME_MULTIPLE);
        if (cap == null || multiple == null) {
            return unknown(CollateralType.HF, "HF 한도 기준을 아직 확인할 수 없어요.");
        }
        long byDeposit = ratioOf(deposit, BASE_RATIO);
        long byIncome = BigDecimal.valueOf(monthlyIncome)
                .multiply(BigDecimal.valueOf(12))
                .multiply(multiple)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
        long limit = Math.min(Math.min(byDeposit, byIncome), cap);
        return new CollateralLoanLimitResponse(
                CollateralType.HF,
                limit,
                provisional(HF_CAP, HF_INCOME_MULTIPLE),
                appliedNote(limit, byDeposit, byIncome, cap));
    }

    /** HUG = 만 34세 이하·신혼 min(보증금 × 0.9, 4억), 그 외 min(보증금 × 0.8, 4억). 병역 보정 미적용. */
    private CollateralLoanLimitResponse hug(PlanInput input) {
        Long deposit = input == null ? null : input.getHopeDeposit();
        if (deposit == null) {
            return unknown(CollateralType.HUG, "보증금이 있어야 한도를 계산할 수 있어요.");
        }
        Long cap = won(HUG_CAP);
        if (cap == null) {
            return unknown(CollateralType.HUG, "HUG 한도 기준을 아직 확인할 수 없어요.");
        }
        boolean youth = qualifiesForYouthRatio(input);
        BigDecimal ratio = BASE_RATIO;
        if (youth) {
            BigDecimal pct = number(HUG_YOUTH_RATIO);
            if (pct == null) {
                return unknown(CollateralType.HUG, "HUG 청년 비율 기준을 아직 확인할 수 없어요.");
            }
            ratio = pct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        }
        long limit = Math.min(ratioOf(deposit, ratio), cap);
        String note = youth ? "만 34세 이하·신혼은 보증금의 90%까지예요." : "보증금의 80%까지예요.";
        return new CollateralLoanLimitResponse(CollateralType.HUG, limit, provisional(HUG_CAP), note);
    }

    /** SGI = min(보증금 × 0.8, 5억). */
    private CollateralLoanLimitResponse sgi(PlanInput input) {
        Long deposit = input == null ? null : input.getHopeDeposit();
        if (deposit == null) {
            return unknown(CollateralType.SGI, "보증금이 있어야 한도를 계산할 수 있어요.");
        }
        Long cap = won(SGI_CAP);
        if (cap == null) {
            return unknown(CollateralType.SGI, "SGI 한도 기준을 아직 확인할 수 없어요.");
        }
        long limit = Math.min(ratioOf(deposit, BASE_RATIO), cap);
        return new CollateralLoanLimitResponse(CollateralType.SGI, limit, provisional(SGI_CAP), "보증금의 80%까지예요.");
    }

    /**
     * HUG 90% 대상인가. 만 34세 이하이거나 신혼(기혼)이면 해당.
     *
     * <p>병역 보정은 적용하지 않는다(BR-19 명시) — 실제 만 나이로만 본다. 생년월일을 모르면
     * 혼인 여부로만 판단하고, 둘 다 모르면 기본 비율(80%)로 둔다.
     */
    private boolean qualifiesForYouthRatio(PlanInput input) {
        if (input != null && input.getMaritalStatus() == MaritalStatus.MARRIED) {
            return true;
        }
        LocalDate birthDate = input == null ? null : input.getBirthDate();
        if (birthDate == null) {
            return false;
        }
        int age = Period.between(birthDate, LocalDate.now(clock)).getYears();
        return age <= HUG_YOUTH_MAX_AGE;
    }

    private long ratioOf(long deposit, BigDecimal ratio) {
        return BigDecimal.valueOf(deposit)
                .multiply(ratio)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    private String appliedNote(long limit, long byDeposit, long byIncome, long cap) {
        if (limit == byIncome && byIncome < byDeposit) {
            return "증빙소득(연소득 3.5배)에 막혀 있어요. 소득이 늘면 한도가 올라가요.";
        }
        if (limit == cap && cap < byDeposit) {
            return "상품 최대한도에 걸려 있어요.";
        }
        return "보증금의 80%까지예요.";
    }

    private CollateralLoanLimitResponse unknown(CollateralType type, String note) {
        return new CollateralLoanLimitResponse(type, null, false, note);
    }

    /** 금액 팩트. 못 쓰면 null — 없는 값으로 한도를 지어내지 않는다. */
    private Long won(String factCode) {
        try {
            return facts.won(factCode);
        } catch (FactNotFoundException | UnusableFactException e) {
            return null;
        }
    }

    private BigDecimal number(String factCode) {
        try {
            return facts.require(factCode).number();
        } catch (FactNotFoundException | UnusableFactException e) {
            return null;
        }
    }

    private boolean provisional(String... factCodes) {
        for (String code : factCodes) {
            try {
                if (facts.require(code).provisional()) {
                    return true;
                }
            } catch (FactNotFoundException | UnusableFactException e) {
                // 못 쓰는 팩트는 여기 오기 전에 이미 걸러졌다.
            }
        }
        return false;
    }
}
