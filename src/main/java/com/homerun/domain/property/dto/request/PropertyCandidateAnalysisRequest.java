package com.homerun.domain.property.dto.request;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.property.type.LandlordConsent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "매물 후보 통합조회 및 안전성 판정 요청")
public record PropertyCandidateAnalysisRequest(
        @Valid @NotNull HouseAnalysisRequest house,
        @NotNull @PositiveOrZero Long deposit,
        @PositiveOrZero Long marketPrice,
        @PositiveOrZero Long officialPrice,
        @PositiveOrZero Long seniorDebt,
        Boolean ownerMatches,
        Boolean trustRegistered,
        Boolean landlordTaxUnpaid,
        Boolean leaseholdRegistered,
        Boolean seizureOrDispositionRestricted,
        Boolean auctionInProgress,
        LocalDate seniorDebtRegisteredAt,

        @Schema(description = "동·호수. 집합건물은 여기까지 정확해야 등기부가 맞다")
        String detailAddress,

        @Schema(description = "전용면적(㎡). 실거래 매칭이 없을 때만 쓴다. 매칭되면 조회값이 우선한다") @PositiveOrZero
        BigDecimal exclusiveArea,

        @Schema(description = "임대인 전세대출 협조 여부(FR-P1-07). 생략하면 NOT_ASKED 로 본다")
        LandlordConsent landlordConsent) {

    /** 주소 상세·전용면적을 직접 넣지 않는 호출부를 위한 편의 생성자. */
    public PropertyCandidateAnalysisRequest(
            HouseAnalysisRequest house,
            Long deposit,
            Long marketPrice,
            Long officialPrice,
            Long seniorDebt,
            Boolean ownerMatches,
            Boolean trustRegistered,
            Boolean landlordTaxUnpaid,
            Boolean leaseholdRegistered,
            Boolean seizureOrDispositionRestricted,
            Boolean auctionInProgress,
            LocalDate seniorDebtRegisteredAt) {
        this(
                house,
                deposit,
                marketPrice,
                officialPrice,
                seniorDebt,
                ownerMatches,
                trustRegistered,
                landlordTaxUnpaid,
                leaseholdRegistered,
                seizureOrDispositionRestricted,
                auctionInProgress,
                seniorDebtRegisteredAt,
                null,
                null,
                null);
    }

    public PropertyCandidateAnalysisRequest(
            HouseAnalysisRequest house,
            Long deposit,
            Long marketPrice,
            Long officialPrice,
            Long seniorDebt,
            Boolean ownerMatches,
            Boolean trustRegistered,
            Boolean landlordTaxUnpaid) {
        this(
                house,
                deposit,
                marketPrice,
                officialPrice,
                seniorDebt,
                ownerMatches,
                trustRegistered,
                landlordTaxUnpaid,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** 등록 시 실제로 쓰는 값(협조 여부 미지정이면 NOT_ASKED). */
    public LandlordConsent effectiveLandlordConsent() {
        return landlordConsent == null ? LandlordConsent.NOT_ASKED : landlordConsent;
    }
}
