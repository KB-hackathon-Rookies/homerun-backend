package com.homerun.domain.asset.dto.request;

import com.homerun.domain.asset.type.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

@Schema(description = "자산 처분 비교 입력. 비교하고 싶은 자산만 담는다")
public record AssetComparisonRequest(
        @Valid @NotEmpty(message = "비교할 자산을 하나 이상 넣어야 합니다") List<Entry> assets) {

    /**
     * @param assetType IRP, HOUSING_SUBSCRIPTION(주택청약) 중 하나
     * @param balance 현재 보유 잔액(원)
     * @param withdrawAmount 인출·해지해서 보증금으로 쓰려는 금액(원)
     */
    @Schema(description = "비교 대상 자산 하나")
    public record Entry(
            @NotNull(message = "자산 종류가 필요합니다") AssetType assetType,
            @PositiveOrZero(message = "잔액은 0원 이상이어야 합니다") Long balance,

            @NotNull(message = "인출·해지 예정액이 필요합니다") @PositiveOrZero(message = "인출·해지 예정액은 0원 이상이어야 합니다")
            Long withdrawAmount) {}
}
