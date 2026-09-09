package com.homerun.domain.property.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.PropertyComparisonRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.dto.response.BankConsultationResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyComparisonResponse;
import com.homerun.domain.property.dto.response.PropertyConsultationSummaryResponse;
import com.homerun.domain.property.dto.response.PropertyDecisionResponse;
import com.homerun.domain.property.entity.BankConsultation;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.entity.PropertyDecision;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyDecisionRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyDecisionService {

    /** V74 에서 만든 (plan_id, property_id, bank_name, loan_product) 유니크 제약 이름. */
    private static final String DUPLICATE_CONSULTATION_CONSTRAINT = "uq_bank_consultation_plan_property_bank_product";

    private final PlanRepository plans;
    private final PropertyRepository properties;
    private final BankConsultationRepository consultations;
    private final PropertyDecisionRepository decisions;
    private final PropertyTrafficLightResolver trafficLights;
    private final Clock clock;

    public PropertyDecisionService(
            PlanRepository plans,
            PropertyRepository properties,
            BankConsultationRepository consultations,
            PropertyDecisionRepository decisions,
            PropertyTrafficLightResolver trafficLights,
            Clock clock) {
        this.plans = plans;
        this.properties = properties;
        this.consultations = consultations;
        this.decisions = decisions;
        this.trafficLights = trafficLights;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PropertyComparisonResponse compare(Long memberId, Long planId, PropertyComparisonRequest request) {
        ownedPlan(memberId, planId);
        if (new HashSet<>(request.propertyIds()).size() != request.propertyIds().size()) {
            throw new BusinessException(ErrorCode.PROPERTY_COMPARISON_DUPLICATE);
        }
        List<Property> comparedProperties = request.propertyIds().stream()
                .map(propertyId -> ownedProperty(planId, propertyId))
                .toList();
        List<PropertyCandidateResponse> compared = comparedProperties.stream()
                .map(property ->
                        PropertyCandidateResponse.from(property, trafficLights.forProperty(planId, property.getId())))
                .toList();
        List<PropertyConsultationSummaryResponse> summaries = comparedProperties.stream()
                .map(property -> consultationSummary(planId, property.getId()))
                .toList();
        return new PropertyComparisonResponse(compared, summaries);
    }

    @Transactional
    public BankConsultationResponse addConsultation(
            Long memberId, Long planId, Long propertyId, BankConsultationRequest request) {
        ownedPlan(memberId, planId);
        Property property = ownedProperty(planId, propertyId);
        if (!trafficLights.forProperty(planId, propertyId).showsLoanProducts()) {
            throw new BusinessException(ErrorCode.PROPERTY_CONSULTATION_NOT_READY);
        }
        // 같은 (은행 + 상품) 상담은 카드를 새로 만들지 않고 최신 값으로 덮어쓴다(은행+상품당 1건).
        // 상품이 다르면 별도 카드로 남아 비교할 수 있다.
        BankConsultation consultation = consultations
                .findByPlanIdAndPropertyIdAndBankNameAndLoanProduct(
                        planId, propertyId, request.bankName(), request.loanProduct())
                .map(existing -> {
                    existing.applyUpdate(request);
                    return existing;
                })
                .orElseGet(() -> new BankConsultation(planId, propertyId, request));
        BankConsultationResponse response;
        try {
            response = BankConsultationResponse.from(consultations.saveAndFlush(consultation));
        } catch (DataIntegrityViolationException e) {
            // 조회와 저장 사이에 같은 키가 먼저 들어오면(더블클릭·동시 제출) INSERT 가 V74 유니크
            // 제약에 걸린다. 이 시점에는 트랜잭션이 이미 깨져 재조회로 덮어쓸 수 없으므로 409 로
            // 돌려보내 다시 저장하게 한다 -- 그때는 앞선 요청이 만든 카드를 찾아 덮어쓴다.
            // 무결성 오류를 전부 경합으로 바꾸면 없는 정책·보증기관을 넣은 요청까지 가려지므로
            // 제약 이름으로 갈라낸다.
            if (violates(e, DUPLICATE_CONSULTATION_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.CONCURRENT_UPDATE, e);
            }
            throw e;
        }
        property.markConsulted();
        return response;
    }

    private boolean violates(DataIntegrityViolationException e, String constraintName) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<BankConsultationResponse> consultations(Long memberId, Long planId, Long propertyId) {
        ownedPlan(memberId, planId);
        ownedProperty(planId, propertyId);
        return consultations.findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(planId, propertyId).stream()
                .map(BankConsultationResponse::from)
                .toList();
    }

    @Transactional
    public PropertyDecisionResponse decide(Long memberId, Long planId, PropertyDecisionRequest request) {
        ownedPlan(memberId, planId);
        Property property = ownedProperty(planId, request.propertyId());
        BankConsultation consultation = consultations
                .findByIdAndPlanIdAndPropertyId(request.consultationId(), planId, request.propertyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONSULTATION_PROPERTY_MISMATCH));
        if (!consultation.isSelectable()) {
            throw new BusinessException(ErrorCode.BANK_CONSULTATION_NOT_SELECTABLE);
        }
        PropertyDecision previous = decisions.findByPlanId(planId).orElse(null);
        if (previous != null
                && previous.getPropertyId().equals(request.propertyId())
                && previous.getConsultationId().equals(request.consultationId())) {
            return response(previous, property, consultation);
        }
        properties.clearSelection(planId);
        property = ownedProperty(planId, request.propertyId());
        property.select();
        Instant decidedAt = Instant.now(clock);
        PropertyDecision decision = previous == null
                ? new PropertyDecision(planId, request.propertyId(), request.consultationId(), decidedAt)
                : previous;
        if (previous != null) {
            decision.decide(request.propertyId(), request.consultationId(), decidedAt);
        }
        return response(decisions.save(decision), property, consultation);
    }

    @Transactional(readOnly = true)
    public PropertyDecisionResponse getDecision(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        PropertyDecision decision = decisions
                .findByPlanId(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_DECISION_NOT_FOUND));
        Property property = ownedProperty(planId, decision.getPropertyId());
        BankConsultation consultation = consultations
                .findByIdAndPlanIdAndPropertyId(decision.getConsultationId(), planId, decision.getPropertyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_CONSULTATION_NOT_FOUND));
        return response(decision, property, consultation);
    }

    private PropertyDecisionResponse response(
            PropertyDecision decision, Property property, BankConsultation consultation) {
        return PropertyDecisionResponse.from(
                decision,
                PropertyCandidateResponse.from(
                        property, trafficLights.forProperty(decision.getPlanId(), property.getId())),
                BankConsultationResponse.from(consultation));
    }

    private PropertyConsultationSummaryResponse consultationSummary(Long planId, Long propertyId) {
        List<BankConsultation> saved =
                consultations.findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(planId, propertyId);
        BankConsultationResponse latest = saved.isEmpty() ? null : BankConsultationResponse.from(saved.get(0));
        BankConsultationResponse best = saved.stream()
                .filter(BankConsultation::isSelectable)
                .filter(consultation -> consultation.getApprovedLimit() != null)
                .max(Comparator.comparing(BankConsultation::getApprovedLimit)
                        .thenComparing(
                                BankConsultation::getQuotedRate, Comparator.nullsFirst(Comparator.reverseOrder())))
                .map(BankConsultationResponse::from)
                .orElse(null);
        return new PropertyConsultationSummaryResponse(propertyId, saved.size(), latest, best);
    }

    private Plan ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private Property ownedProperty(Long planId, Long propertyId) {
        return properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
    }
}
