package com.homerun.domain.property.service;

import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.service.HouseAnalysisService;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.BuildingSafetyFactsResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateAnalysisResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyCandidateService {

    private static final long MAX_CANDIDATES = 3;

    private final PlanRepository plans;
    private final PropertyRepository properties;
    private final HouseAnalysisService houseAnalysisService;
    private final PropertyVerificationService verificationService;
    private final Clock clock;

    public PropertyCandidateService(
            PlanRepository plans,
            PropertyRepository properties,
            HouseAnalysisService houseAnalysisService,
            PropertyVerificationService verificationService,
            Clock clock) {
        this.plans = plans;
        this.properties = properties;
        this.houseAnalysisService = houseAnalysisService;
        this.verificationService = verificationService;
        this.clock = clock;
    }

    public PropertyCandidateAnalysisResponse analyzeAndSave(
            Long memberId, Long planId, PropertyCandidateAnalysisRequest request) {
        Plan plan = ownedPlan(memberId, planId);
        if (properties.countByPlanId(planId) >= MAX_CANDIDATES) {
            throw new BusinessException(ErrorCode.PROPERTY_CANDIDATE_LIMIT);
        }

        HouseAnalysisResponse analysis = houseAnalysisService.analyze(request.house());
        BuildingSafetyFactsResponse automatic = automaticFacts(analysis);
        Instant analyzedAt = Instant.now(clock);
        Property property = properties.save(Property.candidate(
                planId,
                request.house().legalDistrictCode(),
                request.house().jibunAddress(),
                request.house().roadAddress(),
                request.house().buildingName(),
                analysis.resolvedHousingType().name(),
                request.deposit(),
                request.marketPrice(),
                request.officialPrice(),
                request.seniorDebt(),
                request.ownerMatches(),
                automatic.violationBuilding(),
                request.trustRegistered(),
                automatic.multiHousehold(),
                request.landlordTaxUnpaid(),
                analyzedAt));

        PropertyFacts facts = new PropertyFacts(
                plan.getLeaseType(),
                request.deposit(),
                request.house().legalDistrictCode().substring(0, 5),
                request.marketPrice(),
                request.officialPrice(),
                request.seniorDebt(),
                request.ownerMatches(),
                automatic.violationBuilding(),
                request.trustRegistered(),
                automatic.multiHousehold(),
                request.landlordTaxUnpaid());
        PropertyVerification verification = verificationService.verifyAndRecord(property.getId(), facts);
        return new PropertyCandidateAnalysisResponse(property.getId(), false, analysis, automatic, verification);
    }

    @Transactional(readOnly = true)
    public List<PropertyCandidateResponse> getCandidates(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        return properties.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(PropertyCandidateResponse::from)
                .toList();
    }

    @Transactional
    public PropertyCandidateResponse select(Long memberId, Long planId, Long propertyId) {
        ownedPlan(memberId, planId);
        if (!properties.existsByIdAndPlanId(propertyId, planId)) {
            throw new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN);
        }
        properties.clearSelection(planId);
        Property selected = properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
        selected.select();
        return PropertyCandidateResponse.from(selected);
    }

    private Plan ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private BuildingSafetyFactsResponse automaticFacts(HouseAnalysisResponse analysis) {
        List<Map<String, String>> titles = analysis.buildingLedger().titles().items();
        Boolean violation = booleanValue(titles, "violBldYn", "violationBuildingYn");
        String description = titles.stream()
                .flatMap(item -> item.values().stream())
                .filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);
        Boolean multiHousehold = description.contains("다가구")
                ? Boolean.TRUE
                : description.matches(".*(다세대|연립|아파트|오피스텔).*") ? Boolean.FALSE : null;
        return new BuildingSafetyFactsResponse(violation, multiHousehold);
    }

    private Boolean booleanValue(List<Map<String, String>> rows, String... keys) {
        for (Map<String, String> row : rows) {
            for (String key : keys) {
                String value = row.get(key);
                if (value == null || value.isBlank()) {
                    continue;
                }
                return switch (value.trim().toUpperCase(Locale.ROOT)) {
                    case "Y", "YES", "TRUE", "1" -> Boolean.TRUE;
                    case "N", "NO", "FALSE", "0" -> Boolean.FALSE;
                    default -> null;
                };
            }
        }
        return null;
    }
}
