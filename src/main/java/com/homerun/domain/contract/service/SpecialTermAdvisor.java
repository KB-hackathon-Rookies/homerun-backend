package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.ContractDtos.SpecialTermAdvice;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 권장 특약 안내(PRP-02-03).
 *
 * <p>세 특약 모두 법정 필수 조항이 아니다. 임대인이 거절할 수 있고 거절했다고 계약이 무효가
 * 되지도 않는다. "필수"라고 단정하면 사용자가 협상 여지를 오해한다. 문구는 팩트 레지스트리
 * 원문을 그대로 쓴다.
 *
 * <p>보증금이 없는 순수 월세에는 셋 다 의미가 없다. 지킬 돈이 없기 때문이다.
 */
@Component
class SpecialTermAdvisor {

    private static final List<TermSpec> TERMS = List.of(
            new TermSpec(
                    "NO_NEW_MORTGAGE",
                    "근저당 설정 금지",
                    "FCT-116",
                    "전입신고를 해도 대항력은 다음날 0시에 생긴다. 잔금일 당일에 임대인이 근저당을 잡으면 은행이 선순위가 된다."),
            new TermSpec(
                    "LOAN_REJECTED_VOID", "대출 미승인 시 계약 무효", "FCT-117", "대출이 안 나와 잔금을 못 치르면 계약 불이행이 되어 계약금을 돌려받지 못한다."),
            new TermSpec(
                    "GUARANTEE_REJECTED_VOID", "반환보증 불가 시 계약 무효", "FCT-118", "대출이 나와도 반환보증에 가입하지 못하면 보증금을 지킬 수단이 없다."));

    private final FactRegistry facts;

    SpecialTermAdvisor(FactRegistry facts) {
        this.facts = facts;
    }

    List<SpecialTermAdvice> advise(LeaseContract contract) {
        boolean applicable = contract != null && contract.hasDeposit();
        return TERMS.stream().map(term -> term.toAdvice(facts, applicable)).toList();
    }

    private record TermSpec(String code, String label, String factCode, String reason) {

        SpecialTermAdvice toAdvice(FactRegistry facts, boolean applicable) {
            Fact fact = facts.require(factCode);
            return new SpecialTermAdvice(code, label, fact.text(), reason, factCode, applicable);
        }
    }
}
