package com.homerun.rent;

import com.homerun.fact.FactRegistry;
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
 */
@Service
public class HousingBenefitEvaluator {

    /** 1인가구 소득인정액 기준. 다인 가구 기준은 아직 팩트 레지스트리에 없다. */
    private static final String SINGLE_INCOME_THRESHOLD = "FCT-039";

    /** 서울 기준임대료. 가구원 수별로 코드가 다르다. */
    private static final Map<Integer, String> SEOUL_RENT_CEILING = Map.of(
            1, "FCT-040",
            2, "FCT-151",
            3, "FCT-152",
            4, "FCT-153",
            5, "FCT-154",
            6, "FCT-155");

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
            return new HousingBenefitResult(Verdict.NEEDS_CHECK, false, 0, 0, reasons);
        }

        long ceiling = facts.won(ceilingCode);
        long expected = Math.min(ceiling, request.monthlyRent());

        Verdict verdict = judgeIncome(request, reasons);
        boolean youth = judgeYouthSeparatePayment(request, reasons);

        if (youth && verdict == Verdict.ELIGIBLE) {
            reasons.add("청년 주거급여 분리지급 대상이다");
        }
        reasons.add("기준임대료 %,d원과 실제 임차료 %,d원 중 작은 쪽이 상한이다".formatted(ceiling, request.monthlyRent()));

        return new HousingBenefitResult(verdict, youth, ceiling, expected, reasons);
    }

    /**
     * 소득인정액이 선정 기준 이하인지 본다.
     *
     * <p>1인가구 기준만 확보돼 있다. 다인 가구는 불가로 단정하지 않고 추가확인으로 넘긴다.
     */
    private Verdict judgeIncome(HousingBenefitRequest request, List<String> reasons) {
        if (request.householdSize() > 1) {
            reasons.add("%d인 가구의 소득인정액 선정 기준이 아직 없어 확인이 필요하다".formatted(request.householdSize()));
            return Verdict.NEEDS_CHECK;
        }
        long threshold = facts.won(SINGLE_INCOME_THRESHOLD);
        if (request.recognizedIncome() <= threshold) {
            reasons.add("소득인정액이 1인가구 선정 기준 %,d원 이하다".formatted(threshold));
            return Verdict.ELIGIBLE;
        }
        reasons.add("소득인정액이 1인가구 선정 기준 %,d원을 넘는다".formatted(threshold));
        return Verdict.INELIGIBLE;
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
