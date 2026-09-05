package com.homerun.domain.property.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.request.BankConsultationRequest;
import com.homerun.domain.property.dto.request.PropertyComparisonRequest;
import com.homerun.domain.property.dto.request.PropertyDecisionRequest;
import com.homerun.domain.property.dto.response.BankConsultationResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyComparisonResponse;
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
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyDecisionService {

    private final PlanRepository plans;
    private final PropertyRepository properties;
    private final BankConsultationRepository consultations;
    private final PropertyDecisionRepository decisions;
    private final Clock clock;

    public PropertyDecisionService(
            PlanRepository plans,
            PropertyRepository properties,
            BankConsultationRepository consultations,
            PropertyDecisionRepository decisions,
            Clock clock) {
        this.plans = plans;
        this.properties = properties;
        this.consultations = consultations;
        this.decisions = decisions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PropertyComparisonResponse compare(Long memberId, Long planId, PropertyComparisonRequest request) {
        ownedPlan(memberId, planId);
        if (new HashSet<>(request.propertyIds()).size() != request.propertyIds().size()) {
            throw new BusinessException(ErrorCode.PROPERTY_COMPARISON_DUPLICATE);
        }
        List<PropertyCandidateResponse> compared = request.propertyIds().stream()
                .map(propertyId -> ownedProperty(planId, propertyId))
                .map(PropertyCandidateResponse::from)
                .toList();
        return new PropertyComparisonResponse(compared);
    }

    @Transactional
    public BankConsultationResponse addConsultation(
            Long memberId, Long planId, Long propertyId, BankConsultationRequest request) {
        ownedPlan(memberId, planId);
        ownedProperty(planId, propertyId);
        return BankConsultationResponse.from(consultations.save(new BankConsultation(planId, propertyId, request)));
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
        properties.clearSelection(planId);
        property.select();
        Instant decidedAt = Instant.now(clock);
        PropertyDecision decision = decisions
                .findByPlanId(planId)
                .orElseGet(
                        () -> new PropertyDecision(planId, request.propertyId(), request.consultationId(), decidedAt));
        decision.decide(request.propertyId(), request.consultationId(), decidedAt);
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
                decision, PropertyCandidateResponse.from(property), BankConsultationResponse.from(consultation));
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
