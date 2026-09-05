package com.homerun.domain.policy.dto.response;

import com.homerun.domain.policy.type.PolicyVerdictResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record JeonsePolicyVerdictListResponse(
        Long planId,
        List<PolicyVerdictResponse> results,
        Instant evaluatedAt,

        @Schema(description = "전세대출 판정 전용. PASS/NEED_INFO 정책과 은행 상담 안내. 실패 사유는 results에서 확인")
        List<JeonseLoanCardResponse> cards) {
    public JeonsePolicyVerdictListResponse(Long planId, List<PolicyVerdictResponse> results, Instant evaluatedAt) {
        this(planId, results, evaluatedAt, List.of());
    }

    /**
     * 카드만 예상 혜택·총비용 순으로 정렬한다(POL-02-01). {@code results} 는 판정 기록 원본이라
     * 정책 코드 순서를 그대로 둔다 — 감사 로그 성격이다.
     *
     * <p>은행 상담 카드는 정책이 아니라 안내라서 정렬에 넣지 않고 항상 맨 뒤에 붙인다.
     */
    public static JeonsePolicyVerdictListResponse loans(
            Long planId, List<PolicyVerdictResponse> results, Instant evaluatedAt, Long availableCash) {
        List<JeonseLoanCardResponse> cards = new ArrayList<>();
        results.stream()
                .filter(result -> result.verdict() == PolicyVerdictResult.PASS
                        || result.verdict() == PolicyVerdictResult.NEED_INFO)
                .map(result -> JeonseLoanCardResponse.policy(result, availableCash))
                .sorted(JeonseLoanCardResponse.BY_BENEFIT_AND_COST)
                .forEach(cards::add);
        cards.add(JeonseLoanCardResponse.bankConsultation());
        return new JeonsePolicyVerdictListResponse(planId, results, evaluatedAt, List.copyOf(cards));
    }
}
