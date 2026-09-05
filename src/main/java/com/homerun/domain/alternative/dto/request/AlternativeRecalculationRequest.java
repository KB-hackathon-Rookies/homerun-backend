package com.homerun.domain.alternative.dto.request;

import com.homerun.domain.diagnosis.dto.request.DiagnosisCalculationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * 대안 재계산 입력(ALT-01-02).
 *
 * <p>{@code assumptions} 는 기준선과 대안 양쪽에 <b>똑같이</b> 쓴다. 그래야 결과 차이가 아래 축을
 * 바꾼 것 때문이라고 말할 수 있다.
 *
 * @param hopeDeposit 바꿔 볼 희망 보증금. 생략하면 계획 입력값을 그대로 쓴다
 * @param targetMoveDate 바꿔 볼 독립 희망일. 생략하면 계획의 목표 이사일을 그대로 쓴다
 */
@Schema(description = "대안 재계산 입력. 저축액은 assumptions 의 월 생활비로 조정한다")
public record AlternativeRecalculationRequest(
        @Valid @NotNull(message = "비용 가정이 필요하다") @Schema(description = "기준선과 대안에 공통으로 적용할 비용 가정")
        DiagnosisCalculationRequest assumptions,

        @PositiveOrZero(message = "희망 보증금은 0원 이상이어야 한다")
        @Schema(description = "바꿔 볼 희망 보증금. 함께 expectedLoanAmount 도 새로 계산해 넘겨야 한다", example = "180000000")
        Long hopeDeposit,

        @Schema(description = "바꿔 볼 독립 희망일", example = "2027-03-01")
        LocalDate targetMoveDate) {}
