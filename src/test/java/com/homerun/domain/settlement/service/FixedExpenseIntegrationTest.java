package com.homerun.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.settlement.entity.FixedExpense;
import com.homerun.domain.settlement.repository.FixedExpenseRepository;
import com.homerun.domain.settlement.type.ExpenseCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** fixed_expense 가 실제 Postgres 에서 왕복하는지와 category↔CHECK 짝을 본다(#235). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FixedExpenseIntegrationTest {

    private final FixedExpenseRepository expenses;
    private final EntityManager em;

    FixedExpenseIntegrationTest(@Autowired FixedExpenseRepository expenses, @Autowired EntityManager em) {
        this.expenses = expenses;
        this.em = em;
    }

    @Test
    void should_roundTripCategoryAndAutopay() {
        long[] ids = setUpPlan();
        FixedExpense saved =
                expenses.save(new FixedExpense(ids[0], ids[1], "대출이자", ExpenseCategory.INTEREST, 264_000L, 25, true));
        em.flush();
        em.clear();

        FixedExpense reloaded = expenses.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCategory()).isEqualTo(ExpenseCategory.INTEREST);
        assertThat(reloaded.getDueDay()).isEqualTo(25);
        assertThat(reloaded.isAutopay()).isTrue();
        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void should_rejectUnknownCategory_becauseEnumAndCheckArePaired() {
        long[] ids = setUpPlan();
        assertThatThrownBy(() -> {
                    em.createNativeQuery("INSERT INTO fixed_expense (user_id, plan_id, name, category, amount)"
                                    + " VALUES (:u, :p, 'x', 'BOGUS', 1000)")
                            .setParameter("u", ids[0])
                            .setParameter("p", ids[1])
                            .executeUpdate();
                    em.flush();
                })
                .isInstanceOf(Exception.class);
    }

    private long[] setUpPlan() {
        long memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'fx-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        long planId = ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
        return new long[] {memberId, planId};
    }
}
