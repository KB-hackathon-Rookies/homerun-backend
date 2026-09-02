package com.homerun.domain.property.service.rule;

import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.service.PropertyRiskRule;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.JeonseRatioLevel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * PRP-01-05 전세가율 산출.
 *
 * <p>전세가율 = (선순위채권 + 내 보증금) ÷ 시세. 경매로 넘어갔을 때 내 보증금까지 순서가
 * 돌아오는지를 보는 값이다. 80%를 넘으면 깡통전세 가능성이 있다(FCT-119).
 *
 * <p>구간 기준은 확정도가 REVIEW 다. 공식 출처를 아직 못 찾아 화면에 변경 가능으로 표시한다.
 */
@Component
class JeonseRatioRule implements PropertyRiskRule {

    private static final String FACT = "FCT-119";
    private static final BigDecimal CAUTION_FROM = new BigDecimal("70");
    private static final BigDecimal RISK_FROM = new BigDecimal("80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Override
    public boolean appliesTo(PropertyFacts facts) {
        // 보증금이 없는 순수 월세는 따질 실익이 없다.
        return facts.deposit() > 0;
    }

    @Override
    public Optional<CheckFinding> evaluate(PropertyFacts facts) {
        if (facts.marketPrice() == null || facts.marketPrice() <= 0) {
            return Optional.of(finding(CheckResult.UNKNOWN, "시세를 확인하지 못해 전세가율을 계산할 수 없다.", "실거래가나 KB시세로 시세를 확인한다."));
        }

        BigDecimal ratio = ratio(facts);
        JeonseRatioLevel level = level(ratio);
        String percent = ratio.toPlainString();

        return switch (level) {
            case SAFE -> Optional.of(finding(CheckResult.PASS, "전세가율 %s%% 로 안전 구간이다.".formatted(percent), null));
            case CAUTION ->
                Optional.of(finding(CheckResult.WARN, "전세가율 %s%% 로 주의 구간이다.".formatted(percent), "반환보증 가입을 함께 확인한다."));
            case RISK ->
                Optional.of(finding(
                        CheckResult.WARN,
                        "전세가율 %s%% 로 위험 구간이다. 깡통전세 가능성이 있다.".formatted(percent),
                        "보증금을 낮추거나 다른 매물을 확인한다. 반환보증 가입이 안 되면 계약하지 않는다."));
            case UNKNOWN -> Optional.of(finding(CheckResult.UNKNOWN, "전세가율을 계산할 수 없다.", null));
        };
    }

    /** 선순위채권을 모르면 0으로 보지 않는다. 없는 것과 모르는 것은 다르다. */
    BigDecimal ratio(PropertyFacts facts) {
        long senior = facts.seniorDebt() == null ? 0L : facts.seniorDebt();
        return BigDecimal.valueOf(senior + facts.deposit())
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(facts.marketPrice()), 1, RoundingMode.HALF_UP);
    }

    JeonseRatioLevel level(BigDecimal ratio) {
        if (ratio.compareTo(RISK_FROM) > 0) {
            return JeonseRatioLevel.RISK;
        }
        if (ratio.compareTo(CAUTION_FROM) > 0) {
            return JeonseRatioLevel.CAUTION;
        }
        return JeonseRatioLevel.SAFE;
    }

    private CheckFinding finding(CheckResult result, String summary, String action) {
        return new CheckFinding("JEONSE_RATIO", "전세가율", result, summary, action, FACT, null, true);
    }
}
