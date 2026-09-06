package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import com.homerun.domain.settlement.dto.response.LoanAccountResponse;
import com.homerun.domain.settlement.entity.LoanAccount;
import com.homerun.domain.settlement.repository.LoanAccountRepository;
import com.homerun.domain.settlement.type.RepaymentType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * loan_account 가 실제 Postgres 에서 왕복하는지와 enum↔CHECK 짝이 맞는지 본다(#231).
 * enum 값과 CHECK 제약이 어긋나면 저장할 때 런타임에 터진다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LoanAccountIntegrationTest {

    private final LoanAccountRepository loans;
    private final EntityManager em;

    LoanAccountIntegrationTest(@Autowired LoanAccountRepository loans, @Autowired EntityManager em) {
        this.loans = loans;
        this.em = em;
    }

    @Test
    void should_roundTripAllEnumValues_againstRealPostgres() {
        Long planId = setUpPlan();
        LoanAccount loan = new LoanAccount(planId);
        loan.apply(
                ConsultedLoanProduct.YOUTH_BEOTIMMOK,
                CollateralMethod.HUG_SAFE_JEONSE,
                144_000_000L,
                new BigDecimal("2.200"),
                RepaymentType.MATURITY_LUMP_SUM,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2028, 5, 1),
                LocalDate.of(2030, 5, 1),
                0);
        loans.save(loan);
        em.flush();
        em.clear();

        LoanAccount reloaded = loans.findByPlanId(planId).orElseThrow();
        assertThat(reloaded.getProduct()).isEqualTo(ConsultedLoanProduct.YOUTH_BEOTIMMOK);
        assertThat(reloaded.getGuarantee()).isEqualTo(CollateralMethod.HUG_SAFE_JEONSE);
        assertThat(reloaded.getPrincipal()).isEqualTo(144_000_000L);
        assertThat(reloaded.getRate()).isEqualByComparingTo("2.2");
        assertThat(reloaded.getRepaymentType()).isEqualTo(RepaymentType.MATURITY_LUMP_SUM);
        // 조회 응답은 월 이자를 파생한다: 1.44억 × 2.2% ÷ 12 = 26.4만.
        assertThat(LoanAccountResponse.from(reloaded).monthlyInterest()).isEqualTo(264_000L);
    }

    @Test
    void should_enforceOnePerPlan_whenSavingSecond() {
        // UNIQUE plan_id: 서비스는 덮어쓰기로 처리하지만, 두 건 직접 저장은 DB 가 막는다.
        Long planId = setUpPlan();
        loans.saveAndFlush(loanFor(planId));

        assertThatThrownBy(() -> loans.saveAndFlush(loanFor(planId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private LoanAccount loanFor(Long planId) {
        LoanAccount loan = new LoanAccount(planId);
        loan.apply(
                ConsultedLoanProduct.BANK_LOAN,
                null,
                100_000_000L,
                new BigDecimal("3.0"),
                RepaymentType.UNKNOWN,
                LocalDate.of(2026, 5, 1),
                null,
                null,
                0);
        return loan;
    }

    private Long setUpPlan() {
        Long memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'loan-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        return ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
    }
}
