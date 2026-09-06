package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.dto.response.RentTransactions;
import com.homerun.domain.house.service.HouseAnalysisService;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.DataSource;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.external.building.BuildingLedgerResponse;
import com.homerun.global.external.building.BuildingRegisterResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PropertyCandidateServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final HouseAnalysisService houses = mock(HouseAnalysisService.class);
    private final PropertyVerificationService verifications = mock(PropertyVerificationService.class);
    private final PropertyTrafficLightResolver trafficLights = mock(PropertyTrafficLightResolver.class);
    private final PropertyCandidateService service = new PropertyCandidateService(
            plans,
            properties,
            houses,
            verifications,
            trafficLights,
            Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUpPlan() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
    }

    @Test
    void should_saveAndVerifyCandidate_with_automaticBuildingFacts() {
        when(houses.analyze(any())).thenReturn(houseAnalysis());
        when(properties.save(any(Property.class))).thenAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            ReflectionTestUtils.setField(property, "id", 77L);
            return property;
        });
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.PASS, List.of(), List.of()));

        var result = service.analyzeAndSave(MEMBER_ID, PLAN_ID, request());

        assertThat(result.propertyId()).isEqualTo(77L);
        assertThat(result.automaticFacts().violationBuilding()).isFalse();
        assertThat(result.automaticFacts().multiHousehold()).isFalse();
        verify(verifications).verifyAndRecord(any(), any());
    }

    @Test
    void should_flagNonResidential_when_ledgerMainPurposeIsNeighborhoodFacility() {
        // 근생빌라는 겉보기에 빌라와 같지만 모든 전세 상품이 불가다(FCT-120, BR-09).
        when(houses.analyze(any())).thenReturn(houseAnalysisNonResidential());
        stubSave(82L);
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.PASS, List.of(), List.of()));

        var result = service.analyzeAndSave(MEMBER_ID, PLAN_ID, request());

        assertThat(result.automaticFacts().nonResidential()).isTrue();
    }

    @Test
    void should_storeDomainVocabulary_not_realEstateApiVocabulary() {
        // 자동판별은 실거래 API 어휘(ROW_HOUSE)를 주지만 매물에는 도메인 어휘(VILLA)로 저장한다.
        // 어긋나면 HOUSE_TYPE 화이트리스트·1루 희망유형과 이름이 갈린다(#193).
        when(houses.analyze(any())).thenReturn(houseAnalysisRowHouse());
        stubSave(84L);
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.PASS, List.of(), List.of()));

        service.analyzeAndSave(MEMBER_ID, PLAN_ID, request());

        assertThat(savedProperty().getHouseType()).isEqualTo("VILLA");
    }

    @Test
    void should_confirmResidential_when_ledgerShowsApartment() {
        // 주거용이 확인되면 FALSE 다. null(모름)과 구분돼야 근생 확인 안내가 안 뜬다.
        when(houses.analyze(any())).thenReturn(houseAnalysis());
        stubSave(83L);
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.PASS, List.of(), List.of()));

        var result = service.analyzeAndSave(MEMBER_ID, PLAN_ID, request());

        assertThat(result.automaticFacts().nonResidential()).isFalse();
    }

    @Test
    void should_saveCandidate_when_planIsUnderTheFivePropertyCap() {
        // #179 이전에는 상한이 없었다. 이제 5건 미만이면 저장되고 5건이면 막힌다(FR-P1-03).
        when(properties.countByPlanId(PLAN_ID)).thenReturn(4L);
        when(houses.analyze(any())).thenReturn(houseAnalysis());
        when(properties.save(any(Property.class))).thenAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            ReflectionTestUtils.setField(property, "id", 78L);
            return property;
        });
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.UNKNOWN, List.of(), List.of()));

        assertThat(service.analyzeAndSave(MEMBER_ID, PLAN_ID, request()).propertyId())
                .isEqualTo(78L);
        verify(houses).analyze(any());
    }

    @Test
    void should_rejectSixthCandidate_becauseComparisonCapsAtFive() {
        when(properties.countByPlanId(PLAN_ID)).thenReturn(5L);

        assertThatThrownBy(() -> service.analyzeAndSave(MEMBER_ID, PLAN_ID, request()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.PROPERTY_LIMIT_EXCEEDED));
        // 상한에 걸리면 외부 조회를 아예 하지 않는다 — 막을 거면 돈·시간을 쓰기 전에 막는다.
        verify(houses, never()).analyze(any());
    }

    @Test
    void should_takeAreaFromMatchedTransaction_when_realEstateMatchExists() {
        when(houses.analyze(any())).thenReturn(houseAnalysisWithMatch("42.35"));
        stubSave(79L);
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.PASS, List.of(), List.of()));

        service.analyzeAndSave(MEMBER_ID, PLAN_ID, request());

        Property saved = savedProperty();
        assertThat(saved.getExclusiveArea()).isEqualByComparingTo("42.35");
        assertThat(saved.getAreaSource()).isEqualTo(DataSource.AUTO);
        assertThat(saved.getPriceMatched()).isTrue();
    }

    @Test
    void should_fallBackToTypedArea_when_noTransactionMatched() {
        // 매칭이 없으면 사용자가 적은 값을 쓴다(FR-P1-10).
        when(houses.analyze(any())).thenReturn(houseAnalysis());
        stubSave(80L);
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.PASS, List.of(), List.of()));

        service.analyzeAndSave(MEMBER_ID, PLAN_ID, requestWithArea(new java.math.BigDecimal("59.75")));

        Property saved = savedProperty();
        assertThat(saved.getExclusiveArea()).isEqualByComparingTo("59.75");
        assertThat(saved.getAreaSource()).isEqualTo(DataSource.MANUAL);
        assertThat(saved.getPriceMatched()).isFalse();
    }

    @Test
    void should_leaveAreaNull_when_neitherMatchedNorTyped() {
        // 0 으로 채우면 85㎡ 이하로 통과해 버린다. 모르는 것은 모르는 채로 둔다.
        when(houses.analyze(any())).thenReturn(houseAnalysis());
        stubSave(81L);
        when(verifications.verifyAndRecord(any(), any()))
                .thenReturn(new PropertyVerification(CheckResult.PASS, List.of(), List.of()));

        service.analyzeAndSave(MEMBER_ID, PLAN_ID, request());

        Property saved = savedProperty();
        assertThat(saved.getExclusiveArea()).isNull();
        assertThat(saved.getAreaSource()).isNull();
    }

    /** save() 가 받은 엔티티를 그대로 돌려주도록 스텁한다 — 서비스가 무엇을 채웠는지 보려는 것이다. */
    private void stubSave(long id) {
        when(properties.save(any(Property.class))).thenAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            ReflectionTestUtils.setField(property, "id", id);
            return property;
        });
    }

    /** 실제로 저장된 매물을 꺼낸다. */
    private Property savedProperty() {
        ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(properties).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_keepOnlyRequestedCandidateSelected() {
        Property first = candidate(1L);
        first.select();
        Property second = candidate(2L);
        when(properties.existsByIdAndPlanId(2L, PLAN_ID)).thenReturn(true);
        when(properties.findByIdAndPlanId(2L, PLAN_ID)).thenReturn(Optional.of(second));

        var selected = service.select(MEMBER_ID, PLAN_ID, 2L);

        assertThat(second.isSelected()).isTrue();
        assertThat(selected.propertyId()).isEqualTo(2L);
        verify(properties).clearSelection(PLAN_ID);
    }

    private PropertyCandidateAnalysisRequest request() {
        return new PropertyCandidateAnalysisRequest(houseRequest(), 200_000_000L, null, null, 0L, true, false, false);
    }

    private HouseAnalysisRequest houseRequest() {
        return new HouseAnalysisRequest(
                "1168010100", false, "123", "4", "서울특별시 강남구 테헤란로 123", "서울특별시 강남구 역삼동 123-4", "홈런아파트", null, "202608");
    }

    private PropertyCandidateAnalysisRequest requestWithArea(java.math.BigDecimal area) {
        return new PropertyCandidateAnalysisRequest(
                houseRequest(), 200_000_000L, null, null, 0L, true, false, false, null, null, null, null, "401호", area);
    }

    /** 실거래 매칭이 하나 있는 응답. excluUseAr 는 국토부 실거래가 응답의 전용면적 키다. */
    private HouseAnalysisResponse houseAnalysisWithMatch(String area) {
        var titles = new BuildingRegisterResponse(
                "00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "공동주택(아파트)", "violBldYn", "N")));
        var prices = new BuildingRegisterResponse("00", "OK", 0, List.of());
        return new HouseAnalysisResponse(
                houseRequest(),
                HouseType.APARTMENT,
                new BuildingLedgerResponse(titles, prices),
                new RentTransactions(true, 1, 1, null, List.of(Map.of("excluUseAr", area, "jibun", "123-4"))),
                List.of());
    }

    /** 주용도가 제2종 근린생활시설인 대장. 오피스텔로 자동판별은 되지만(기타용도) 근생이 잡혀야 한다. */
    private HouseAnalysisResponse houseAnalysisNonResidential() {
        var titles = new BuildingRegisterResponse(
                "00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "제2종근린생활시설", "etcPurps", "오피스텔", "violBldYn", "N")));
        var prices = new BuildingRegisterResponse("00", "OK", 0, List.of());
        return new HouseAnalysisResponse(
                houseRequest(),
                HouseType.OFFICETEL,
                new BuildingLedgerResponse(titles, prices),
                new RentTransactions(true, 0, 0, null, List.of()),
                List.of());
    }

    /** 자동판별이 연립다세대(실거래 API 어휘 ROW_HOUSE)로 나오는 대장. */
    private HouseAnalysisResponse houseAnalysisRowHouse() {
        var titles =
                new BuildingRegisterResponse("00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "연립주택", "violBldYn", "N")));
        var prices = new BuildingRegisterResponse("00", "OK", 0, List.of());
        return new HouseAnalysisResponse(
                houseRequest(),
                HouseType.VILLA,
                new BuildingLedgerResponse(titles, prices),
                new RentTransactions(true, 0, 0, null, List.of()),
                List.of());
    }

    private HouseAnalysisResponse houseAnalysis() {
        var titles = new BuildingRegisterResponse(
                "00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "공동주택(아파트)", "violBldYn", "N")));
        var prices = new BuildingRegisterResponse("00", "OK", 0, List.of());
        return new HouseAnalysisResponse(
                houseRequest(),
                HouseType.APARTMENT,
                new BuildingLedgerResponse(titles, prices),
                new RentTransactions(true, 0, 0, null, List.of()),
                List.of());
    }

    private Property candidate(Long id) {
        Property property = Property.candidate(
                PLAN_ID,
                "1168010100",
                "지번주소",
                "도로명주소",
                "홈런아파트",
                "APARTMENT",
                200_000_000L,
                null,
                null,
                0L,
                true,
                false,
                false,
                false,
                false,
                Instant.parse("2026-09-04T00:00:00Z"));
        ReflectionTestUtils.setField(property, "id", id);
        return property;
    }
}
