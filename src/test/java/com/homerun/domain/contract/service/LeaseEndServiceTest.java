package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.contract.dto.request.DepositReturnRecordRequest;
import com.homerun.domain.contract.dto.request.LeaseEndRequest;
import com.homerun.domain.contract.dto.response.LeaseEndResponse;
import com.homerun.domain.contract.entity.LeaseEnd;
import com.homerun.domain.contract.repository.LeaseEndRepository;
import com.homerun.domain.contract.type.DepositReturnStatus;
import com.homerun.domain.contract.type.LeaseDecision;
import com.homerun.domain.contract.type.RenewalMethod;
import com.homerun.domain.contract.type.UnreturnedAction;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 청구권 사용 파생(청구권 선택 시 true)과 계획당 1건 덮어쓰기·미존재 조회를 본다. */
class LeaseEndServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long PLAN_ID = 10L;

    private final PlanRepository plans = mock(PlanRepository.class);
    private final LeaseEndRepository leaseEnds = mock(LeaseEndRepository.class);
    private final LeaseEndService service =
            new LeaseEndService(plans, leaseEnds, Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));

    private void owned() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(leaseEnds.save(any(LeaseEnd.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void should_markClaimRightUsed_whenClaimMethod() {
        owned();
        when(leaseEnds.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        LeaseEndResponse r =
                service.decide(MEMBER_ID, PLAN_ID, new LeaseEndRequest(LeaseDecision.RENEW, RenewalMethod.CLAIM, null));

        assertThat(r.claimRightUsed()).isTrue();
    }

    @Test
    void should_notMarkClaimRightUsed_forOtherMethods() {
        owned();
        when(leaseEnds.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThat(service.decide(
                                MEMBER_ID,
                                PLAN_ID,
                                new LeaseEndRequest(LeaseDecision.RENEW, RenewalMethod.IMPLIED, null))
                        .claimRightUsed())
                .isFalse();
        assertThat(service.decide(MEMBER_ID, PLAN_ID, new LeaseEndRequest(LeaseDecision.LEAVE, null, null))
                        .claimRightUsed())
                .isFalse();
    }

    @Test
    void should_overwriteExisting_whenDecidingAgain() {
        owned();
        LeaseEnd existing = new LeaseEnd(PLAN_ID);
        when(leaseEnds.findByPlanId(PLAN_ID)).thenReturn(Optional.of(existing));

        service.decide(MEMBER_ID, PLAN_ID, new LeaseEndRequest(LeaseDecision.LEAVE, null, null));

        assertThat(existing.getDecision()).isEqualTo(LeaseDecision.LEAVE);
    }

    @Test
    void should_throw_whenGettingUnsavedDecision() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(leaseEnds.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(MEMBER_ID, PLAN_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.LEASE_END_NOT_FOUND));
    }

    @Test
    void should_throw_whenNotOwner() {
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        assertThatThrownBy(() -> service.get(999L, PLAN_ID)).isInstanceOf(BusinessException.class);
    }

    @Test
    void should_recordDepositReturn_ontoExistingDecision() {
        owned();
        LeaseEnd existing = new LeaseEnd(PLAN_ID);
        when(leaseEnds.findByPlanId(PLAN_ID)).thenReturn(Optional.of(existing));

        var r = service.recordDepositReturn(
                MEMBER_ID,
                PLAN_ID,
                new DepositReturnRecordRequest(
                        DepositReturnStatus.NO, 0L, 0L, UnreturnedAction.LEASEHOLD_REGISTRATION));

        assertThat(existing.getDepositReturned()).isEqualTo(DepositReturnStatus.NO);
        assertThat(existing.getUnreturnedAction()).isEqualTo(UnreturnedAction.LEASEHOLD_REGISTRATION);
        assertThat(r.depositReturned()).isEqualTo(DepositReturnStatus.NO);
    }

    @Test
    void should_throw_whenRecordingReturnWithoutDecision() {
        // 갱신·퇴거 결정이 먼저 있어야 반환 결과를 기록한다.
        when(plans.findById(PLAN_ID)).thenReturn(Optional.of(Plan.create(MEMBER_ID, LeaseType.JEONSE, null)));
        when(leaseEnds.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordDepositReturn(
                        MEMBER_ID, PLAN_ID, new DepositReturnRecordRequest(DepositReturnStatus.YES, null, null, null)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.LEASE_END_NOT_FOUND));
    }
}
