package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.entity.ReturnGuaranteeEnrollment;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.repository.ReturnGuaranteeEnrollmentRepository;
import com.homerun.domain.settlement.type.RepaymentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 반환보증 여부 = HUG 담보 OR 가입 기록. 둘 다 아니거나 없으면 false. */
class ReturnGuaranteeStatusResolverTest {

    private static final Long PLAN_ID = 10L;

    private final LoanAccountRepository loans = mock(LoanAccountRepository.class);
    private final ReturnGuaranteeEnrollmentRepository enrollments = mock(ReturnGuaranteeEnrollmentRepository.class);
    private final ReturnGuaranteeStatusResolver resolver = new ReturnGuaranteeStatusResolver(loans, enrollments);

    private LoanAccount loan(CollateralMethod guarantee) {
        LoanAccount l = new LoanAccount(PLAN_ID);
        l.apply(
                ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                guarantee,
                100_000_000L,
                new BigDecimal("2.2"),
                RepaymentType.MATURITY_LUMP_SUM,
                LocalDate.of(2026, 5, 1),
                null,
                null,
                0);
        return l;
    }

    private ReturnGuaranteeEnrollment enrollment(boolean enrolled) {
        ReturnGuaranteeEnrollment e = new ReturnGuaranteeEnrollment(PLAN_ID);
        e.update(enrolled, false, null);
        return e;
    }

    @Test
    void should_beTrue_forHugCollateral() {
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(CollateralMethod.HUG_SAFE_JEONSE)));
        assertThat(resolver.hasReturnGuarantee(PLAN_ID)).isTrue();
    }

    @Test
    void should_beTrue_whenEnrolled_evenIfNotHug() {
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(CollateralMethod.HF)));
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.of(enrollment(true)));
        assertThat(resolver.hasReturnGuarantee(PLAN_ID)).isTrue();
    }

    @Test
    void should_beFalse_whenNotHugAndNotEnrolled() {
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.of(loan(CollateralMethod.HF)));
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.of(enrollment(false)));
        assertThat(resolver.hasReturnGuarantee(PLAN_ID)).isFalse();
    }

    @Test
    void should_beFalse_whenNoData() {
        when(loans.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(enrollments.findByPlanId(PLAN_ID)).thenReturn(Optional.empty());
        assertThat(resolver.hasReturnGuarantee(PLAN_ID)).isFalse();
    }
}
