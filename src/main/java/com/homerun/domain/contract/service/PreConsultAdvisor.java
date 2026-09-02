package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.ContractDtos.PreConsultGuide;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.fact.service.FactRegistry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 은행 사전상담 안내(PRP-02-01).
 *
 * <p>중개사보다 은행이 먼저다. 계약서를 쓰고 나서 대출이 안 나오면 계약금을 날린다. 매물마다
 * 취급 은행과 한도가 다르므로 계약 전에 확인해야 한다.
 *
 * <p>날짜 두 개가 성격이 다르다. 상담 착수일(FCT-104)은 심사에 2~3주가 걸린다는 경험치라
 * 권장이고, 신청 마감(FCT-103)은 넘기면 정책자금을 영구히 못 쓰는 절대 기한이다.
 */
@Component
class PreConsultAdvisor {

    /** 상담 착수 권장일 — 잔금일 D-21. */
    private static final String START_FACT = "FCT-104";
    /** 대출 신청 마감 — 잔금일·전입일 중 빠른 날 + 3개월. */
    private static final String DEADLINE_FACT = "FCT-103";

    private final FactRegistry facts;

    PreConsultAdvisor(FactRegistry facts) {
        this.facts = facts;
    }

    PreConsultGuide advise(LeaseContract contract) {
        boolean consulted = contract != null && contract.getBankConsultedAt() != null;
        LocalDate balanceDate = contract == null ? null : contract.getBalanceDate();
        LocalDate moveInDate = contract == null ? null : contract.getMoveInDate();

        LocalDate start = balanceDate == null
                ? null
                : balanceDate.minusDays(
                        facts.require(START_FACT).requireNumber().longValueExact());
        LocalDate deadline = deadline(balanceDate, moveInDate);

        if (consulted) {
            return new PreConsultGuide(
                    true,
                    start,
                    deadline,
                    "은행 사전상담을 %s 에 마쳤다.".formatted(contract.getBankConsultedAt()),
                    deadline == null ? null : "대출 신청은 %s 까지 끝내야 한다.".formatted(deadline),
                    List.of(START_FACT, DEADLINE_FACT));
        }
        if (balanceDate == null) {
            return new PreConsultGuide(
                    false,
                    null,
                    null,
                    "잔금일이 정해지지 않아 상담 시점을 계산할 수 없다.",
                    "계약서를 쓰기 전에 은행에서 이 매물로 대출이 되는지 먼저 확인한다.",
                    List.of(START_FACT));
        }
        return new PreConsultGuide(
                false,
                start,
                deadline,
                "잔금일 %s 기준으로 %s 부터는 은행 상담을 시작해야 한다.".formatted(balanceDate, start),
                "계약서를 쓰기 전에 은행에서 이 매물로 대출이 되는지 먼저 확인한다.",
                List.of(START_FACT, DEADLINE_FACT));
    }

    /** 잔금일과 전입일 중 빠른 날이 기준이다. 한쪽만 알면 그 날로 본다. */
    private LocalDate deadline(LocalDate balanceDate, LocalDate moveInDate) {
        LocalDate base = earlier(balanceDate, moveInDate);
        if (base == null) {
            return null;
        }
        return base.plusMonths(facts.require(DEADLINE_FACT).requireNumber().longValueExact());
    }

    private LocalDate earlier(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }
}
