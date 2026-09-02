package com.homerun.domain.rent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 상호배타 판정 입력.
 *
 * @param eligibleSupports 자격 판정에서 받을 수 있다고 나온 지원금들. 비어 있어도 된다
 */
@Schema(description = "수급 가능한 지원금 목록")
public record RentSupportResolveRequest(@NotNull @Valid List<RentSupportOption> eligibleSupports) {

    public RentSupportResolveRequest {
        eligibleSupports = eligibleSupports == null ? List.of() : List.copyOf(eligibleSupports);
    }
}
