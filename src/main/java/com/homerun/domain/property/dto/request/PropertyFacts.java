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
 * @param nonResidential 근린생활시설(비주거)인가. 근생은 모든 전세 상품이 불가다(BR-09)
 * @param landlordTaxUnpaid 임대인 체납이 있는가
 * @param hopeDeposit 1루에서 정한 희망예산(원). 실제 매물 보증금({@code deposit})과 별개다.
 *     null 이면 예산을 확인하지 못한 것이라 예산 초과 판정을 하지 않는다
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
        LocalDate seniorDebtRegisteredAt,
        Boolean nonResidential,
        Long hopeDeposit) {

    /** 희망예산을 아직 넘기지 않는 호출부(테스트 픽스처 등)를 위한 편의 생성자. */
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
            Boolean landlordTaxUnpaid,
            Boolean leaseholdRegistered,
            Boolean seizureOrDispositionRestricted,
            Boolean auctionInProgress,
            LocalDate seniorDebtRegisteredAt,
            Boolean nonResidential) {
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
                leaseholdRegistered,
                seizureOrDispositionRestricted,
                auctionInProgress,
                seniorDebtRegisteredAt,
                nonResidential,
                null);
    }

    /** nonResidential 이전 자리수(등기 위험 포함)를 위한 편의 생성자. */
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
            Boolean landlordTaxUnpaid,
            Boolean leaseholdRegistered,
            Boolean seizureOrDispositionRestricted,
            Boolean auctionInProgress,
            LocalDate seniorDebtRegisteredAt) {
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
                leaseholdRegistered,
                seizureOrDispositionRestricted,
                auctionInProgress,
                seniorDebtRegisteredAt,
                null);
    }

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
