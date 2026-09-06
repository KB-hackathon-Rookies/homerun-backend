package com.homerun.domain.policy.service;

import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.policy.dto.response.AncillaryCostResponse;
import com.homerun.domain.policy.dto.response.TotalCostResponse;
import com.homerun.domain.policy.model.CollateralType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * 부대비용(BR-08a)·총비용(BR-21)·중개보수(BR-27)를 계산한다. 정책 판정이 아니라 산술 계산이라
 * 저장하지 않는다. 요율·구간·한도는 전부 config_effective 에서 읽는다 — 코드에 박지 않는다.
 *
 * <p>중개보수 요율은 시도 조례라 서울 기준만 심어 두었다(REVIEW). 6억 초과 구간은 요율표를
 * 확보하지 못해 중개보수를 null 로 돌려주고 합계도 내지 않는다 — 없는 값으로 지어내지 않는다.
 */
@Service
public class AncillaryCostCalculator {

    // 중개보수(BR-27) — 거래금액(보증금) 구간별 요율·한도.
    private static final String BROKER_B1_CEIL = "FCT-215";
    private static final String BROKER_B1_RATE = "FCT-216";
    private static final String BROKER_B1_CAP = "FCT-217";
    private static final String BROKER_B2_CEIL = "FCT-218";
    private static final String BROKER_B2_RATE = "FCT-219";
    private static final String BROKER_B2_CAP = "FCT-220";
    private static final String BROKER_B3_CEIL = "FCT-221";
    private static final String BROKER_B3_RATE = "FCT-222";
    private static final String BROKER_VAT = "FCT-223";

    // 인지세(BR-08a·BR-21) — 대출금 구간별 고객 부담분.
    private static final String STAMP_B1_CEIL = "FCT-224";
    private static final String STAMP_B1_AMT = "FCT-225";
    private static final String STAMP_B2_CEIL = "FCT-226";
    private static final String STAMP_B2_AMT = "FCT-227";
    private static final String STAMP_B3_CEIL = "FCT-228";
    private static final String STAMP_B3_AMT = "FCT-229";
    private static final String STAMP_B4_AMT = "FCT-230";

    // 대출보증 보증료율(BR-08a).
    private static final String HF_FEE_MIN = "FCT-231";
    private static final String HF_FEE_MAX = "FCT-232";
    private static final String HUG_FEE_MIN = "FCT-233";
    private static final String HUG_FEE_MAX = "FCT-234";
    private static final String SGI_FEE_APT = "FCT-235";
    private static final String SGI_FEE_ETC = "FCT-236";

    private static final String MOVING_DEFAULT = "FCT-237";
    private static final String DOCUMENT_FEE = "FCT-238";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final FactRegistry facts;

    public AncillaryCostCalculator(FactRegistry facts) {
        this.facts = facts;
    }

    /**
     * 부대비용 내역(BR-08a). 담보를 모르면(collateral null) 보증료율을 중간값으로 추정한다.
     *
     * @param deposit 보증금(원)
     * @param loanAmount 대출금(원)
     * @param collateral 담보 방식. null 이면 보증료율 중간값 추정
     * @param apartment SGI 보증료율이 아파트/그 외로 갈려 필요하다. null 이면 그 외(보수적)로 본다
     * @param movingCostInput 이사비 사용자 입력. null 이면 기본값
     */
    public AncillaryCostResponse ancillaryCost(
            long deposit, long loanAmount, CollateralType collateral, Boolean apartment, Long movingCostInput) {
        Long brokerage = brokerageFee(deposit);
        long stamp = stampDutyCustomer(loanAmount);
        GuaranteeRate rate = guaranteeRate(collateral, apartment);
        long guarantee = wonOf(BigDecimal.valueOf(loanAmount).multiply(rate.ratio()));
        long moving = movingCostInput != null ? movingCostInput : facts.won(MOVING_DEFAULT);
        long document = facts.won(DOCUMENT_FEE);

        Long total = brokerage == null ? null : brokerage + stamp + guarantee + moving + document;
        return new AncillaryCostResponse(brokerage, stamp, guarantee, moving, document, total, rate.estimated());
    }

    /**
     * 1년차 총비용(BR-21) = 연 이자 + 보증료 + 인지세 고객부담. 금리는 퍼센트로 받는다(연 2.2% → 2.2).
     */
    public TotalCostResponse totalCostYear1(
            long loanAmount, BigDecimal annualRatePercent, CollateralType collateral, Boolean apartment) {
        long interest = wonOf(BigDecimal.valueOf(loanAmount).multiply(ratioOf(annualRatePercent)));
        GuaranteeRate rate = guaranteeRate(collateral, apartment);
        long guarantee = wonOf(BigDecimal.valueOf(loanAmount).multiply(rate.ratio()));
        long stamp = stampDutyCustomer(loanAmount);
        return new TotalCostResponse(interest, guarantee, stamp, interest + guarantee + stamp, rate.estimated());
    }

    /** 중개보수(BR-27) = min(보증금 × 요율, 한도액) × (1 + 부가세율). 6억 초과면 요율 미확보라 null. */
    private Long brokerageFee(long deposit) {
        BigDecimal base;
        if (deposit < facts.won(BROKER_B1_CEIL)) {
            base = cappedBroker(deposit, BROKER_B1_RATE, BROKER_B1_CAP);
        } else if (deposit < facts.won(BROKER_B2_CEIL)) {
            base = cappedBroker(deposit, BROKER_B2_RATE, BROKER_B2_CAP);
        } else if (deposit < facts.won(BROKER_B3_CEIL)) {
            // 3구간은 한도액이 없다.
            base = BigDecimal.valueOf(deposit).multiply(ratioOf(rate(BROKER_B3_RATE)));
        } else {
            return null; // 6억 초과 요율표 미확보 -- 지어내지 않는다.
        }
        BigDecimal withVat = base.multiply(BigDecimal.ONE.add(ratioOf(rate(BROKER_VAT))));
        return wonOf(withVat);
    }

    private BigDecimal cappedBroker(long deposit, String rateCode, String capCode) {
        BigDecimal raw = BigDecimal.valueOf(deposit).multiply(ratioOf(rate(rateCode)));
        BigDecimal cap = BigDecimal.valueOf(facts.won(capCode));
        return raw.min(cap);
    }

    /** 인지세 고객 부담분(BR-08a·BR-21) — 대출금 구간별. */
    private long stampDutyCustomer(long loanAmount) {
        if (loanAmount <= facts.won(STAMP_B1_CEIL)) {
            return facts.won(STAMP_B1_AMT);
        }
        if (loanAmount <= facts.won(STAMP_B2_CEIL)) {
            return facts.won(STAMP_B2_AMT);
        }
        if (loanAmount <= facts.won(STAMP_B3_CEIL)) {
            return facts.won(STAMP_B3_AMT);
        }
        return facts.won(STAMP_B4_AMT);
    }

    /**
     * 대출보증 보증료율(비율). 담보를 알면 그 기관 값을, 모르면 전체 범위 중간값을 쓴다.
     * SGI 만 단일 요율이라 추정이 아니고, HF·HUG·미확정은 범위 중간값이라 추정으로 표시한다.
     */
    private GuaranteeRate guaranteeRate(CollateralType collateral, Boolean apartment) {
        if (collateral == null) {
            // 전체 범위 [최저 하한, 최고 상한] 의 중간값.
            BigDecimal lo = rate(HF_FEE_MIN); // 세 기관 중 가장 낮은 하한
            BigDecimal hi = rate(HUG_FEE_MAX); // 세 기관 중 가장 높은 상한
            return new GuaranteeRate(ratioOf(lo.add(hi).divide(TWO)), true);
        }
        return switch (collateral) {
            case HF -> new GuaranteeRate(midRatio(HF_FEE_MIN, HF_FEE_MAX), true);
            case HUG -> new GuaranteeRate(midRatio(HUG_FEE_MIN, HUG_FEE_MAX), true);
            case SGI ->
                new GuaranteeRate(ratioOf(rate(Boolean.TRUE.equals(apartment) ? SGI_FEE_APT : SGI_FEE_ETC)), false);
        };
    }

    private BigDecimal midRatio(String minCode, String maxCode) {
        return ratioOf(rate(minCode).add(rate(maxCode)).divide(TWO));
    }

    /** 퍼센트 팩트 값(0.3 등). */
    private BigDecimal rate(String factCode) {
        return facts.require(factCode).requireNumber();
    }

    /** 퍼센트를 비율로 바꾼다(0.3% → 0.003). */
    private BigDecimal ratioOf(BigDecimal percent) {
        return percent.divide(HUNDRED, 10, RoundingMode.HALF_UP);
    }

    private long wonOf(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 보증료율과 그 값이 추정치인지. */
    private record GuaranteeRate(BigDecimal ratio, boolean estimated) {}
}
