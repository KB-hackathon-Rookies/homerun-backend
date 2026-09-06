package com.homerun.domain.property.service;

import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.service.HouseAnalysisService;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.BuildingSafetyFactsResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateAnalysisResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.DataSource;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyCandidateService {

    /** 계획당 매물 등록 상한(FR-P1-03). 명세서 NFR-OPS-02 는 설정 테이블 분리를 권하지만, 화면
     * 비교 가능 개수와 묶인 값이라 지금은 여기 둔다. */
    private static final int MAX_PROPERTIES_PER_PLAN = 5;

    private final PlanRepository plans;
    private final PropertyRepository properties;
    private final HouseAnalysisService houseAnalysisService;
    private final PropertyVerificationService verificationService;
    private final PropertyTrafficLightResolver trafficLights;
    private final Clock clock;

    public PropertyCandidateService(
            PlanRepository plans,
            PropertyRepository properties,
            HouseAnalysisService houseAnalysisService,
            PropertyVerificationService verificationService,
            PropertyTrafficLightResolver trafficLights,
            Clock clock) {
        this.plans = plans;
        this.properties = properties;
        this.houseAnalysisService = houseAnalysisService;
        this.verificationService = verificationService;
        this.trafficLights = trafficLights;
        this.clock = clock;
    }

    public PropertyCandidateAnalysisResponse analyzeAndSave(
            Long memberId, Long planId, PropertyCandidateAnalysisRequest request) {
        Plan plan = ownedPlan(memberId, planId);
        // FR-P1-03. 5개가 차면 더 등록하지 않는다. 외부 조회 앞에서 막는다 — 어차피 거절할
        // 요청으로 공공 API 호출 한도를 쓰지 않는다.
        if (properties.countByPlanId(planId) >= MAX_PROPERTIES_PER_PLAN) {
            throw new BusinessException(ErrorCode.PROPERTY_LIMIT_EXCEEDED);
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
                HouseType.from(analysis.resolvedHousingType()).name(),
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
        boolean priceMatched = analysis.rents() != null && analysis.rents().matchedCount() > 0;
        BigDecimal matchedArea = priceMatched ? exclusiveAreaOf(analysis.rents().items()) : null;
        // 실거래에서 못 가져오면 사용자가 적은 값을 쓴다(FR-P1-10). 둘 다 없으면 null 로 두고
        // 면적 판정을 하지 않는다 — 0 으로 채우면 85㎡ 이하로 통과해 버린다.
        BigDecimal area = matchedArea != null ? matchedArea : request.exclusiveArea();
        property.recordSourcedFacts(
                request.house().mainLotNumber(),
                request.detailAddress(),
                area,
                matchedArea != null ? DataSource.AUTO : DataSource.MANUAL,
                request.house().housingType() == null ? DataSource.AUTO : DataSource.MANUAL,
                priceMatched);
        property.recordRegistryRisks(
                request.leaseholdRegistered(),
                request.seizureOrDispositionRestricted(),
                request.auctionInProgress(),
                request.seniorDebtRegisteredAt());

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
                request.landlordTaxUnpaid(),
                request.leaseholdRegistered(),
                request.seizureOrDispositionRestricted(),
                request.auctionInProgress(),
                request.seniorDebtRegisteredAt(),
                automatic.nonResidential());
        PropertyVerification verification = verificationService.verifyAndRecord(property.getId(), facts);
        return new PropertyCandidateAnalysisResponse(property.getId(), false, analysis, automatic, verification);
    }

    @Transactional(readOnly = true)
    public List<PropertyCandidateResponse> getCandidates(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        return properties.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(property ->
                        PropertyCandidateResponse.from(property, trafficLights.forProperty(planId, property.getId())))
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
        return PropertyCandidateResponse.from(selected, trafficLights.forProperty(planId, propertyId));
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
        // 근생은 주용도에 "제1종/제2종 근린생활시설"로 적힌다. 주거용이 확인되면 FALSE,
        // 어느 쪽도 안 보이면 null 로 둔다 — 모르는 것을 주거용으로 단정하지 않는다.
        Boolean nonResidential = description.contains("근린생활")
                ? Boolean.TRUE
                : description.matches(".*(다세대|연립|아파트|오피스텔|다가구|단독주택).*") ? Boolean.FALSE : null;
        return new BuildingSafetyFactsResponse(violation, multiHousehold, nonResidential);
    }

    /**
     * 매칭된 실거래에서 전용면적을 읽는다. 키 {@code excluUseAr} 는 국토부 실거래가 응답의 것으로
     * {@code RealEstateTransactionXmlParserTest} 가 검증하고 있다.
     *
     * <p>값이 숫자가 아니면 조용히 넘긴다. 억지로 파싱해 이상한 면적을 넣느니 없는 편이 낫다.
     */
    private BigDecimal exclusiveAreaOf(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            String raw = row.get("excluUseAr");
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return new BigDecimal(raw.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
