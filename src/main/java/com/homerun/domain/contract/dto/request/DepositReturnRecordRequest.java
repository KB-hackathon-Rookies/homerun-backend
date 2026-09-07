package com.homerun.domain.contract.dto.request;

import com.homerun.domain.contract.type.DepositReturnStatus;
import com.homerun.domain.contract.type.UnreturnedAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 보증금 반환 결과 저장(FR-HX-01). 갱신·퇴거 결정이 먼저 저장돼 있어야 한다.
 *
 * @param depositReturned 반환 여부(YES/NO/PARTIAL)
 * @param returnAmountToBank 은행에 상환된 금액(원)
 * @param returnAmountToMe 내가 받은 금액(원)
 * @param unreturnedAction 미반환 시 조치(NO/PARTIAL 일 때). 없으면 null
 */
@Schema(description = "보증금 반환 결과(FR-HX-01)")
public record DepositReturnRecordRequest(
        @NotNull DepositReturnStatus depositReturned,
        @PositiveOrZero Long returnAmountToBank,
        @PositiveOrZero Long returnAmountToMe,
        UnreturnedAction unreturnedAction) {}
