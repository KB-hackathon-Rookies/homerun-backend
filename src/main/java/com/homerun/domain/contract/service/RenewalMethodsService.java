package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.RenewalMethodsResponse;
import com.homerun.domain.contract.dto.response.RenewalMethodsResponse.Method;
import com.homerun.domain.contract.repository.LeaseEndRepository;
import com.homerun.domain.contract.type.RenewalMethod;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 갱신 방법 3종 비교 안내(FR-H9-02). 5%·1회·통보시기 같은 법정 수치는 config_effective 에서 읽고,
 * 방법별 조건은 정적으로 구성한다.
 */
@Service
public class RenewalMethodsService {

    private static final String NOTICE_WINDOW = "FCT-111"; // 갱신 의사 통보 시기
    private static final String CLAIM_RIGHT = "FCT-113"; // 계약갱신요구권 1회·5% 상한

    private final PlanRepository plans;
    private final FactRegistry facts;
    private final LeaseEndRepository leaseEnds;

    public RenewalMethodsService(PlanRepository plans, FactRegistry facts, LeaseEndRepository leaseEnds) {
        this.plans = plans;
        this.facts = facts;
        this.leaseEnds = leaseEnds;
    }

    @Transactional(readOnly = true)
    public RenewalMethodsResponse forPlan(Long memberId, Long planId) {
        Plan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        // 저장된 갱신 결정에서 청구권 사용 여부를 읽는다. 없으면 아직 안 쓴 것으로 본다.
        boolean claimUsed =
                leaseEnds.findByPlanId(planId).map(le -> le.isClaimRightUsed()).orElse(false);

        String noticeWindow = facts.require(NOTICE_WINDOW).text();
        String claimDetail = facts.require(CLAIM_RIGHT).text(); // "1회 행사 · 임대료 인상 5% 상한"

        List<Method> methods = List.of(
                new Method(
                        RenewalMethod.CLAIM,
                        "계약갱신청구권",
                        claimDetail,
                        "계약 종료 전 통보 기간(" + noticeWindow + ")에 청구한다. 임대인이 정당한 사유 없이 거절할 수 없다."),
                new Method(
                        RenewalMethod.IMPLIED,
                        "묵시적 갱신",
                        "기존 조건 그대로 2년 연장된다.",
                        "종료 통보 기간에 양측 모두 통보하지 않으면 성립한다. 임대인이 먼저 조건 변경을 연락하면 성립하지 않는다."),
                new Method(RenewalMethod.AGREED, "합의 갱신", "임대인과 합의로 조건을 바꾼다. 인상률 법정 제한이 없다.", "양측이 새 조건에 합의한다."));

        String reuseNote = claimUsed
                ? "계약갱신청구권을 이미 사용했어요. 이번 갱신에는 청구권을 쓸 수 없으니 묵시적 갱신이나 합의 갱신으로 진행하세요."
                : "계약갱신청구권은 1회만 쓸 수 있어요. 한 번 사용하면 다음 갱신에는 청구권을 다시 쓸 수 없어요.";
        return new RenewalMethodsResponse(noticeWindow, methods, claimUsed, reuseNote);
    }
}
