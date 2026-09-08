package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import com.homerun.domain.member.repository.MemberRepository;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.CheckResult;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;
import com.homerun.domain.property.type.TrafficLight;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PropertyConsultationIntegrationTest {

    @Autowired
    PropertyDecisionService service;

    @Autowired
    PropertyTrafficLightResolver trafficLights;

    @Autowired
    MemberRepository members;

    @Autowired
    PlanRepository plans;

    @Autowired
    PropertyRepository properties;

    @Autowired
    PropertyCheckRepository checks;

    private Long memberId;
    private Long planId;
    private Long propertyId;

    @BeforeEach
    void setUp() {
        Member member =
                members.save(Member.create(AuthProvider.KAKAO, "consultation-" + System.nanoTime(), null, "tester"));
        memberId = member.getId();
        planId = plans.save(Plan.create(memberId, LeaseType.JEONSE, null)).getId();
        Property property = Property.candidate(
                planId,
                "1168010100",
                "서울특별시 강남구 역삼동 123-4",
                "서울특별시 강남구 테헤란로 123",
                "홈런아파트",
                "APARTMENT",
                100_000_000L,
                200_000_000L,
                150_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false,
                Instant.now());
        ReflectionTestUtils.setField(property, "workflowStep", PropertyDiagnosisStep.COMPLETE);
        ReflectionTestUtils.setField(property, "workflowStatus", PropertyWorkflowStatus.READY_FOR_CONSULTATION);
        propertyId = properties.save(property).getId();
        checks.saveAll(List.of(
                pass("VIOLATION_BUILDING"),
                pass("NON_RESIDENTIAL"),
                pass("OWNER_MATCH"),
                pass("TRUST_REGISTRATION"),
                pass("REGISTRY_RESTRICTION")));
    }

    @Test
    void should_turnPropertyBlue_evenWhenUnknownAnswersAreSaved() {
        var saved = service.addConsultation(memberId, planId, propertyId, notHeard());

        assertThat(saved.resultStatus()).isEqualTo(ConsultationResultStatus.NOT_HEARD);
        assertThat(trafficLights.forProperty(planId, propertyId)).isEqualTo(TrafficLight.BLUE);
        assertThat(properties.findById(propertyId).orElseThrow().getWorkflowStatus())
                .isEqualTo(PropertyWorkflowStatus.CONSULTED);
    }

    @Test
    void should_persistFinalPropertyAndPossibleConsultationTogether() {
        var consultation = service.addConsultation(memberId, planId, propertyId, possible());

        var decision = service.decide(
                memberId, planId, new PropertyDecisionRequest(propertyId, consultation.consultationId()));

        assertThat(decision.consultation().approvedLimit()).isEqualTo(80_000_000L);
        assertThat(properties.findById(propertyId).orElseThrow().isSelected()).isTrue();
        assertThat(service.getDecision(memberId, planId).decisionId()).isEqualTo(decision.decisionId());
    }

    @Test
    void should_upsertConsultation_whenSameBankAndProduct() {
        // 같은 (은행 + 상품) 을 다시 저장하면 카드를 새로 만들지 않고 최신 값으로 덮어쓴다.
        service.addConsultation(memberId, planId, propertyId, possible());
        var updated = service.addConsultation(memberId, planId, propertyId, possibleWithLimit(90_000_000L));

        var list = service.consultations(memberId, planId, propertyId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).approvedLimit()).isEqualTo(90_000_000L);
        assertThat(updated.approvedLimit()).isEqualTo(90_000_000L);
    }

    @Test
    void should_keepSeparateCards_whenSameBankDifferentProduct() {
        // 같은 은행이라도 상품이 다르면 별도 카드로 남아 비교할 수 있다.
        service.addConsultation(memberId, planId, propertyId, possible());
        service.addConsultation(
                memberId, planId, propertyId, possibleWithProduct(ConsultedLoanProduct.YOUTH_BEOTIMMOK));

        assertThat(service.consultations(memberId, planId, propertyId)).hasSize(2);
    }

    private PropertyCheck pass(String code) {
        return new PropertyCheck(propertyId, code, code, CheckResult.PASS, null, null, Instant.now());
    }

    private BankConsultationRequest notHeard() {
        return new BankConsultationRequest(
                "국민은행",
                "역삼점",
                null,
                null,
                ConsultationResultStatus.NOT_HEARD,
                ConsultedLoanProduct.UNKNOWN,
                CollateralMethod.UNKNOWN,
                null,
                null,
                LocalDate.now(),
                "다시 문의 필요");
    }

    private BankConsultationRequest possible() {
        return possibleWith(ConsultedLoanProduct.BANK_LOAN, 80_000_000L);
    }

    private BankConsultationRequest possibleWithLimit(long approvedLimit) {
        return possibleWith(ConsultedLoanProduct.BANK_LOAN, approvedLimit);
    }

    private BankConsultationRequest possibleWithProduct(ConsultedLoanProduct product) {
        return possibleWith(product, 80_000_000L);
    }

    private BankConsultationRequest possibleWith(ConsultedLoanProduct product, long approvedLimit) {
        return new BankConsultationRequest(
                "국민은행",
                "역삼점",
                null,
                null,
                ConsultationResultStatus.POSSIBLE,
                product,
                CollateralMethod.HF,
                approvedLimit,
                new BigDecimal("3.200"),
                LocalDate.now(),
                null);
    }
}
