package com.homerun.domain.settlement.service;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.repository.ReturnGuaranteeEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계획의 반환보증 여부를 저장값에서 판단한다. 여러 화면(미반환 대응·퇴거·대시보드)이 같은 규칙을
 * 쓰도록 한 곳에 둔다.
 *
 * <p>반환보증이 있다 = HUG 안심전세 담보(대출보증에 반환보증 포함) 또는 return_guarantee 에 가입
 * 기록이 있음. 둘 다 아니거나 기록이 없으면 없는 것으로 본다.
 */
@Component
public class ReturnGuaranteeStatusResolver {

    private final LoanAccountRepository loans;
    private final ReturnGuaranteeEnrollmentRepository enrollments;

    public ReturnGuaranteeStatusResolver(LoanAccountRepository loans, ReturnGuaranteeEnrollmentRepository enrollments) {
        this.loans = loans;
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public boolean hasReturnGuarantee(Long planId) {
        boolean hugCollateral = loans.findByPlanId(planId)
                .map(loan -> loan.getGuarantee() == CollateralMethod.HUG_SAFE_JEONSE)
                .orElse(false);
        if (hugCollateral) {
            return true;
        }
        return enrollments.findByPlanId(planId).map(e -> e.isEnrolled()).orElse(false);
    }
}
