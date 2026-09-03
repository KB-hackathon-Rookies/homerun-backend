package com.homerun.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.contract.dto.ContractDtos.SaveRequest;
import com.homerun.domain.contract.service.ContractService;
import com.homerun.domain.dashboard.entity.Deadline;
import com.homerun.domain.dashboard.repository.DeadlineRepository;
import com.homerun.domain.dashboard.type.DeadlineType;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.type.StepTaskTemplate;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약 날짜에서 마감이 나오는지 본다(SEQ-01-04, SEQ-01-06).
 *
 * <p>대시보드는 할 일에 붙은 마감으로 우선순위를 매긴다. 그 마감이 안 만들어지면
 * {@code OVERDUE} 도 {@code IRREVERSIBLE_DEADLINE} 도 영영 뜨지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ContractDeadlineTest {

    private static final LocalDate BALANCE = LocalDate.of(2026, 11, 20);
    private static final LocalDate CONTRACT = LocalDate.of(2026, 10, 20);

    private final ContractService service;
    private final DeadlineRepository deadlines;
    private final EntityManager em;

    private Long ownerId;
    private Long planId;

    ContractDeadlineTest(
            @Autowired ContractService service, @Autowired DeadlineRepository deadlines, @Autowired EntityManager em) {
        this.service = service;
        this.deadlines = deadlines;
        this.em = em;
    }

    @BeforeEach
    void setUpPlanWithTasks() {
        ownerId = (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
        planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", ownerId)
                        .getSingleResult();
        Long stepId =
                (Long) em.createNativeQuery("""
                        INSERT INTO plan_step (plan_id, step_code, step_name, sequence, status)
                        VALUES (:pid, 'THIRD_EXECUTION', '3루 실행', 4, 'READY')
                        RETURNING id
                        """).setParameter("pid", planId).getSingleResult();

        int sequence = 1;
        for (StepTaskTemplate template : List.of(
                StepTaskTemplate.BANK_CONSULTATION,
                StepTaskTemplate.DOCUMENT_CHECK,
                StepTaskTemplate.REGISTER_CHECK,
                StepTaskTemplate.MOVE_IN_REPORT,
                StepTaskTemplate.FIXED_DATE,
                StepTaskTemplate.GUARANTEE_CHECK)) {
            em.createNativeQuery("""
                            INSERT INTO step_task (plan_step_id, task_code, task_name, sequence, status)
                            VALUES (:sid, :code, :name, :seq, 'TODO')
                            """)
                    .setParameter("sid", stepId)
                    .setParameter("code", template.code())
                    .setParameter("name", template.displayName())
                    .setParameter("seq", sequence++)
                    .executeUpdate();
        }
        em.flush();
        em.clear();
    }

    private SaveRequest contractWith(LocalDate contractDate, LocalDate balanceDate, LocalDate moveInReportAt) {
        return new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                null,
                contractDate,
                balanceDate,
                balanceDate,
                null,
                moveInReportAt,
                null,
                null,
                null,
                false);
    }

    private Map<String, Deadline> byFact() {
        em.flush();
        em.clear();
        return deadlines.findAllByPlanIdAndTaskIdIsNotNull(planId).stream()
                .filter(deadline -> deadline.getFactCode() != null)
                .collect(Collectors.toMap(Deadline::getFactCode, deadline -> deadline, (first, second) -> first));
    }

    @Test
    @DisplayName("전입신고와 확정일자 마감은 잔금일 당일이고 법정 기한이다")
    void should_write_legal_deadline_on_balance_date() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        List<Deadline> settlement = deadlines.findAllByPlanIdAndFactCodeIn(planId, List.of("FCT-107"));

        assertThat(settlement).hasSize(2);
        assertThat(settlement).allSatisfy(deadline -> {
            assertThat(deadline.getType()).isEqualTo(DeadlineType.LEGAL);
            assertThat(deadline.getDueDate()).isEqualTo(BALANCE);
        });
    }

    @Test
    @DisplayName("은행 상담 착수일은 잔금일에서 21일을 뺀 권장일이다")
    void should_write_recommended_consult_start() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        Deadline consult = byFact().get("FCT-104");

        assertThat(consult.getType()).isEqualTo(DeadlineType.RECOMMENDED);
        assertThat(consult.getDueDate()).isEqualTo(BALANCE.minusDays(21));
    }

    @Test
    @DisplayName("서류 발급은 상담일 기준으로 다시 역산한다")
    void should_chain_document_deadline_from_consult_date() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        // 잔금일 D-21 이 상담일, 거기서 다시 D-7 이라 잔금일 D-28 이다.
        assertThat(byFact().get("FCT-105").getDueDate()).isEqualTo(BALANCE.minusDays(28));
    }

    @Test
    @DisplayName("계약 전 검증은 계약일 기준이다")
    void should_write_verification_deadline_from_contract_date() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        assertThat(byFact().get("FCT-106").getDueDate()).isEqualTo(CONTRACT.minusDays(3));
    }

    @Test
    @DisplayName("법정 기한과 권장일을 섞지 않는다")
    void should_separate_legal_from_recommended() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        Map<String, Deadline> written = byFact();

        assertThat(written.get("FCT-107").getType()).isEqualTo(DeadlineType.LEGAL);
        assertThat(written.get("FCT-104").getType()).isEqualTo(DeadlineType.RECOMMENDED);
        assertThat(written.get("FCT-105").getType()).isEqualTo(DeadlineType.RECOMMENDED);
    }

    @Test
    @DisplayName("잔금일을 모르면 마감을 지어내지 않는다")
    void should_not_invent_deadline_without_balance_date() {
        service.save(ownerId, planId, contractWith(CONTRACT, null, null));

        Map<String, Deadline> written = byFact();

        assertThat(written).doesNotContainKeys("FCT-104", "FCT-105", "FCT-107", "FCT-109");
        // 계약일은 알고 있으므로 계약 전 검증만 남는다.
        assertThat(written).containsKey("FCT-106");
    }

    @Test
    @DisplayName("잔금일을 미루면 옛 마감이 남지 않는다")
    void should_replace_deadlines_when_balance_date_moves() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));
        LocalDate moved = BALANCE.plusDays(14);

        service.save(ownerId, planId, contractWith(CONTRACT, moved, null));

        List<Deadline> settlement = deadlines.findAllByPlanIdAndFactCodeIn(planId, List.of("FCT-107"));
        assertThat(settlement).hasSize(2);
        assertThat(settlement).allMatch(deadline -> deadline.getDueDate().equals(moved));
    }

    @Test
    @DisplayName("반환보증은 전입신고를 마친 날 기준이다")
    void should_base_guarantee_on_move_in_report() {
        LocalDate moveIn = BALANCE.plusDays(1);

        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, moveIn));

        assertThat(byFact().get("FCT-109").getDueDate()).isEqualTo(moveIn);
    }

    @Test
    @DisplayName("모든 마감은 근거 팩트를 남긴다")
    void should_record_fact_code_on_every_deadline() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        assertThat(deadlines.findAllByPlanIdAndTaskIdIsNotNull(planId))
                .allMatch(deadline -> deadline.getFactCode() != null);
    }
}
