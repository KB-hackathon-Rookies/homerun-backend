package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.PropertyComparisonRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.entity.BankConsultation;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyDecision;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyDecisionRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.property.type.TrafficLight;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PropertyDecisionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private final PlanRepository plans = mock(PlanRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final BankConsultationRepository consultations = mock(BankConsultationRepository.class);
    private final PropertyDecisionRepository decisions = mock(PropertyDecisionRepository.class);
    private final PropertyTrafficLightResolver trafficLights = mock(PropertyTrafficLightResolver.class);
    private final PropertyDecisionService service = new PropertyDecisionService(
            plans,
            properties,
            consultations,
            decisions,
            trafficLights,
            Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(trafficLights.forProperty(PLAN_ID, 1L)).thenReturn(TrafficLight.GREEN);
    }

    @Test
    void should_compareUpToThreeProperties_inRequestedOrder() {
        Property first = property(1L);
        Property second = property(2L);
        when(properties.findByIdAndPlanId(2L, PLAN_ID)).thenReturn(Optional.of(second));
        when(properties.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(first));

        var response = service.compare(MEMBER_ID, PLAN_ID, new PropertyComparisonRequest(List.of(2L, 1L)));

        assertThat(response.properties()).extracting("propertyId").containsExactly(2L, 1L);
    }

    @Test
    void should_rejectDuplicatePropertyComparison() {
        assertThatThrownBy(() -> service.compare(MEMBER_ID, PLAN_ID, new PropertyComparisonRequest(List.of(1L, 1L))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.PROPERTY_COMPARISON_DUPLICATE));
    }

    @Test
    void should_includeBestPossibleConsultationInComparison() {
        Property first = property(1L);
        Property second = property(2L);
        BankConsultation lower = new BankConsultation(PLAN_ID, 1L, consultationRequest(80_000_000L, "2.100"));
        BankConsultation higher = new BankConsultation(PLAN_ID, 1L, consultationRequest(100_000_000L, "3.100"));
        when(properties.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(first));
        when(properties.findByIdAndPlanId(2L, PLAN_ID)).thenReturn(Optional.of(second));
        when(consultations.findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(PLAN_ID, 1L))
                .thenReturn(List.of(lower, higher));

        var response = service.compare(MEMBER_ID, PLAN_ID, new PropertyComparisonRequest(List.of(1L, 2L)));

        assertThat(response.consultationSummaries().get(0).consultationCount()).isEqualTo(2);
        assertThat(response.consultationSummaries()
                        .get(0)
                        .bestPossibleConsultation()
                        .approvedLimit())
                .isEqualTo(100_000_000L);
    }

    @Test
    void should_saveMultipleConsultationsForSameProperty() {
        Property property = property(1L);
        when(properties.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(property));
        when(consultations.save(any(BankConsultation.class))).thenAnswer(invocation -> {
            BankConsultation consultation = invocation.getArgument(0);
            ReflectionTestUtils.setField(consultation, "id", 7L);
            ReflectionTestUtils.setField(consultation, "createdAt", Instant.parse("2026-09-05T00:00:00Z"));
            return consultation;
        });

        var response = service.addConsultation(MEMBER_ID, PLAN_ID, 1L, consultationRequest());

        assertThat(response.consultationId()).isEqualTo(7L);
        assertThat(response.approvedLimit()).isEqualTo(144_000_000L);
        assertThat(response.resultStatus()).isEqualTo(ConsultationResultStatus.POSSIBLE);
        assertThat(response.loanProduct()).isEqualTo(ConsultedLoanProduct.YOUTH_BEOTIMMOK);
    }

    @Test
    void should_saveNotHeardAnswers_withoutBlockingConsultationProgress() {
        Property property = property(1L);
        when(properties.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(property));
        when(consultations.save(any(BankConsultation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BankConsultationRequest request = new BankConsultationRequest(
                "국민은행",
                null,
                null,
                null,
                ConsultationResultStatus.NOT_HEARD,
                ConsultedLoanProduct.UNKNOWN,
                CollateralMethod.UNKNOWN,
                null,
                null,
                LocalDate.of(2026, 9, 5),
                null);

        var response = service.addConsultation(MEMBER_ID, PLAN_ID, 1L, request);

        assertThat(response.resultStatus()).isEqualTo(ConsultationResultStatus.NOT_HEARD);
        assertThat(response.loanProduct()).isEqualTo(ConsultedLoanProduct.UNKNOWN);
        assertThat(response.collateralMethod()).isEqualTo(CollateralMethod.UNKNOWN);
        assertThat(response.approvedLimit()).isNull();
    }

    @Test
    void should_rejectConsultationBeforePropertyIsGreen() {
        Property property = property(1L);
        when(properties.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(property));
        when(trafficLights.forProperty(PLAN_ID, 1L)).thenReturn(TrafficLight.YELLOW);

        assertThatThrownBy(() -> service.addConsultation(MEMBER_ID, PLAN_ID, 1L, consultationRequest()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.PROPERTY_CONSULTATION_NOT_READY));
    }

    @Test
    void should_selectPropertyAndConsultationTogether() {
        Property property = property(1L);
        BankConsultation consultation = new BankConsultation(PLAN_ID, 1L, consultationRequest());
        ReflectionTestUtils.setField(consultation, "id", 7L);
        when(properties.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(property));
        when(consultations.findByIdAndPlanIdAndPropertyId(7L, PLAN_ID, 1L)).thenReturn(Optional.of(consultation));
        when(decisions.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(decisions.save(any(PropertyDecision.class))).thenAnswer(invocation -> {
            PropertyDecision decision = invocation.getArgument(0);
            ReflectionTestUtils.setField(decision, "id", 9L);
            return decision;
        });

        var response = service.decide(MEMBER_ID, PLAN_ID, new PropertyDecisionRequest(1L, 7L));

        assertThat(property.isSelected()).isTrue();
        assertThat(response.consultation().consultationId()).isEqualTo(7L);
        verify(properties).clearSelection(PLAN_ID);
    }

    @Test
    void should_rejectDifficultConsultationAsFinalDecision() {
        Property property = property(1L);
        BankConsultationRequest request = new BankConsultationRequest(
                "국민은행",
                "봉천점",
                null,
                null,
                ConsultationResultStatus.DIFFICULT,
                ConsultedLoanProduct.UNKNOWN,
                CollateralMethod.UNKNOWN,
                null,
                null,
                LocalDate.of(2026, 9, 5),
                null);
        BankConsultation consultation = new BankConsultation(PLAN_ID, 1L, request);
        ReflectionTestUtils.setField(consultation, "id", 7L);
        when(properties.findByIdAndPlanId(1L, PLAN_ID)).thenReturn(Optional.of(property));
        when(consultations.findByIdAndPlanIdAndPropertyId(7L, PLAN_ID, 1L)).thenReturn(Optional.of(consultation));

        assertThatThrownBy(() -> service.decide(MEMBER_ID, PLAN_ID, new PropertyDecisionRequest(1L, 7L)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.BANK_CONSULTATION_NOT_SELECTABLE));
    }

    private BankConsultationRequest consultationRequest() {
        return consultationRequest(144_000_000L, "2.200");
    }

    private BankConsultationRequest consultationRequest(Long limit, String rate) {
        return new BankConsultationRequest(
                "국민은행",
                "봉천점",
                null,
                null,
                ConsultationResultStatus.POSSIBLE,
                ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                CollateralMethod.HUG_SAFE_JEONSE,
                limit,
                new BigDecimal(rate),
                LocalDate.of(2026, 9, 5),
                null);
    }

    private Property property(Long id) {
        Property property = Property.candidate(
                PLAN_ID,
                "1168010100",
                "서울 지번",
                "서울 도로명",
                "홈런빌라",
                "ROW_HOUSE",
                180_000_000L,
                null,
                null,
                0L,
                true,
                false,
                false,
                false,
                false,
                Instant.parse("2026-09-05T00:00:00Z"));
        ReflectionTestUtils.setField(property, "id", id);
        return property;
    }
}
