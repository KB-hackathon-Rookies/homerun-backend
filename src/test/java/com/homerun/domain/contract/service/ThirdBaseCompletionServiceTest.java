package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.request.ContractExecutionCompletionRequest;
import com.homerun.domain.contract.dto.request.ThirdBaseCompleteRequest;
import com.homerun.domain.contract.dto.response.RegistryComparisonResponse;
import com.homerun.domain.contract.dto.response.ThirdBaseCompleteResponse;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.type.ApplicationMethod;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.contract.type.RegistryComparisonStatus;
import com.homerun.domain.plan.dto.response.PlanProgressResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.service.PlanService;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.PlanStage;
import com.homerun.domain.plan.type.PlanStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThirdBaseCompletionServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LeaseContractRepository contracts = mock(LeaseContractRepository.class);
    private final RegistryComparisonService registries = mock(RegistryComparisonService.class);
    private final PlanService planService = mock(PlanService.class);
    private final ThirdBaseCompletionService service =
            new ThirdBaseCompletionService(plans, contracts, registries, planService);

    private Plan plan;

    @BeforeEach
    void setUp() {
        plan = Plan.create(MEMBER_ID, LeaseType.JEONSE, LocalDate.of(2026, 11, 20));
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    }

    @Test
    @DisplayName("잔금 지급·전입신고 완료일만 기존 계약에 기록한다")
    void should_record_execution_completion_without_overwriting_contract() {
        LeaseContract contract = new LeaseContract(PLAN_ID, LeaseType.WOLSE, 10_000_000L, 500_000L);
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.of(contract));
        LocalDate completedAt = LocalDate.now();

        service.recordExecutionCompletion(
                MEMBER_ID, PLAN_ID, new ContractExecutionCompletionRequest(completedAt, completedAt));

        assertThat(contract.getLeaseType()).isEqualTo(LeaseType.WOLSE);
        assertThat(contract.getMonthlyRent()).isEqualTo(500_000L);
        assertThat(contract.getBalancePaidAt()).isEqualTo(completedAt);
        assertThat(contract.getMoveInReportAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("잔금·전입신고·등기부 안전 대조가 끝나면 HOME 반환보증 작업으로 넘긴다")
    void should_complete_third_base_and_handoff_return_guarantee() {
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.of(completedContract(ContractCollateralMethod.HF)));
        when(registries.compare(MEMBER_ID, PLAN_ID)).thenReturn(safeComparison());
        when(planService.completeStep(eq(MEMBER_ID), eq(PLAN_ID), any(), any())).thenReturn(progress());

        ThirdBaseCompleteResponse response = service.complete(MEMBER_ID, PLAN_ID, new ThirdBaseCompleteRequest("1.0"));

        assertThat(response.completed()).isTrue();
        assertThat(response.homeHandoffs())
                .extracting(ThirdBaseCompleteResponse.HomeHandoff::taskCode)
                .containsExactly("RETURN_GUARANTEE_JOIN");
        verify(planService).completeStep(eq(MEMBER_ID), eq(PLAN_ID), any(), any());
    }

    @Test
    @DisplayName("잔금 또는 전입신고가 끝나지 않으면 3루를 완료할 수 없다")
    void should_reject_completion_when_execution_is_incomplete() {
        LeaseContract contract = completedContract(ContractCollateralMethod.HF);
        contract.overwrite(
                1L,
                LeaseType.JEONSE,
                100_000_000L,
                0L,
                0L,
                10_000_000L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 10, 1),
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 11, 1),
                false,
                LoanProductKind.FUND_YOUTH,
                ContractCollateralMethod.HF,
                ApplicationMethod.ONLINE,
                HouseType.APARTMENT);
        when(contracts.findByPlanId(PLAN_ID)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.complete(MEMBER_ID, PLAN_ID, new ThirdBaseCompleteRequest("1.0")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.THIRD_BASE_EXECUTION_INCOMPLETE);
        verifyNoInteractions(registries, planService);
    }

    @Test
    @DisplayName("잔금일 등기부가 안전하지 않으면 송금 완료 후에도 3루를 닫지 않는다")
    void should_reject_completion_when_registry_is_not_safe() {
        when(contracts.findByPlanId(PLAN_ID))
                .thenReturn(Optional.of(completedContract(ContractCollateralMethod.HUG_SAFE_JEONSE)));
        when(registries.compare(MEMBER_ID, PLAN_ID))
                .thenReturn(new RegistryComparisonResponse(
                        RegistryComparisonStatus.BLOCK, true, null, null, List.of("MORTGAGE_INCREASED"), "송금을 중단합니다."));

        assertThatThrownBy(() -> service.complete(MEMBER_ID, PLAN_ID, new ThirdBaseCompleteRequest("1.0")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.THIRD_BASE_REGISTRY_RECHECK_REQUIRED);
        verifyNoInteractions(planService);
    }

    private LeaseContract completedContract(ContractCollateralMethod collateralMethod) {
        LeaseContract contract = new LeaseContract(PLAN_ID, LeaseType.JEONSE, 100_000_000L, 0L);
        contract.overwrite(
                1L,
                LeaseType.JEONSE,
                100_000_000L,
                0L,
                0L,
                10_000_000L,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 11, 1),
                false,
                LoanProductKind.FUND_YOUTH,
                collateralMethod,
                ApplicationMethod.ONLINE,
                HouseType.APARTMENT);
        return contract;
    }

    private RegistryComparisonResponse safeComparison() {
        return new RegistryComparisonResponse(RegistryComparisonStatus.SAFE, false, null, null, List.of(), "안전합니다.");
    }

    private PlanProgressResponse progress() {
        return new PlanProgressResponse(
                PLAN_ID, PlanStage.HOME, PlanStage.HOME, PlanStatus.ACTIVE, null, 4, 5, 80, List.of());
    }
}
