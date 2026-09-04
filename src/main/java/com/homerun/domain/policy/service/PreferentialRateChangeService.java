package com.homerun.domain.policy.service;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.entity.PlanInputHistory;
import com.homerun.domain.plan.repository.PlanInputHistoryRepository;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.CompanySize;
import com.homerun.domain.policy.dto.response.PreferentialRateChange;
import com.homerun.domain.policy.dto.response.PreferentialRateChangeResponse;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 우대금리 승격 감지(POL-01-06). 직전 입력(plan_input_history의 가장 최근 revision)과 현재
 * plan_input 을 비교해 새로 우대 대상이 된 조건을 알린다.
 *
 * <p>버팀목 우대금리는 FCT-016~023 다섯 갈래인데 대부분은 지금 걷는 입력만으로 판단이 안 된다
 * — 단독세대주(FCT-018)는 가구원수, 신청금액 30%(FCT-020)는 은행 심사 산정액, 성실납부
 * (FCT-016)는 기존 대출자 전용이라 신규 대출 희망자에게 안 맞는다. 이번엔 FCT-019(중소기업
 * 취업·창업 청년, company_size 만 있으면 판단 가능)만 다룬다.
 */
@Service
public class PreferentialRateChangeService {

    private final PlanRepository planRepository;
    private final PlanInputRepository planInputRepository;
    private final PlanInputHistoryRepository planInputHistoryRepository;
    private final FactRegistry facts;

    public PreferentialRateChangeService(
            PlanRepository planRepository,
            PlanInputRepository planInputRepository,
            PlanInputHistoryRepository planInputHistoryRepository,
            FactRegistry facts) {
        this.planRepository = planRepository;
        this.planInputRepository = planInputRepository;
        this.planInputHistoryRepository = planInputHistoryRepository;
        this.facts = facts;
    }

    @Transactional(readOnly = true)
    public PreferentialRateChangeResponse detect(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        PlanInput current = planInputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));

        Optional<PlanInputHistory> previous = planInputHistoryRepository.findFirstByPlanIdOrderByRevisionDesc(planId);
        if (previous.isEmpty()) {
            // 수정 이력이 없으면 비교 대상이 없다 — 승격이라 부를 "이전"이 없다.
            return new PreferentialRateChangeResponse(planId, List.of());
        }

        List<PreferentialRateChange> changes = new ArrayList<>();
        detectYoungEmploymentPromotion(current, previous.get()).ifPresent(changes::add);

        return new PreferentialRateChangeResponse(planId, changes);
    }

    private Optional<PreferentialRateChange> detectYoungEmploymentPromotion(
            PlanInput current, PlanInputHistory previous) {
        boolean isNowEligible = isSmallOrStartup(current.getCompanySize());
        boolean wasEligible = isSmallOrStartup(previousCompanySize(previous));
        if (!isNowEligible || wasEligible) {
            return Optional.empty();
        }
        return resolveFact("FCT-019")
                .map(fact -> new PreferentialRateChange(
                        "YOUNG_EMPLOYMENT_DISCOUNT", fact.item(), fact.number(), fact.text(), fact.sourceUrl()));
    }

    private boolean isSmallOrStartup(CompanySize companySize) {
        return companySize == CompanySize.SMALL || companySize == CompanySize.STARTUP;
    }

    /** JSONB 스냅샷은 제네릭 {@code Map<String,Object>}라 enum이 저장 당시 이름 그대로의
     * 문자열로 돌아온다 — CompanySize로 바로 캐스팅하면 ClassCastException이다. */
    private CompanySize previousCompanySize(PlanInputHistory history) {
        Object value = history.getSnapshot().get("companySize");
        if (value == null) {
            return null;
        }
        try {
            return CompanySize.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Optional<Fact> resolveFact(String factCode) {
        try {
            return Optional.of(facts.require(factCode));
        } catch (FactNotFoundException | UnusableFactException e) {
            return Optional.empty();
        }
    }
}
