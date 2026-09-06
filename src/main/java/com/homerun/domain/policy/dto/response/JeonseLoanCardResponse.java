package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;

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

    /**
     * 후보 정책 정렬 기준(POL-02-01). 혜택과 비용을 한 점수로 합치지 않는다 — 그러려면 문서에
     * 없는 가중치를 지어내야 한다. 대신 설명 가능한 사전식 순서를 쓴다.
     *
     * <ol>
     *   <li>PASS → NEED_INFO. 추가 확인이 필요한 건 아직 실행할 수 없다
     *   <li>자기자금 부족액 오름차순. 부족액이 적을수록 실제로 실행 가능하다(예상 혜택)
     *   <li>월 이자 오름차순(총비용)
     *   <li>예상 대출액 내림차순. 같은 비용이면 더 많이 빌려주는 쪽
     * </ol>
     *
     * <p>모르는 값(null)은 뒤로 보낸다. 유리한 기본값을 채우면 그게 곧 틀린 추천이 된다(NFR-01-06).
     *
     * <p>정책 코드로 마지막 tie-break 를 걸지 않는다 — 전부 동률이면 입력 순서가 그대로 남아야
     * 큐레이션된 기본 순서(청년→일반→서울시)가 알파벳순으로 흔들리지 않는다. 입력 순서가 고정된
     * 목록이고 정렬이 안정적이라 결정론(NFR-01-01)은 이것으로 이미 보장된다.
     */
    public static final Comparator<JeonseLoanCardResponse> BY_BENEFIT_AND_COST = Comparator.comparingInt(
                    JeonseLoanCardResponse::verdictPriority)
            .thenComparing(JeonseLoanCardResponse::ownFundsShortfall, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(JeonseLoanCardResponse::monthlyInterestMin, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(
                    JeonseLoanCardResponse::estimatedLoanAmount, Comparator.nullsLast(Comparator.reverseOrder()));

    private int verdictPriority() {
        if (verdict == PolicyVerdictResult.PASS) {
            return 0;
        }
        return verdict == PolicyVerdictResult.NEED_INFO ? 1 : 2;
    }

    private Long monthlyInterestMin() {
        return estimate == null ? null : estimate.monthlyInterestMin();
    }

    private Long estimatedLoanAmount() {
        return estimate == null ? null : estimate.estimatedLoanAmount();
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
        // 2루에서는 은행을 특정하지 않는다(FR-D3-05·BR-05). 은행명은 2-6 상담 결과 입력
        // 후에만 붙는다 — 여기서 특정 은행을 박으면 사용자가 그 은행만 가야 하는 것으로 읽는다.
        return new JeonseLoanCardResponse(
                "GENERAL-JEONSE-LOAN",
                "일반 전세대출",
                CardType.CONSULTATION,
                null,
                null,
                null,
                null,
                "은행 전세대출도 이용할 수 있어요. 한도·금리는 담보 방식(HF·HUG·SGI)과 개인 조건에 따라 달라지니 은행에서 상담받아보세요.");
    }
}
