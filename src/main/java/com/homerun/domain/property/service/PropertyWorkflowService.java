package com.homerun.domain.property.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.request.PropertyBuildingStepRequest;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.request.PropertyRegistryStepRequest;
import com.homerun.domain.property.dto.request.PropertyViolationStepRequest;
import com.homerun.domain.property.dto.response.BankConsultationResponse;
import com.homerun.domain.property.dto.response.BuildingSafetyFactsResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyCardResponse;
import com.homerun.domain.property.dto.response.PropertyCheckResponse;
import com.homerun.domain.property.dto.response.PropertyPolicyVerdictListResponse;
import com.homerun.domain.property.dto.response.PropertyStepSaveResponse;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.dto.response.PropertyWorkflowResponse;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;
import com.homerun.domain.property.type.TrafficLight;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyWorkflowService {

    private final PlanRepository plans;
    private final PlanInputRepository planInputs;
    private final PropertyRepository properties;
    private final PropertyCheckRepository checks;
    private final PropertyVerificationService verificationService;
    private final PropertyTrafficLightResolver trafficLights;
    private final PropertyPolicyVerdictService policyVerdicts;
    private final PropertyDecisionService decisions;

    public PropertyWorkflowService(
            PlanRepository plans,
            PlanInputRepository planInputs,
            PropertyRepository properties,
            PropertyCheckRepository checks,
            PropertyVerificationService verificationService,
            PropertyTrafficLightResolver trafficLights,
            PropertyPolicyVerdictService policyVerdicts,
            PropertyDecisionService decisions) {
        this.plans = plans;
        this.planInputs = planInputs;
        this.properties = properties;
        this.checks = checks;
        this.verificationService = verificationService;
        this.trafficLights = trafficLights;
        this.policyVerdicts = policyVerdicts;
        this.decisions = decisions;
    }

    @Transactional(readOnly = true)
    public PropertyWorkflowResponse resume(Long memberId, Long planId, Long propertyId) {
        return PropertyWorkflowResponse.from(ownedProperty(memberId, planId, propertyId, false));
    }

    @Transactional(readOnly = true)
    public void requireLoanProductsReady(Long memberId, Long planId, Long propertyId) {
        ownedProperty(memberId, planId, propertyId, false);
        if (!trafficLights.forProperty(planId, propertyId).showsLoanProducts()) {
            throw new BusinessException(ErrorCode.PROPERTY_LOAN_PRODUCTS_NOT_READY);
        }
    }

    @Transactional
    public PropertyStepSaveResponse saveBuilding(
            Long memberId, Long planId, Long propertyId, PropertyBuildingStepRequest request) {
        Plan plan = ownedPlan(memberId, planId);
        Property property = ownedProperty(planId, propertyId, true);
        property.completeBuildingStep(request.expectedRevision(), request.houseType(), request.exclusiveArea());
        PropertyVerification verification = verify(property, plan);
        if (verification.trafficLight() == TrafficLight.RED) {
            property.blockAt(PropertyDiagnosisStep.BUILDING);
        }
        return response(property, verification);
    }

    @Transactional
    public PropertyStepSaveResponse saveViolation(
            Long memberId, Long planId, Long propertyId, PropertyViolationStepRequest request) {
        Plan plan = ownedPlan(memberId, planId);
        Property property = ownedProperty(planId, propertyId, true);
        property.completeViolationStep(request.expectedRevision(), request.violationBuilding());
        return response(property, verify(property, plan));
    }

    @Transactional
    public PropertyStepSaveResponse saveRegistry(
            Long memberId, Long planId, Long propertyId, PropertyRegistryStepRequest request) {
        Plan plan = ownedPlan(memberId, planId);
        Property property = ownedProperty(planId, propertyId, true);
        property.completeRegistryStep(
                request.expectedRevision(),
                request.officialPrice(),
                request.officialPriceYear(),
                request.officialPriceSource(),
                request.seniorDebt(),
                request.ownerMatches(),
                request.trustRegistered(),
                request.leaseholdRegistered(),
                request.seizureOrDispositionRestricted(),
                request.auctionInProgress(),
                request.seniorDebtRegisteredAt(),
                request.landlordTaxUnpaid());
        PropertyVerification verification = verify(property, plan);
        PropertyWorkflowStatus workflowStatus = status(verification.trafficLight());
        property.finishRegistry(workflowStatus);
        if (workflowStatus == PropertyWorkflowStatus.READY_FOR_CONSULTATION) {
            policyVerdicts.evaluate(memberId, planId, propertyId);
        }
        return response(property, verification);
    }

    @Transactional(readOnly = true)
    public PropertyCardResponse card(Long memberId, Long planId, Long propertyId) {
        Property property = ownedProperty(memberId, planId, propertyId, false);
        TrafficLight light = trafficLights.forProperty(planId, propertyId);
        PropertyPolicyVerdictListResponse loans = light.showsLoanProducts()
                ? policyVerdicts.get(memberId, planId, propertyId)
                : new PropertyPolicyVerdictListResponse(propertyId, List.of(), null);
        List<BankConsultationResponse> consultations = decisions.consultations(memberId, planId, propertyId);
        return new PropertyCardResponse(
                PropertyCandidateResponse.from(property, light),
                PropertyWorkflowResponse.from(property),
                BuildingSafetyFactsResponse.of(
                        property.getViolationBuilding(),
                        property.getMultiHousehold(),
                        property.getNonResidential(),
                        property.getHouseType()),
                property.getOfficialPrice(),
                property.getOfficialPriceYear(),
                property.getOfficialPriceSource(),
                checks.findByPropertyIdOrderById(propertyId).stream()
                        .map(PropertyCheckResponse::from)
                        .toList(),
                loans,
                consultations);
    }

    private PropertyVerification verify(Property property, Plan plan) {
        return verificationService.verifyAndRecord(property.getId(), facts(property, plan));
    }

    private PropertyFacts facts(Property property, Plan plan) {
        String district = property.getLegalDistrictCode();
        String regionCode = district == null || district.length() < 5 ? null : district.substring(0, 5);
        return new PropertyFacts(
                plan.getLeaseType(),
                property.getDeposit(),
                regionCode,
                property.getMarketPrice(),
                property.getOfficialPrice(),
                property.getSeniorDebt(),
                property.getOwnerMatches(),
                property.getViolationBuilding(),
                property.getTrustRegistered(),
                property.getMultiHousehold(),
                property.getLandlordTaxUnpaid(),
                property.getLeaseholdRegistered(),
                property.getSeizureOrDispositionRestricted(),
                property.getAuctionInProgress(),
                property.getSeniorDebtRegisteredAt(),
                property.getNonResidential(),
                hopeDeposit(plan.getId()));
    }

    /** 1루 희망예산. 입력이 아직 없으면 null — 예산 초과 판정을 건너뛴다. */
    private Long hopeDeposit(Long planId) {
        return planInputs.findByPlanId(planId).map(PlanInput::getHopeDeposit).orElse(null);
    }

    private PropertyWorkflowStatus status(TrafficLight light) {
        return switch (light) {
            case RED -> PropertyWorkflowStatus.BLOCKED;
            case YELLOW -> PropertyWorkflowStatus.NEEDS_CONFIRMATION;
            case GREEN -> PropertyWorkflowStatus.READY_FOR_CONSULTATION;
            case BLUE -> PropertyWorkflowStatus.CONSULTED;
        };
    }

    private PropertyStepSaveResponse response(Property property, PropertyVerification verification) {
        return new PropertyStepSaveResponse(PropertyWorkflowResponse.from(property), verification);
    }

    private Plan ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private Property ownedProperty(Long memberId, Long planId, Long propertyId, boolean forUpdate) {
        ownedPlan(memberId, planId);
        return ownedProperty(planId, propertyId, forUpdate);
    }

    private Property ownedProperty(Long planId, Long propertyId, boolean forUpdate) {
        return (forUpdate
                        ? properties.findByIdAndPlanIdForUpdate(propertyId, planId)
                        : properties.findByIdAndPlanId(propertyId, planId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
    }
}
