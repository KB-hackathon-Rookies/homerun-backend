package com.homerun.domain.rent.service;

import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.rent.dto.request.HousingBenefitRequest;
import com.homerun.domain.rent.dto.response.HousingBenefitResult;
import com.homerun.domain.rent.type.Verdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 주거급여와 청년 주거급여 분리지급을 판정한다(POL-04-04).
 *
 * <p>지급액은 기준임대료를 상한으로 실제 임차료까지만 나온다. 소득인정액에 따른 자기부담분
 * 차감식은 공식 근거를 확보하지 못해 넣지 않았다. 상한까지만 안내하고 정확한 금액은
 * 신청기관에서 확인하도록 한다.
 *
 * <p>#42: 7인 이상 가구까지 다룬다. 10인 이상(2인 증가마다 추가 10% 가산)은 여전히
 * NEEDS_CHECK — 이번 범위 밖이다.
 */
@Service
public class HousingBenefitEvaluator {

    /** 가구원 수별 소득인정액 선정 기준. 8인 이상은 아직 팩트 레지스트리에 없다(#42). */
    private static final Map<Integer, String> INCOME_THRESHOLD = Map.ofEntries(
            Map.entry(1, "FCT-039"),
            Map.entry(2, "FCT-184"),
            Map.entry(3, "FCT-185"),
            Map.entry(4, "FCT-186"),
            Map.entry(5, "FCT-187"),
            Map.entry(6, "FCT-188"),
            Map.entry(7, "FCT-189"));

    /** 서울 기준임대료. 가구원 수별로 코드가 다르다. 10인 이상은 아직 없다(#42). */
    private static final Map<Integer, String> SEOUL_RENT_CEILING = Map.ofEntries(
            Map.entry(1, "FCT-040"),
            Map.entry(2, "FCT-151"),
            Map.entry(3, "FCT-152"),
            Map.entry(4, "FCT-153"),
            Map.entry(5, "FCT-154"),
            Map.entry(6, "FCT-155"),
            Map.entry(7, "FCT-190"),
            Map.entry(8, "FCT-191"),
            Map.entry(9, "FCT-191"));

    private static final int YOUTH_MIN_AGE = 19;
    private static final int YOUTH_MAX_AGE_EXCLUSIVE = 30;

    private final FactRegistry facts;

    public HousingBenefitEvaluator(FactRegistry facts) {
        this.facts = facts;
    }

    public HousingBenefitResult evaluate(HousingBenefitRequest request) {
        List<String> reasons = new ArrayList<>();

        String ceilingCode = SEOUL_RENT_CEILING.get(request.householdSize());
        if (ceilingCode == null) {
            reasons.add("가구원 %d인의 서울 기준임대료가 아직 확보되지 않았다".formatted(request.householdSize()));
            return new HousingBenefitResult(Verdict.NEEDS_CHECK, false, 0, 0, false, reasons);
        }

        Fact ceilingFact = facts.require(ceilingCode);
        long ceiling = ceilingFact.requireWon();
        IncomeJudgement income = judgeIncome(request, reasons);
        Verdict verdict = income.verdict();

        // 대상이 아닌데 금액을 보여주면 받을 수 있는 것으로 읽힌다.
        long benefitCeiling = verdict == Verdict.INELIGIBLE ? 0 : Math.min(ceiling, request.monthlyRent());
        boolean youth = judgeYouthSeparatePayment(request, reasons);

        if (youth && verdict == Verdict.ELIGIBLE) {
            reasons.add("청년 주거급여 분리지급 대상이다");
        }
        if (verdict != Verdict.INELIGIBLE) {
            reasons.add("기준임대료 %,d원과 실제 임차료 %,d원 중 작은 쪽이 상한이다".formatted(ceiling, request.monthlyRent()));
            reasons.add("실제 지급액은 소득인정액에 따라 자기부담분이 빠진다. 정확한 금액은 신청기관에서 확인한다");
        }

        boolean provisional = ceilingFact.provisional() || income.provisional();
        if (provisional) {
            reasons.add("일부 기준값은 아직 고시 원문으로 확정되지 않아 바뀔 수 있다");
        }

        return new HousingBenefitResult(verdict, youth, ceiling, benefitCeiling, provisional, reasons);
    }

    /** 판정 결과와, 그 판정에 쓴 기준값이 REVIEW 등급이라 바뀔 수 있는지를 함께 담는다. */
    private record IncomeJudgement(Verdict verdict, boolean provisional) {}

    /**
     * 소득인정액이 선정 기준 이하인지 본다.
     *
     * <p>7인까지 기준이 확보돼 있다(#42). 8인 이상은 불가로 단정하지 않고 추가확인으로 넘긴다.
     */
    private IncomeJudgement judgeIncome(HousingBenefitRequest request, List<String> reasons) {
        String thresholdCode = INCOME_THRESHOLD.get(request.householdSize());
        if (thresholdCode == null) {
            reasons.add("%d인 가구의 소득인정액 선정 기준이 아직 없어 확인이 필요하다".formatted(request.householdSize()));
            return new IncomeJudgement(Verdict.NEEDS_CHECK, false);
        }
        Fact thresholdFact = facts.require(thresholdCode);
        long threshold = thresholdFact.requireWon();
        if (request.recognizedIncome() <= threshold) {
            reasons.add("소득인정액이 %d인가구 선정 기준 %,d원 이하다".formatted(request.householdSize(), threshold));
            return new IncomeJudgement(Verdict.ELIGIBLE, thresholdFact.provisional());
        }
        reasons.add("소득인정액이 %d인가구 선정 기준 %,d원을 넘는다".formatted(request.householdSize(), threshold));
        return new IncomeJudgement(Verdict.INELIGIBLE, thresholdFact.provisional());
    }

    /**
     * 청년 분리지급 요건(FCT-042 · FCT-043 · FCT-044).
     *
     * <p>부모 가구가 이미 수급 중이어야 한다. 청년 단독 신청은 불가하다.
     */
    private boolean judgeYouthSeparatePayment(HousingBenefitRequest request, List<String> reasons) {
        boolean ageOk = request.age() >= YOUTH_MIN_AGE && request.age() < YOUTH_MAX_AGE_EXCLUSIVE;
        if (!ageOk || request.married() || !request.livesApartFromParents()) {
            return false;
        }
        if (!request.parentOnHousingBenefit()) {
            reasons.add("청년 분리지급은 부모 가구가 이미 주거급여를 받고 있어야 신청할 수 있다");
            return false;
        }
        return true;
    }
}
