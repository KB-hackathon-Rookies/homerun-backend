package com.homerun.domain.plan.service;

import com.homerun.domain.member.dto.response.DiagnosisProfileResponse;
import com.homerun.domain.member.service.MemberService;
import com.homerun.domain.plan.dto.response.PlanProfilePrefillResponse;
import com.homerun.domain.plan.dto.response.PlanProfilePrefillResponse.PrefillValue;
import com.homerun.domain.plan.dto.response.PlanProfilePrefillResponse.Source;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.PlanInputUnknownField;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanProfilePrefillService {
    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final MemberService members;

    public PlanProfilePrefillService(PlanRepository plans, PlanInputRepository inputs, MemberService members) {
        this.plans = plans;
        this.inputs = inputs;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public PlanProfilePrefillResponse get(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        DiagnosisProfileResponse profile = members.getDiagnosisProfile(memberId);
        PlanInput input = inputs.findByPlanId(planId).orElse(null);
        return new PlanProfilePrefillResponse(
                planId,
                select(
                        input,
                        PlanInputUnknownField.BIRTH_DATE,
                        input == null ? null : input.getBirthDate(),
                        profile.birthDate()),
                select(
                        input,
                        PlanInputUnknownField.MILITARY_MONTHS,
                        input == null ? null : input.getMilitaryMonths(),
                        profile.militaryMonths()));
    }

    private <T> PrefillValue<T> select(PlanInput input, PlanInputUnknownField field, T saved, T profile) {
        if (input != null && input.getUnknownFields().contains(field))
            return new PrefillValue<>(null, Source.EXPLICIT_UNKNOWN);
        if (saved != null) return new PrefillValue<>(saved, Source.SAVED_INPUT);
        if (profile != null) return new PrefillValue<>(profile, Source.MEMBER_PROFILE);
        return new PrefillValue<>(null, Source.UNAVAILABLE);
    }
}
