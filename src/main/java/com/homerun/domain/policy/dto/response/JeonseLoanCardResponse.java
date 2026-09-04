package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** 상담 안내는 정책 판정이나 대출 승인이 아니다. */
public record JeonseLoanCardResponse(
        String code,
        String name,
        CardType type,

        @Schema(description = "NEED_INFO는 추가 확인, 상담 카드는 null")
        PolicyVerdictResult verdict,

        LoanEstimateResponse estimate,

        @Schema(description = "사용 가능 현금. 순자산이나 계좌 잔액으로 대체하지 않음")
        Long availableCash,

        @Schema(description = "필요 자기자금 - 사용 가능 현금, 최소 0. 미확인 시 null")
        Long ownFundsShortfall,

        String notice) {

    public enum CardType {
        POLICY,
        CONSULTATION
    }

    public static JeonseLoanCardResponse policy(PolicyVerdictResponse result, Long availableCash) {
        Long required = result.estimate() == null ? null : result.estimate().ownFundsRequired();
        Long shortfall = required == null || availableCash == null ? null : Math.max(0, required - availableCash);
        return new JeonseLoanCardResponse(
                result.policyCode(),
                result.policyName(),
                CardType.POLICY,
                result.verdict(),
                result.estimate(),
                availableCash,
                shortfall,
                result.verdict() == PolicyVerdictResult.NEED_INFO
                        ? "추가 확인이 필요한 예상 결과입니다. 판정 근거를 확인해 주세요."
                        : "입력 기준 예상 결과이며 대출 승인이 아닙니다. 한도와 금리는 은행 심사에서 확정됩니다.");
    }

    public static JeonseLoanCardResponse bankConsultation() {
        return new JeonseLoanCardResponse(
                "KB-JEONSE-CONSULTATION",
                "국민은행 전세대출 상담",
                CardType.CONSULTATION,
                null,
                null,
                null,
                null,
                "은행 전세대출도 상담할 수 있어요. 이용 가능 여부와 한도·금리는 담보 방식과 개인 조건에 따라 은행에서 확인해 주세요.");
    }
}
