package com.homerun.domain.property.dto.request;

import com.homerun.domain.property.type.OfficialPriceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

@Schema(description = "매물 STEP 4 등기부·공시가격 확인. nullable 사실값은 '모르겠어요'를 뜻합니다.")
public record PropertyRegistryStepRequest(
        @Min(1) int expectedRevision,
        @PositiveOrZero Long officialPrice,
        @Min(2000) @Max(2100) Integer officialPriceYear,
        OfficialPriceSource officialPriceSource,
        @PositiveOrZero Long seniorDebt,
        Boolean ownerMatches,
        Boolean trustRegistered,
        Boolean leaseholdRegistered,
        Boolean seizureOrDispositionRestricted,
        Boolean auctionInProgress,
        LocalDate seniorDebtRegisteredAt,
        Boolean landlordTaxUnpaid) {

    @AssertTrue(message = "공시가격은 금액·기준연도·출처를 함께 입력해야 합니다")
    public boolean isOfficialPriceMetadataComplete() {
        boolean allMissing = officialPrice == null && officialPriceYear == null && officialPriceSource == null;
        boolean allPresent = officialPrice != null && officialPriceYear != null && officialPriceSource != null;
        return allMissing || allPresent;
    }
}
