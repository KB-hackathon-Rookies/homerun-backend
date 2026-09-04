package com.homerun.domain.policy.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.policy.dto.response.ConditionBasisResponse;
import com.homerun.domain.policy.dto.response.JeonsePolicyVerdictListResponse;
import com.homerun.domain.policy.dto.response.LoanEstimateResponse;
import com.homerun.domain.policy.dto.response.PolicyVerdictResponse;
import com.homerun.domain.policy.dto.response.RejectionReasonResponse;
import com.homerun.domain.policy.entity.Policy;
import com.homerun.domain.policy.entity.PolicyRule;
import com.homerun.domain.policy.entity.PolicyVerdict;
import com.homerun.domain.policy.entity.RejectionReason;
import com.homerun.domain.policy.entity.VerdictBasis;
import com.homerun.domain.policy.model.ConditionResult;
import com.homerun.domain.policy.model.ExpectedEstimate;
import com.homerun.domain.policy.repository.PolicyRepository;
import com.homerun.domain.policy.repository.PolicyRuleRepository;
import com.homerun.domain.policy.repository.PolicyVerdictRepository;
import com.homerun.domain.policy.repository.RejectionReasonRepository;
import com.homerun.domain.policy.repository.VerdictBasisRepository;
import com.homerun.domain.policy.type.PolicyRuleStatus;
import com.homerun.domain.policy.type.PolicyVerdictResult;
import com.homerun.domain.policy.type.RejectionReasonCategory;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전세대출 정책 자격 판정. {@link #evaluate}는 plan_input 기반(청년/일반 버팀목, 서울시 청년
 * 임차보증금 이자지원), {@link #evaluateReturnGuarantees}는 매물 기반(HUG/HF/SGI 반환보증,
 * #89)이다. 예상 대출액·금리 계산까지 여기서 한다(#95 — 원래 하드코딩 서비스였던
 * {@code JeonseLoanDiagnosisService}(#80)를 흡수하며 옮겼다. 그 클래스는 삭제됐다).
 */
@Service
public class JeonsePolicyVerdictService {

    private static final String ENGINE_VERSION = "policy-rule-v1";

    private static final List<String> JEONSE_POLICY_CODES =
            List.of("JEONSE-YOUTH-BEOTIMMOK", "JEONSE-GENERAL-BEOTIMMOK", "JEONSE-SEOUL-INTEREST-SUPPORT");

    private static final List<String> RETURN_GUARANTEE_POLICY_CODES =
            List.of("RETURN-GUARANTEE-HUG", "RETURN-GUARANTEE-HF", "RETURN-GUARANTEE-SGI");

    private static final List<String> GUARANTEE_FEE_SUPPORT_POLICY_CODES = List.of("RETURN-GUARANTEE-FEE-SUPPORT");

    /** FAIL 조건 코드 → 대안으로 보여줄 정책 코드. 지금 구현된 4개 정책 범위 안에서만 채운다
     * (#93) — 맵에 없는 조건은 alternative 없이 사유만 남는다. */
    private static final Map<String, String> ALTERNATIVE_POLICY_BY_CONDITION = Map.of(
            "AGE_UPPER_BOUND", "JEONSE-GENERAL-BEOTIMMOK",
            "INCOME_CAP", "JEONSE-GENERAL-BEOTIMMOK",
            "NET_ASSET_CAP", "JEONSE-GENERAL-BEOTIMMOK",
            "DEPOSIT_CAP", "JEONSE-GENERAL-BEOTIMMOK",
            "PRICE_RATIO_126", "RETURN-GUARANTEE-SGI",
            "REQUIRES_HF_JEONSE_LOAN", "RETURN-GUARANTEE-HUG");

    /** FAIL 조건 코드 → 사용자/주택/한도 분류(POL-03-10, #108). 맵에 없는 조건(RULE_NOT_ACTIVE
     * 등 코드가 아닌 것)은 분류하지 않는다 — 없는 카테고리를 지어내지 않는다. */
    private static final Map<String, RejectionReasonCategory> REASON_CATEGORY_BY_CONDITION = Map.ofEntries(
            Map.entry("HOUSEHOLD_HOMELESS", RejectionReasonCategory.USER),
            Map.entry("HOUSEHOLDER_STATUS", RejectionReasonCategory.USER),
            Map.entry("NO_DUPLICATE_LOAN", RejectionReasonCategory.USER),
            Map.entry("AGE_UPPER_BOUND", RejectionReasonCategory.USER),
            Map.entry("INCOME_CAP", RejectionReasonCategory.USER),
            Map.entry("NET_ASSET_CAP", RejectionReasonCategory.USER),
            Map.entry("REQUIRES_HF_JEONSE_LOAN", RejectionReasonCategory.USER),
            Map.entry("INCOME_CAP_FEE_SUPPORT", RejectionReasonCategory.USER),
            Map.entry("NOT_VIOLATION_BUILDING", RejectionReasonCategory.HOUSE),
            Map.entry("NOT_MULTI_HOUSEHOLD", RejectionReasonCategory.HOUSE),
            Map.entry("AREA_CAP", RejectionReasonCategory.HOUSE),
            Map.entry("PRICE_RATIO_126", RejectionReasonCategory.HOUSE),
            Map.entry("DEPOSIT_CAP", RejectionReasonCategory.LIMIT));

    private final PlanRepository planRepository;
    private final PlanInputRepository planInputRepository;
    private final PropertyRepository propertyRepository;
    private final PolicyRepository policyRepository;
    private final PolicyRuleRepository policyRuleRepository;
    private final PolicyVerdictRepository policyVerdictRepository;
    private final VerdictBasisRepository verdictBasisRepository;
    private final RejectionReasonRepository rejectionReasonRepository;
    private final PolicyRuleEngine engine;
    private final Clock clock;

    public JeonsePolicyVerdictService(
            PlanRepository planRepository,
            PlanInputRepository planInputRepository,
            PropertyRepository propertyRepository,
            PolicyRepository policyRepository,
            PolicyRuleRepository policyRuleRepository,
            PolicyVerdictRepository policyVerdictRepository,
            VerdictBasisRepository verdictBasisRepository,
            RejectionReasonRepository rejectionReasonRepository,
            PolicyRuleEngine engine,
            Clock clock) {
        this.planRepository = planRepository;
        this.planInputRepository = planInputRepository;
        this.propertyRepository = propertyRepository;
        this.policyRepository = policyRepository;
        this.policyRuleRepository = policyRuleRepository;
        this.policyVerdictRepository = policyVerdictRepository;
        this.verdictBasisRepository = verdictBasisRepository;
        this.rejectionReasonRepository = rejectionReasonRepository;
        this.engine = engine;
        this.clock = clock;
    }

    /** 매물을 아직 못 정했을 때 — 사람 조건만 본 예상 판정(#100 이전과 동일하게 동작). */
    @Transactional
    public JeonsePolicyVerdictListResponse evaluate(Long memberId, Long planId) {
        return evaluate(memberId, planId, null);
    }

    /**
     * propertyId 를 주면 집 조건(위반건축물·다가구 등, #100)까지 같이 본다 — 기금대출은
     * 사람 조건과 집 조건을 둘 다 통과해야 최종 승인이라 매물이 정해진 뒤에는 이 형태로
     * 다시 판정해야 한다. propertyId 가 없으면 사람 조건만 본 예상 판정이다.
     */
    @Transactional
    public JeonsePolicyVerdictListResponse evaluate(Long memberId, Long planId, Long propertyId) {
        verifyJeonsePlan(memberId, planId);
        PlanInput input = planInputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));
        Property property = propertyId == null ? null : findOwnedProperty(planId, propertyId);

        List<PolicyVerdictResponse> results = JEONSE_POLICY_CODES.stream()
                .map(code -> evaluateOne(planId, code, input, property))
                .toList();

        return JeonsePolicyVerdictListResponse.loans(planId, results, Instant.now(clock), input.getAvailableCash());
    }

    /** 매물 기준 반환보증(HUG/HF/SGI) 판정. plan_input 은 필요 없다(#89). */
    @Transactional
    public JeonsePolicyVerdictListResponse evaluateReturnGuarantees(Long memberId, Long planId, Long propertyId) {
        verifyJeonsePlan(memberId, planId);
        Property property = findOwnedProperty(planId, propertyId);

        List<PolicyVerdictResponse> results = RETURN_GUARANTEE_POLICY_CODES.stream()
                .map(code -> evaluateOne(planId, code, null, property))
                .toList();

        return new JeonsePolicyVerdictListResponse(planId, results, Instant.now(clock));
    }

    /** 반환보증료 지원사업(GTE-01-04, #104) 판정. 소득 기준이라 매물이 아니라 plan_input 만
     * 본다. 기혼(신혼부부 포함)은 혼인 기간 데이터가 없어 항상 NEED_INFO 다. */
    @Transactional
    public JeonsePolicyVerdictListResponse evaluateGuaranteeFeeSupport(Long memberId, Long planId) {
        verifyJeonsePlan(memberId, planId);
        PlanInput input = planInputRepository
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_INPUT_NOT_FOUND));

        List<PolicyVerdictResponse> results = GUARANTEE_FEE_SUPPORT_POLICY_CODES.stream()
                .map(code -> evaluateOne(planId, code, input, null))
                .toList();

        return new JeonsePolicyVerdictListResponse(planId, results, Instant.now(clock));
    }

    private Property findOwnedProperty(Long planId, Long propertyId) {
        return propertyRepository
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
    }

    private void verifyJeonsePlan(Long memberId, Long planId) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        if (plan.getLeaseType() == LeaseType.WOLSE) {
            throw new BusinessException(ErrorCode.POLICY_JEONSE_PLAN_REQUIRED);
        }
    }

    private PolicyVerdictResponse evaluateOne(Long planId, String policyCode, PlanInput input, Property property) {
        Policy policy = policyRepository
                .findByCode(policyCode)
                .orElseThrow(() -> new IllegalStateException("시드에 있어야 할 정책이 없습니다: " + policyCode));

        Optional<PolicyRule> activeRule = policyRuleRepository.findFirstByPolicyIdAndStatusOrderByVersionDesc(
                policy.getId(), PolicyRuleStatus.ACTIVE);

        List<ConditionResult> conditionResults = activeRule.isEmpty()
                ? List.of(new ConditionResult(
                        "RULE_NOT_ACTIVE",
                        "정책 조건식 검수 대기",
                        "사람이 조건식을 검수해 ACTIVE 로 올려야 판정에 쓸 수 있습니다.",
                        null,
                        null,
                        policy.getDetailUrl()))
                : engine.evaluate(activeRule.get().getRuleJson(), input, property);

        PolicyVerdictResult verdict = aggregate(conditionResults);
        Long ruleId = activeRule.map(PolicyRule::getId).orElse(null);
        Long propertyId = property == null ? null : property.getId();
        ExpectedEstimate estimate = activeRule.isEmpty()
                ? ExpectedEstimate.empty()
                : engine.estimate(activeRule.get().getRuleJson(), conditionResults, input, verdict);

        PolicyVerdict saved = save(planId, policy.getId(), ruleId, propertyId, verdict, estimate, conditionResults);
        List<RejectionReasonResponse> rejectionReasons =
                saveRejectionReasons(saved.getId(), policy.getCode(), verdict, conditionResults);

        return new PolicyVerdictResponse(
                policy.getCode(),
                policy.getName(),
                saved.getVerdict(),
                toBasisResponses(conditionResults),
                rejectionReasons,
                LoanEstimateResponse.from(estimate));
    }

    private PolicyVerdict save(
            Long planId,
            Long policyId,
            Long ruleId,
            Long propertyId,
            PolicyVerdictResult verdict,
            ExpectedEstimate estimate,
            List<ConditionResult> conditions) {
        PolicyVerdict verdictEntity = policyVerdictRepository
                .findByPlanIdAndPolicyIdAndRuleId(planId, policyId, ruleId)
                .map(existing -> {
                    existing.reevaluate(
                            verdict, propertyId, estimate.estimatedLoanAmount(), estimate.rateMin(), ENGINE_VERSION);
                    verdictBasisRepository.deleteByVerdictId(existing.getId());
                    return existing;
                })
                .orElseGet(() -> PolicyVerdict.create(
                        planId,
                        policyId,
                        ruleId,
                        propertyId,
                        verdict,
                        estimate.estimatedLoanAmount(),
                        estimate.rateMin(),
                        ENGINE_VERSION));
        PolicyVerdict saved = policyVerdictRepository.save(verdictEntity);

        conditions.forEach(condition -> verdictBasisRepository.save(VerdictBasis.create(
                saved.getId(),
                condition.code(),
                condition.label(),
                condition.requiredText(),
                condition.isMet(),
                condition.factCode(),
                condition.sourceUrl())));

        return saved;
    }

    /** verdict 가 FAIL 일 때만 채운다. 재판정마다 기존 사유를 지우고 다시 쓴다(verdict_basis 와
     * 같은 패턴) — 이력이 아니라 최신 상태만 남긴다. */
    private List<RejectionReasonResponse> saveRejectionReasons(
            Long verdictId, String policyCode, PolicyVerdictResult verdict, List<ConditionResult> conditions) {
        rejectionReasonRepository.deleteByVerdictId(verdictId);
        if (verdict != PolicyVerdictResult.FAIL) {
            return List.of();
        }

        return conditions.stream()
                .filter(condition -> Boolean.FALSE.equals(condition.isMet()))
                .map(condition -> saveOneRejectionReason(verdictId, policyCode, condition))
                .toList();
    }

    private RejectionReasonResponse saveOneRejectionReason(
            Long verdictId, String policyCode, ConditionResult condition) {
        String alternativeCode = ALTERNATIVE_POLICY_BY_CONDITION.get(condition.code());
        Policy alternative = alternativeCode == null || alternativeCode.equals(policyCode)
                ? null
                : policyRepository.findByCode(alternativeCode).orElse(null);
        Long alternativeId = alternative == null ? null : alternative.getId();

        rejectionReasonRepository.save(
                RejectionReason.create(verdictId, condition.code(), condition.label(), alternativeId));

        return new RejectionReasonResponse(
                condition.code(),
                condition.label(),
                REASON_CATEGORY_BY_CONDITION.get(condition.code()),
                alternative == null ? null : alternative.getCode(),
                alternative == null ? null : alternative.getName());
    }

    private PolicyVerdictResult aggregate(List<ConditionResult> results) {
        if (results.stream().anyMatch(result -> Boolean.FALSE.equals(result.isMet()))) {
            return PolicyVerdictResult.FAIL;
        }
        if (results.stream().anyMatch(result -> result.isMet() == null)) {
            return PolicyVerdictResult.NEED_INFO;
        }
        return PolicyVerdictResult.PASS;
    }

    private List<ConditionBasisResponse> toBasisResponses(List<ConditionResult> results) {
        LocalDate today = LocalDate.now(clock);
        return results.stream()
                .map(result -> ConditionBasisResponse.from(result, today))
                .toList();
    }
}
