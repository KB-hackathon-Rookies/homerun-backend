package com.homerun.domain.property.service;

import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.response.PropertySearchGuideResponse;
import com.homerun.domain.property.dto.response.PropertySearchGuideResponse.Filter;
import com.homerun.domain.property.dto.response.PropertySearchGuideResponse.Site;
import com.homerun.domain.region.entity.Region;
import com.homerun.domain.region.repository.RegionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온라인 매물 탐색 안내(FR-P0). 계획의 1루 조건을 매물 사이트 필터 형태로 정리하고, 정적 링크·경고를
 * 함께 준다. 판정이 아니라 안내라 저장하지 않는다.
 */
@Service
public class PropertySearchGuideService {

    /** 버팀목 전용면적 상한(FCT-005 = 85㎡). 하드코딩하지 않고 팩트에서 읽는다. */
    private static final String AREA_CEILING_FACT = "FCT-005";

    /** 대상 주택유형(FR-P0-01). 도메인 어휘(#194) — 빌라(연립·다세대)·아파트·오피스텔. */
    private static final List<String> TARGET_HOUSE_TYPES = List.of("APARTMENT", "OFFICETEL", "VILLA");

    /** 탐색처 링크(FR-P0-02). 사이트 연동은 범위 밖이라 링크만 준다. */
    private static final List<Site> SEARCH_SITES = List.of(
            new Site("KB부동산", "https://kbland.kr"),
            new Site("네이버부동산", "https://land.naver.com"),
            new Site("직방", "https://www.zigbang.com"),
            new Site("다방", "https://www.dabangapp.com"),
            new Site("피터팬의 좋은방 구하기", "https://www.peterpanz.com"));

    private static final String SITE_HINT = "같은 매물이 여러 사이트에 올라와 있으면 정상이에요. 한 곳에만 있으면 허위 매물일 수 있으니 의심해 보세요.";

    /** 목록에서 미리 거르기(FR-P0-03). 매물을 클릭하기 전에 목록에서 판단할 수 있는 것들이다. */
    private static final List<String> PREFILTER_WARNINGS = List.of(
            "다가구와 다세대는 둘 다 \"빌라\"로 표시돼요. 다가구는 건물 전체가 한 등기라 전세대출이 안 되는 경우가 많으니 다세대(호실별 등기)를 고르세요.",
            "주변 시세보다 유난히 싸면 근린생활시설(근생빌라)이나 위반건축물일 수 있어요. 2루에서 자동으로 확인해 드려요.",
            "신축인데 전세가가 매매가와 비슷하면 시세·공시가격이 없어 위험도를 판단하기 어려워요.",
            "보증금이 예산을 넘으면 대출 한도는 그대로라 내 돈만 더 들어가요.");

    private final PlanRepository plans;
    private final PlanInputRepository inputs;
    private final RegionRepository regions;
    private final FactRegistry facts;

    public PropertySearchGuideService(
            PlanRepository plans, PlanInputRepository inputs, RegionRepository regions, FactRegistry facts) {
        this.plans = plans;
        this.inputs = inputs;
        this.regions = regions;
        this.facts = facts;
    }

    @Transactional(readOnly = true)
    public PropertySearchGuideResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId)
                .orElseThrow(() -> new com.homerun.global.exception.BusinessException(
                        com.homerun.global.exception.ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        // 입력이 아직 없어도 안내는 준다 — 필터 값만 비어 있고 링크·경고는 그대로 유효하다.
        PlanInput input = inputs.findByPlanId(planId).orElse(null);
        return new PropertySearchGuideResponse(filterOf(input), SEARCH_SITES, SITE_HINT, PREFILTER_WARNINGS);
    }

    private Filter filterOf(PlanInput input) {
        Long deposit = input == null ? null : input.getHopeDeposit();
        String regionName = input == null ? null : regionName(input.getRegionId());
        return new Filter(deposit, areaCeiling(), regionName, TARGET_HOUSE_TYPES);
    }

    private String regionName(Long regionId) {
        if (regionId == null) {
            return null;
        }
        return regions.findById(regionId).map(Region::getName).orElse(null);
    }

    /** 못 읽으면 null — 없는 상한을 지어내지 않는다. 안내 화면이라 필터 한 칸이 비어도 나머지는 준다. */
    private BigDecimal areaCeiling() {
        try {
            return facts.require(AREA_CEILING_FACT).number();
        } catch (FactNotFoundException | UnusableFactException e) {
            return null;
        }
    }
}
