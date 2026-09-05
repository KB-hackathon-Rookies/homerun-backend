package com.homerun.domain.policy.service;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.policy.dto.response.BankLoanRateListResponse;
import com.homerun.domain.policy.dto.response.BankLoanRateResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 일반(비정책) 전세대출의 은행별 공시 평균금리 비교(POL-03-07).
 *
 * <p>정책대출로 안 되는 사용자에게 남는 선택지가 은행 자체 대출이다. 그때 광고금리가 아니라
 * 공시 평균금리를 봐야 한다(FCT-102) — 광고금리는 우대조건을 전부 채운 값이라 실현율이 낮다.
 *
 * <p>판정이 아니다. 사용자 조건을 보지 않고 공시값을 그대로 비교해 보여줄 뿐이라 계획에
 * 매달지 않는다({@code GuaranteeAgencyController} 와 같은 참조 데이터 취급).
 */
@Service
public class BankLoanRateService {

    /** 은행별 금리 팩트. V33 이 FCT-101 의 note 에서 갈라 세웠다. */
    private static final List<String> BANK_RATE_FACT_CODES =
            List.of("FCT-201", "FCT-202", "FCT-203", "FCT-204", "FCT-205", "FCT-206", "FCT-207", "FCT-208");

    private static final String PUBLISHED_AVERAGE_FACT_CODE = "FCT-101";
    private static final String NO_AD_RATE_FACT_CODE = "FCT-102";

    private final FactRegistry facts;

    public BankLoanRateService(FactRegistry facts) {
        this.facts = facts;
    }

    public BankLoanRateListResponse compare() {
        List<BankLoanRateResponse> banks = BANK_RATE_FACT_CODES.stream()
                .map(this::readUsable)
                .flatMap(Optional::stream)
                // 수치가 없는 팩트는 비교할 수 없다. 문구만 있는 값을 0 으로 읽으면 그 은행이
                // 가장 싼 것처럼 맨 위에 올라간다.
                .filter(fact -> fact.number() != null)
                .map(BankLoanRateResponse::from)
                .sorted(Comparator.comparing(BankLoanRateResponse::rate))
                .toList();

        Optional<Fact> average = readUsable(PUBLISHED_AVERAGE_FACT_CODE);

        return new BankLoanRateListResponse(
                banks,
                // 은행 목록의 평균을 계산하지 않는다. 공시 전체 평균은 집계 대상이 달라
                // 나열된 은행들의 산술평균과 값이 다르다(V33 주석).
                average.map(Fact::number).orElse(null),
                average.map(Fact::text).orElse(null),
                average.map(Fact::provisional).orElse(false),
                readUsable(NO_AD_RATE_FACT_CODE).map(Fact::text).orElse(null));
    }

    /**
     * 못 쓰는 팩트는 빼고 나머지로 비교를 만든다.
     *
     * <p>은행 하나의 금리가 확정되지 않았다고 비교 전체를 못 보여줄 이유가 없다. 다만 없는 값을
     * 지어내지도 않는다 — 그 은행은 목록에서 빠진다.
     */
    private Optional<Fact> readUsable(String factCode) {
        try {
            return Optional.of(facts.require(factCode));
        } catch (FactNotFoundException | UnusableFactException e) {
            return Optional.empty();
        }
    }
}
