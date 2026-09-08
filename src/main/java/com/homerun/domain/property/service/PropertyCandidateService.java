package com.homerun.domain.property.service;

import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.service.HouseAnalysisService;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.request.PropertyCandidateAnalysisRequest;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.BuildingSafetyFactsResponse;
import com.homerun.domain.property.dto.response.LandlordConsentGuideResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateAnalysisResponse;
import com.homerun.domain.property.dto.response.PropertyCandidateResponse;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.dto.response.PropertyWorkflowResponse;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.DataSource;
import com.homerun.domain.property.type.LandlordConsent;
import com.homerun.domain.property.type.TrafficLight;
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
    private final PlanInputRepository planInputs;
    private final PropertyRepository properties;
    private final HouseAnalysisService houseAnalysisService;
    private final PropertyVerificationService verificationService;
    private final PropertyTrafficLightResolver trafficLights;
    private final LandlordConsentAdvisor landlordConsentAdvisor;
    private final Clock clock;

    public PropertyCandidateService(
            PlanRepository plans,
            PlanInputRepository planInputs,
            PropertyRepository properties,
            HouseAnalysisService houseAnalysisService,
            PropertyVerificationService verificationService,
            PropertyTrafficLightResolver trafficLights,
            LandlordConsentAdvisor landlordConsentAdvisor,
            Clock clock) {
        this.plans = plans;
        this.planInputs = planInputs;
        this.properties = properties;
        this.houseAnalysisService = houseAnalysisService;
        this.verificationService = verificationService;
        this.trafficLights = trafficLights;
        this.landlordConsentAdvisor = landlordConsentAdvisor;
        this.clock = clock;
    }

    @Transactional
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
                analysis.resolvedHouseType().name(),
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
                request.house().houseType() == null ? DataSource.AUTO : DataSource.MANUAL,
                priceMatched);
        property.recordAutomaticSafety(automatic.multiHousehold(), automatic.nonResidential());
        property.recordRegistryRisks(
                request.leaseholdRegistered(),
                request.seizureOrDispositionRestricted(),
                request.auctionInProgress(),
                request.seniorDebtRegisteredAt());
        property.recordLandlordConsent(request.effectiveLandlordConsent());

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
                automatic.nonResidential(),
                planInputs.findByPlanId(planId).map(PlanInput::getHopeDeposit).orElse(null));
        PropertyVerification verification = verificationService.verifyAndRecord(property.getId(), facts);
        property.startWorkflow(area != null, verification.trafficLight() == TrafficLight.RED);
        return new PropertyCandidateAnalysisResponse(
                property.getId(), false, analysis, automatic, verification, PropertyWorkflowResponse.from(property));
    }

    @Transactional(readOnly = true)
    public List<PropertyCandidateResponse> getCandidates(Long memberId, Long planId) {
        ownedPlan(memberId, planId);
        return properties.findAllByPlanIdOrderByIdAsc(planId).stream()
                .map(property ->
                        PropertyCandidateResponse.from(property, trafficLights.forProperty(planId, property.getId())))
                .sorted(java.util.Comparator.comparingInt(response -> trafficPriority(response.trafficLight())))
                .toList();
    }

    /**
     * 임대인 협조 여부를 저장하고 상태별 안내를 돌려준다(FR-P1-07·08). REFUSED 여도 신호등을
     * 바꾸지 않는다 — 여기서는 상태만 저장하고 설득 스크립트를 안내한다.
     */
    @Transactional
    public LandlordConsentGuideResponse updateLandlordConsent(
            Long memberId, Long planId, Long propertyId, LandlordConsent consent) {
        ownedPlan(memberId, planId);
        Property property = properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
        property.recordLandlordConsent(consent);
        return landlordConsentAdvisor.guide(consent);
    }

    @Transactional(readOnly = true)
    public LandlordConsentGuideResponse landlordConsentGuide(Long memberId, Long planId, Long propertyId) {
        ownedPlan(memberId, planId);
        Property property = properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
        return landlordConsentAdvisor.guide(property.getLandlordConsent());
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

    /**
     * 계약 해제로 매물을 접는다(FR-P8-06). 삭제하지 않고 해제 표시·사유만 남긴다. 선택된 매물이었으면
     * 선택을 풀어 2루에서 다른 매물을 고르게 한다.
     */
    @Transactional
    public PropertyCandidateResponse cancel(Long memberId, Long planId, Long propertyId, String reason) {
        ownedPlan(memberId, planId);
        Property property = properties
                .findByIdAndPlanId(propertyId, planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN));
        property.cancel(reason, Instant.now(clock));
        return PropertyCandidateResponse.from(property, trafficLights.forProperty(planId, propertyId));
    }

    private Plan ownedPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return plan;
    }

    private BuildingSafetyFactsResponse automaticFacts(HouseAnalysisResponse analysis) {
        List<Map<String, String>> titles = analysis.buildingLedger().titles().items();
        // 공개 건축물대장 표제부 API에는 위반건축물 여부가 안정적으로 제공되지 않는다.
        // 정부24 열람 후 STEP 3에서 사람이 확인하기 전까지 null로 둔다.
        Boolean violation = null;
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
        return BuildingSafetyFactsResponse.of(
                violation,
                multiHousehold,
                nonResidential,
                analysis.resolvedHouseType().name());
    }

    private int trafficPriority(TrafficLight light) {
        return switch (light) {
            case BLUE -> 0;
            case GREEN -> 1;
            case YELLOW -> 2;
            case RED -> 3;
        };
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
}
