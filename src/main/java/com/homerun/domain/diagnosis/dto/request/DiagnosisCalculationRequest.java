package com.homerun.domain.diagnosis.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 진단 계산 입력.
 *
 * <p>비용 항목은 전부 <b>생략할 수 있다</b>. 0 은 "확인해서 0원" 이라는 뜻이지 모른다는 뜻이 아니다.
 * 중개보수·인지세·보증료는 대출금이 정해져야 나오는 값이라 화면이 알 수 없으므로, 보내지 않으면
 * 서버가 {@code config_effective} 기준으로 계산한다. 계산할 근거가 없는 항목은 경고로 남긴다.
 */
public record DiagnosisCalculationRequest(
        @PositiveOrZero @Schema(description = "이사 비용. 생략하면 기본값(FCT-237)", example = "1000000")
        Long movingCost,

        @PositiveOrZero @Schema(description = "중개보수. 생략하면 보증금 구간 요율로 계산(BR-27)", example = "600000")
        Long brokerageFee,

        @PositiveOrZero @Schema(description = "반환보증 등 보증료. 생략하면 대출금 × 보증료율로 계산", example = "300000")
        Long guaranteeFee,

        @PositiveOrZero @Schema(description = "인지세 본인 부담액. 생략하면 대출금 구간으로 계산", example = "75000")
        Long stampTax,

        @PositiveOrZero @Schema(description = "입주 후 생활 예비비. 생략하면 0으로 두고 경고를 남긴다", example = "3000000")
        Long emergencyReserve,

        @PositiveOrZero @Schema(description = "월 생활비. 생략하면 0으로 두고 경고를 남긴다", example = "900000")
        Long monthlyLivingExpense,

        @PositiveOrZero @Schema(description = "월 대출 상환액. 생략하면 최근 오픈뱅킹 스냅샷을 사용하며, 조회값도 미확인 정보로 표시")
        Long monthlyDebtPayment,

        @PositiveOrZero @Schema(description = "정책 적용 예상 대출액", example = "120000000")
        long expectedLoanAmount,

        @PositiveOrZero @Schema(description = "예상 월 이자. 정책 카드의 월 이자 예상값을 사용", example = "275000")
        long expectedMonthlyInterest) {}
