package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.dto.response.RentTransactions;
import com.homerun.domain.house.service.HouseAnalysisService;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.global.external.building.BuildingLedgerResponse;
import com.homerun.global.external.building.BuildingRegisterResponse;
import com.homerun.global.external.realestate.HousingType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void should_allowSavingCandidate_withoutPlanLimit() {
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

    private HouseAnalysisResponse houseAnalysis() {
        var titles = new BuildingRegisterResponse(
                "00", "OK", 1, List.of(Map.of("mainPurpsCdNm", "공동주택(아파트)", "violBldYn", "N")));
        var prices = new BuildingRegisterResponse("00", "OK", 0, List.of());
        return new HouseAnalysisResponse(
                houseRequest(),
                HousingType.APARTMENT,
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
