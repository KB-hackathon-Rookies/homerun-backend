package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.response.PropertyRejectionResponse;
import com.homerun.domain.property.entity.BankConsultation;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultationResultStatus;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.property.type.RejectionCategory;
import com.homerun.domain.property.type.RejectionStage;
import com.homerun.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** FR-P8-01·02 매물별 거절 대응 종합. 사유 분류 묶기와 같은 사유 반복 감지를 본다. */
class PropertyRejectionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Long PROPERTY_ID = 100L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final BankConsultationRepository consultations = mock(BankConsultationRepository.class);
    private final PropertyRejectionService service =
            new PropertyRejectionService(plans, properties, consultations, new RejectionAdvisor());

    private void givenOwnedPropertyWith(BankConsultation... rejected) {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(properties.existsByIdAndPlanId(PROPERTY_ID, PLAN_ID)).thenReturn(true);
        when(consultations.findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(PLAN_ID, PROPERTY_ID))
                .thenReturn(List.of(rejected));
    }

    private BankConsultation consultation(RejectionCategory category, CollateralMethod collateral) {
        return new BankConsultation(
                PLAN_ID,
                PROPERTY_ID,
                new BankConsultationRequest(
                        "국민은행",
                        null,
                        null,
                        null,
                        ConsultationResultStatus.DIFFICULT,
                        ConsultedLoanProduct.BANK_LOAN,
                        collateral,
                        null,
                        null,
                        LocalDate.of(2026, 9, 1),
                        null,
                        RejectionStage.GUARANTEE,
                        category,
                        null));
    }

    private BankConsultation notRejected() {
        return new BankConsultation(
                PLAN_ID,
                PROPERTY_ID,
                new BankConsultationRequest(
                        "신한은행",
                        null,
                        null,
                        null,
                        ConsultationResultStatus.POSSIBLE,
                        ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                        CollateralMethod.HF,
                        null,
                        null,
                        LocalDate.of(2026, 9, 2),
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void should_groupDistinctCategories_andNotSuggestChange_whenNoRepeat() {
        givenOwnedPropertyWith(
                consultation(RejectionCategory.SUBJECT_ISSUE, CollateralMethod.HF),
                consultation(RejectionCategory.GUARANTEE_ISSUE, CollateralMethod.HUG_SAFE_JEONSE));

        PropertyRejectionResponse r = service.forProperty(MEMBER_ID, PLAN_ID, PROPERTY_ID);

        assertThat(r.guidances())
                .extracting("category")
                .containsExactlyInAnyOrder(RejectionCategory.SUBJECT_ISSUE, RejectionCategory.GUARANTEE_ISSUE);
        assertThat(r.repeatedCategories()).isEmpty();
        assertThat(r.suggestChangeProperty()).isFalse();
    }

    @Test
    void should_suggestChangeProperty_whenSameCategoryRepeats() {
        // 같은 사유(집 문제)가 두 은행에서 반복되면 매물을 바꾸는 게 낫다(FR-P8-02).
        givenOwnedPropertyWith(
                consultation(RejectionCategory.PROPERTY_ISSUE, CollateralMethod.HUG_SAFE_JEONSE),
                consultation(RejectionCategory.PROPERTY_ISSUE, CollateralMethod.SGI));

        PropertyRejectionResponse r = service.forProperty(MEMBER_ID, PLAN_ID, PROPERTY_ID);

        assertThat(r.repeatedCategories()).containsExactly(RejectionCategory.PROPERTY_ISSUE);
        assertThat(r.suggestChangeProperty()).isTrue();
        // 중복 분류는 하나로 묶는다.
        assertThat(r.guidances()).hasSize(1);
    }

    @Test
    void should_ignoreNonRejectedConsultations() {
        // 거절 분류가 없는 상담(POSSIBLE)은 대응 대상이 아니다.
        givenOwnedPropertyWith(notRejected(), consultation(RejectionCategory.DOCUMENT_ISSUE, CollateralMethod.HF));

        PropertyRejectionResponse r = service.forProperty(MEMBER_ID, PLAN_ID, PROPERTY_ID);

        assertThat(r.guidances()).hasSize(1);
        assertThat(r.guidances().get(0).category()).isEqualTo(RejectionCategory.DOCUMENT_ISSUE);
    }

    @Test
    void should_useLatestCollateralForGuaranteeGuidance() {
        // 최신순 첫 상담(HUG)의 담보로 안내한다 → SGI·HF 경로.
        givenOwnedPropertyWith(
                consultation(RejectionCategory.GUARANTEE_ISSUE, CollateralMethod.HUG_SAFE_JEONSE),
                consultation(RejectionCategory.GUARANTEE_ISSUE, CollateralMethod.SGI));

        PropertyRejectionResponse r = service.forProperty(MEMBER_ID, PLAN_ID, PROPERTY_ID);

        assertThat(r.guidances().get(0).alternatives()).anyMatch(a -> a.contains("SGI") && a.contains("HF"));
    }

    @Test
    void should_throw_whenNotOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));

        assertThatThrownBy(() -> service.forProperty(999L, PLAN_ID, PROPERTY_ID))
                .isInstanceOf(BusinessException.class);
    }
}
