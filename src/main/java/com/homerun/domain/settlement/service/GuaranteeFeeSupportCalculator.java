package com.homerun.domain.settlement.service;

import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.settlement.dto.response.GuaranteeFeeSupportAmountResponse;
import com.homerun.domain.settlement.type.GuaranteeFeeSupportCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * 보증료 지원 금액 계산(BR-31, FR-H2-01). 자격 판정은 2루(GTE-01-04)에서 하고, 여기서는 실제
 * 납부한 보증료로 지원액만 낸다. 판정이 아니라 산술 계산이라 저장하지 않고 한도·지원율·기준일은
 * 전부 config_effective 에서 읽는다.
 *
 * <p>지원액 = min(보증료 × 지원율, 한도). 청년·신혼은 전액(100%), 청년 외는 90%. 한도는 가입일이
 * 기준일 이후면 40만, 이전이면 30만이다.
 */
@Service
public class GuaranteeFeeSupportCalculator {

    private static final String CEILING_NEW = "FCT-242"; // 40만 (기준일 이후 가입)
    private static final String CEILING_OLD = "FCT-243"; // 30만 (기준일 이전 가입)
    private static final String GENERAL_RATE = "FCT-244"; // 청년 외 지원율 90%
    private static final String CUTOFF_DATE = "FCT-245"; // 한도 기준일

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal FULL = new BigDecimal("100");

    private final FactRegistry facts;

    public GuaranteeFeeSupportCalculator(FactRegistry facts) {
        this.facts = facts;
    }

    public GuaranteeFeeSupportAmountResponse calculate(
            GuaranteeFeeSupportCategory category, long guaranteeFeePaid, LocalDate enrolledAt) {
        // 청년 외만 90%, 청년·신혼은 전액.
        BigDecimal ratePercent = category == GuaranteeFeeSupportCategory.GENERAL
                ? facts.require(GENERAL_RATE).requireNumber()
                : FULL;

        LocalDate cutoff = LocalDate.parse(facts.require(CUTOFF_DATE).text());
        long ceiling = enrolledAt.isBefore(cutoff) ? facts.won(CEILING_OLD) : facts.won(CEILING_NEW);

        long byRate = BigDecimal.valueOf(guaranteeFeePaid)
                .multiply(ratePercent.divide(HUNDRED, 10, RoundingMode.HALF_UP))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long support = Math.min(byRate, ceiling);
        return new GuaranteeFeeSupportAmountResponse(category, ratePercent, ceiling, support);
    }
}
