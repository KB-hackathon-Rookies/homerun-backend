package com.homerun.domain.property.dto.request;

import com.homerun.domain.plan.type.LeaseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 매물 검증의 입력.
 *
 * <p>등기부·건축물대장·공시가격에서 읽어온 사실만 담는다. 판정은 하지 않는다.
 *
 * <p>Boolean 이 {@code null} 이면 "확인 못 함"이다. false 와 구분해야 한다. 모른다고 해서
 * 문제 없다고 볼 수는 없고, 그렇다고 불가로 단정해서도 안 된다(NFR-01-06).
 *
 * @param leaseType 전세·월세·반전세
 * @param deposit 보증금(원)
 * @param regionCode 법정동 코드. 소액임차인 기준이 지역별로 다르다
 * @param marketPrice 시세(원)
 * @param officialPrice 공시가격(원)
 * @param seniorDebt 선순위채권(원). 근저당 채권최고액 등
 * @param ownerMatches 등기부상 소유자와 계약 상대방이 같은가
 * @param violationBuilding 위반건축물인가
 * @param trustRegistered 신탁등기가 있는가
 * @param multiHousehold 다가구주택인가
 * @param landlordTaxUnpaid 임대인 체납이 있는가
 */
@Schema(description = "매물 검증 입력 — 조회로 확인한 사실")
public record PropertyFacts(
        @NotNull LeaseType leaseType,
        @Min(value = 0, message = "보증금은 0원 이상이어야 한다") long deposit,
        String regionCode,
        Long marketPrice,
        Long officialPrice,
        Long seniorDebt,
        Boolean ownerMatches,
        Boolean violationBuilding,
        Boolean trustRegistered,
        Boolean multiHousehold,
        Boolean landlordTaxUnpaid,
        Boolean leaseholdRegistered,
        Boolean seizureOrDispositionRestricted,
        Boolean auctionInProgress,
        LocalDate seniorDebtRegisteredAt) {

    public PropertyFacts(
            LeaseType leaseType,
            long deposit,
            String regionCode,
            Long marketPrice,
            Long officialPrice,
            Long seniorDebt,
            Boolean ownerMatches,
            Boolean violationBuilding,
            Boolean trustRegistered,
            Boolean multiHousehold,
            Boolean landlordTaxUnpaid) {
        this(
                leaseType,
                deposit,
                regionCode,
                marketPrice,
                officialPrice,
                seniorDebt,
                ownerMatches,
                violationBuilding,
                trustRegistered,
                multiHousehold,
                landlordTaxUnpaid,
                null,
                null,
                null,
                null);
    }
}
