package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.ContractDtos.SettlementGuide;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.plan.type.LeaseType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 잔금·전입 안내(PRP-02-05, PRP-02-06).
 *
 * <p>잔금 지급·전입신고·확정일자를 같은 날 처리해야 한다(FCT-107). 순서가 아니라 날짜가
 * 문제다. 전입신고를 하루 미루면 그날 잡힌 근저당이 임차인보다 앞선다.
 *
 * <p>대항력은 전입신고 다음날 0시에 생긴다(FCT-108). 이 하루는 아무리 서둘러도 없앨 수
 * 없고, 특약(FCT-116)으로만 막는다.
 */
@Component
class SettlementAdvisor {

    private static final String SAME_DAY_FACT = "FCT-107";
    private static final String PROTECTION_FACT = "FCT-108";
    private static final String TAX_CREDIT_FACT = "FCT-050";

    /**
     * 대항력이 생기기까지 걸리는 날. FCT-108 은 "다음날 0시"라는 문장이고 수치가 0(시)이라
     * 그대로 더할 수 없다. 문장이 뜻하는 하루를 여기서 상수로 둔다.
     */
    private static final int PROTECTION_DELAY_DAYS = 1;

    SettlementGuide advise(LeaseContract contract) {
        LocalDate balanceDate = contract == null ? null : contract.getBalanceDate();
        LocalDate moveIn = contract == null ? null : contract.getMoveInReportAt();
        LocalDate confirmed = contract == null ? null : contract.getConfirmedDateAt();

        LocalDate protectionAt = moveIn == null ? null : moveIn.plusDays(PROTECTION_DELAY_DAYS);
        LocalDate paid = contract == null ? null : contract.getBalancePaidAt();
        LocalDate moneyOutAt = paid != null ? paid : balanceDate;

        boolean sameDay =
                moveIn != null && confirmed != null && moneyOutAt != null && sameDate(moveIn, confirmed, moneyOutAt);
        int gapDays = gapDays(moneyOutAt, protectionAt);

        return new SettlementGuide(
                balanceDate,
                moveIn,
                confirmed,
                sameDay,
                protectionAt,
                gapDays,
                summary(moneyOutAt, moveIn, confirmed, sameDay, gapDays),
                action(moveIn, confirmed, moneyOutAt, sameDay),
                taxCreditNote(contract),
                List.of(SAME_DAY_FACT, PROTECTION_FACT, TAX_CREDIT_FACT));
    }

    private boolean sameDate(LocalDate first, LocalDate second, LocalDate third) {
        return first.equals(second) && second.equals(third);
    }

    /** 돈이 나간 날부터 대항력이 생기는 날까지 비는 기간. 계산할 수 없으면 0 이다. */
    private int gapDays(LocalDate moneyOutAt, LocalDate protectionAt) {
        if (moneyOutAt == null || protectionAt == null) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.DAYS.between(moneyOutAt, protectionAt));
    }

    private String summary(LocalDate moneyOutAt, LocalDate moveIn, LocalDate confirmed, boolean sameDay, int gapDays) {
        if (moneyOutAt == null) {
            return "잔금일이 정해지지 않았다.";
        }
        if (moveIn == null || confirmed == null) {
            return "잔금일은 %s 다. 전입신고와 확정일자를 같은 날 처리해야 보증금이 보호된다.".formatted(moneyOutAt);
        }
        if (sameDay) {
            return "잔금·전입신고·확정일자를 %s 로 맞췄다. 대항력은 다음날 0시에 생긴다.".formatted(moneyOutAt);
        }
        return "잔금 %s, 전입신고 %s, 확정일자 %s 로 날짜가 어긋나 %d일 동안 보호받지 못한다.".formatted(moneyOutAt, moveIn, confirmed, gapDays);
    }

    private String action(LocalDate moveIn, LocalDate confirmed, LocalDate moneyOutAt, boolean sameDay) {
        if (moveIn == null || confirmed == null || moneyOutAt == null) {
            return "잔금 당일에 주민센터에서 전입신고와 확정일자를 한 번에 처리한다.";
        }
        if (sameDay) {
            // 날짜를 맞춰도 하루는 비므로 여기서 끝내면 안 된다.
            return "잔금일 다음날까지 근저당 설정을 금지하는 특약이 있는지 확인한다.";
        }
        return "전입신고와 확정일자를 잔금일과 같은 날로 맞춘다.";
    }

    /** PRP-02-06. 전입신고를 대항력 문제로만 설명하면 월세 세입자가 놓치는 게 있다. */
    private String taxCreditNote(LeaseContract contract) {
        if (contract == null || contract.getMonthlyRent() <= 0 || contract.getLeaseType() == LeaseType.JEONSE) {
            return null;
        }
        long yearly = contract.getMonthlyRent() * 12;
        return "전입신고는 대항력뿐 아니라 월세 세액공제 요건이기도 하다. 연 월세 %,d원이 공제 대상에서 빠질 수 있다.".formatted(yearly);
    }
}
