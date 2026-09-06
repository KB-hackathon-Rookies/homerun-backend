package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.LoanTransferGuideResponse;
import com.homerun.domain.contract.dto.response.LoanTransferGuideResponse.Method;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대출 승계·이전 안내(FR-H10-03). 분기 없는 정적 안내라 소유권 확인만 하고 고정 문구를 준다.
 * 취급 방법·가능 여부가 은행마다 달라 값을 단정하지 않고 문의를 안내한다.
 */
@Service
public class LoanTransferGuideService {

    private static final LoanTransferGuideResponse GUIDE = new LoanTransferGuideResponse(
            List.of(
                    new Method("상환 후 신규", "지금 대출을 모두 갚고, 새 집에서 대출을 처음부터 다시 받아요."),
                    new Method("임차목적물 변경", "기존 대출의 담보(임차 목적물)를 새 집으로 바꿔 대출을 이어가요.")),
            "은행마다 취급 방법과 가능 여부가 달라요. 이사 계획이 서면 곧바로 지금 대출받은 은행에 문의하세요.");

    private final PlanRepository plans;

    public LoanTransferGuideService(PlanRepository plans) {
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public LoanTransferGuideResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);
        return GUIDE;
    }
}
