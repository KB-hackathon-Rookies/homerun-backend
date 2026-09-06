package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.response.PropertySearchGuideResponse;
import com.homerun.domain.region.entity.Region;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** FR-P0 매물 탐색 안내. 필터가 계획·팩트에서 오는지와 안내가 항상 제공되는지 본다. */
class PropertySearchGuideServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final PlanInputRepository inputs = mock(PlanInputRepository.class);
    private final RegionRepository regions = mock(RegionRepository.class);
    private final FactRegistry facts = mock(FactRegistry.class);
    private final PropertySearchGuideService service = new PropertySearchGuideService(plans, inputs, regions, facts);

    private void givenPlan() {
        when(plans.findById(PLAN_ID))
                .thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 12, 1))));
    }

    private PlanInput input(Long deposit, Long regionId) {
        PlanInput input = mock(PlanInput.class);
        when(input.getHopeDeposit()).thenReturn(deposit);
        when(input.getRegionId()).thenReturn(regionId);
        return input;
    }

    @Test
    void should_buildFilterFromPlanAndFact() {
        givenPlan();
        PlanInput input = input(180_000_000L, 5L);
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        Region seoul = mock(Region.class);
        when(seoul.getName()).thenReturn("서울특별시 관악구");
        when(regions.findById(5L)).thenReturn(Optional.of(seoul));
        when(facts.require("FCT-005"))
                .thenReturn(new Fact("FCT-005", "전용면적", new BigDecimal("85"), "㎡", "85㎡ 이하", "url", false));

        PropertySearchGuideResponse.Filter filter =
                service.forPlan(MEMBER_ID, PLAN_ID).filter();

        assertThat(filter.depositCeiling()).isEqualTo(180_000_000L);
        assertThat(filter.areaCeilingM2()).isEqualByComparingTo("85");
        assertThat(filter.regionName()).isEqualTo("서울특별시 관악구");
        assertThat(filter.houseTypes()).containsExactly("APARTMENT", "OFFICETEL", "VILLA");
    }

    @Test
    void should_readAreaCeilingFromFact_notHardcoded() {
        // 면적 상한이 팩트에서 온다는 것을 못 박는다. 시드가 바뀌면 안내도 바뀌어야 한다.
        givenPlan();
        PlanInput input = input(180_000_000L, null);
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.of(input));
        when(facts.require("FCT-005"))
                .thenReturn(new Fact("FCT-005", "전용면적", new BigDecimal("60"), "㎡", "60㎡", "url", false));

        assertThat(service.forPlan(MEMBER_ID, PLAN_ID).filter().areaCeilingM2()).isEqualByComparingTo("60");
    }

    @Test
    void should_stillGiveSitesAndWarnings_whenPlanInputMissing() {
        // 입력이 아직 없어도 링크·경고는 유효하다. 필터 값만 비운다.
        givenPlan();
        when(inputs.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(facts.require("FCT-005"))
                .thenReturn(new Fact("FCT-005", "전용면적", new BigDecimal("85"), "㎡", "85㎡ 이하", "url", false));

        PropertySearchGuideResponse response = service.forPlan(MEMBER_ID, PLAN_ID);

        assertThat(response.filter().depositCeiling()).isNull();
        assertThat(response.sites()).isNotEmpty();
        assertThat(response.warnings()).hasSize(4);
        assertThat(response.siteHint()).contains("여러 사이트");
    }

    @Test
    void should_throw_whenRequesterIsNotOwner() {
        givenPlan();
        assertThatThrownBy(() -> service.forPlan(999L, PLAN_ID)).isInstanceOf(BusinessException.class);
    }
}
