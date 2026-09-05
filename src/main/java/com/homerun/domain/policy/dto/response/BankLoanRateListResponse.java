package com.homerun.domain.policy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * 일반(비정책) 전세대출 금리 비교(POL-03-07).
 *
 * @param publishedAverage 은행연합회가 공시한 전체 평균(FCT-101). {@code banks} 의 산술평균이
 *     아니다 — 집계 대상 은행이 달라 값이 다르다. 화면에서도 다시 계산하지 않는다
 * @param caution 광고금리를 쓰면 안 된다는 안내(FCT-102)
 */
@Schema(description = "은행별 전세대출 공시 평균금리 비교. 대출 승인이나 한도 안내가 아니다")
public record BankLoanRateListResponse(
        @Schema(description = "금리가 낮은 순") List<BankLoanRateResponse> banks,
        BigDecimal publishedAverage,
        @Schema(description = "전체 평균의 원문 표현") String publishedAverageText,
        boolean publishedAverageProvisional,
        String caution) {}
