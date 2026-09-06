package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.type.CancelFeasibility;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 계약 해제 대응 안내(FR-P8-03·04·05).
 *
 * @param feasibility 해제 가능성(계약 진행 단계 기준)
 * @param feasibilityNote 해제 가능성 설명
 * @param specialTermApplicable 특약이 있고 적용 가능한가(사유가 소득·신용·집이면 적용, 서류 미비면 과실 여지)
 * @param specialTermSteps 특약 발동 4단계 절차(특약 있을 때만)
 * @param remainingDayOptions 특약 없을 때 잔여일 대안. 전부 권장(RECOMMENDED)이며 법정 기한이 아니다
 */
@Schema(description = "계약 해제 대응 안내(FR-P8)")
public record ContractCancellationResponse(
        CancelFeasibility feasibility,
        String feasibilityNote,
        Boolean specialTermApplicable,
        List<String> specialTermSteps,
        List<String> remainingDayOptions) {

    public ContractCancellationResponse {
        specialTermSteps = List.copyOf(specialTermSteps);
        remainingDayOptions = List.copyOf(remainingDayOptions);
    }
}
