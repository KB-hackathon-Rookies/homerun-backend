package com.homerun.domain.property.service;

import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.property.dto.response.PropertyRejectionResponse;
import com.homerun.domain.property.dto.response.RejectionGuidanceResponse;
import com.homerun.domain.property.entity.BankConsultation;
import com.homerun.domain.property.repository.BankConsultationRepository;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.RejectionCategory;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 매물의 거절 대응 종합(FR-P8-01·02). 이 매물의 은행 상담 중 거절된 것을 사유 분류별로 묶어
 * 대안을 붙이고(BR-24), 같은 사유가 반복되면 매물 변경을 권한다.
 */
@Service
public class PropertyRejectionService {

    private final PlanRepository plans;
    private final PropertyRepository properties;
    private final BankConsultationRepository consultations;
    private final RejectionAdvisor advisor;

    public PropertyRejectionService(
            PlanRepository plans,
            PropertyRepository properties,
            BankConsultationRepository consultations,
            RejectionAdvisor advisor) {
        this.plans = plans;
        this.properties = properties;
        this.consultations = consultations;
        this.advisor = advisor;
    }

    @Transactional(readOnly = true)
    public PropertyRejectionResponse forProperty(Long memberId, Long planId, Long propertyId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        if (!properties.existsByIdAndPlanId(propertyId, planId)) {
            throw new BusinessException(ErrorCode.PROPERTY_NOT_IN_PLAN);
        }

        // 거절 분류가 채워진 상담만 본다. 최신순으로 담보 방식을 정한다(같은 분류면 가장 최근 것).
        List<BankConsultation> rejected =
                consultations.findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(planId, propertyId).stream()
                        .filter(c -> c.getRejectionCategory() != null)
                        .toList();

        Map<RejectionCategory, Integer> counts = new LinkedHashMap<>();
        Map<RejectionCategory, CollateralMethod> latestCollateral = new LinkedHashMap<>();
        for (BankConsultation c : rejected) {
            RejectionCategory category = c.getRejectionCategory();
            counts.merge(category, 1, Integer::sum);
            latestCollateral.putIfAbsent(category, c.getCollateralMethod()); // 최신순이라 첫 값이 최신
        }

        List<RejectionGuidanceResponse> guidances = new ArrayList<>();
        List<RejectionCategory> repeated = new ArrayList<>();
        for (Map.Entry<RejectionCategory, Integer> entry : counts.entrySet()) {
            guidances.add(advisor.guide(entry.getKey(), latestCollateral.get(entry.getKey())));
            if (entry.getValue() >= 2) {
                repeated.add(entry.getKey());
            }
        }

        // 같은 사유가 두 번 이상이면 매물을 바꾸는 게 낫다(FR-P8-02).
        boolean suggestChangeProperty = !repeated.isEmpty();
        return new PropertyRejectionResponse(propertyId, guidances, repeated, suggestChangeProperty);
    }
}
