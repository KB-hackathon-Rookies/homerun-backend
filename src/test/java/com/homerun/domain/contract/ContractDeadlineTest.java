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
                StepTaskTemplate.APPLY_LOAN,
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
        return contractWith(contractDate, balanceDate, moveInReportAt, null);
    }

    private SaveRequest contractWith(
            LocalDate contractDate, LocalDate balanceDate, LocalDate moveInReportAt, LocalDate confirmedDateAt) {
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
                confirmedDateAt,
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
    @DisplayName("전입신고와 확정일자는 잔금일 당일이되 법정기한으로 내보내지 않는다")
    void should_not_mark_settlement_guidance_as_legal() {
        // 주민등록법상 전입신고 의무기한은 전입한 날부터 14일이다. 잔금일 당일은 선순위
        // 위험을 줄이기 위한 권고지 법정기한이 아니다. LEGAL 로 올리면 화면이 법정기한으로
        // 안내하게 되고 진짜 법정기한과 구분이 사라진다.
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        List<Deadline> settlement = deadlines.findAllByPlanIdAndFactCodeIn(planId, List.of("FCT-107"));

        assertThat(settlement).hasSize(2);
        assertThat(settlement).allSatisfy(deadline -> {
            assertThat(deadline.getType()).isEqualTo(DeadlineType.RECOMMENDED);
            assertThat(deadline.getDueDate()).isEqualTo(BALANCE);
        });
    }

    @Test
    @DisplayName("대출 신청 마감은 잔금일부터 3개월인 법정기한이다(#67)")
    void should_write_loan_application_deadline_as_legal() {
        // FCT-103: 잔금일·전입일 중 빠른 날 + 3개월. 전입일을 모르면 잔금일 기준이다.
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        Deadline loanApplication = byFact().get("FCT-103");

        assertThat(loanApplication.getType()).isEqualTo(DeadlineType.LEGAL);
        assertThat(loanApplication.getDueDate()).isEqualTo(BALANCE.plusMonths(3));
        assertThat(loanApplication.getBaseEvent()).isEqualTo("BALANCE_DATE");
    }

    @Test
    @DisplayName("전입일이 잔금일보다 빠르면 전입일이 기준이다(#67)")
    void should_baseLoanApplicationDeadline_onEarlierMoveInDate() {
        LocalDate earlyMoveIn = BALANCE.minusDays(5);

        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, earlyMoveIn));

        Deadline loanApplication = byFact().get("FCT-103");

        assertThat(loanApplication.getDueDate()).isEqualTo(earlyMoveIn.plusMonths(3));
        assertThat(loanApplication.getBaseEvent()).isEqualTo("MOVE_IN_REPORT_DATE");
    }

    @Test
    @DisplayName("법정기한이 아닌 마감은 여전히 권고다")
    void should_keep_other_deadlines_as_recommended_or_more() {
        // FCT-103 을 LEGAL 로 추가했다고 다른 마감의 종류가 바뀌면 안 된다.
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        assertThat(byFact().get("FCT-104").getType()).isEqualTo(DeadlineType.RECOMMENDED);
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
    @DisplayName("모든 마감이 근거와 기준 사건을 남긴다")
    void should_record_base_event_on_every_deadline() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        assertThat(deadlines.findAllByPlanIdAndTaskIdIsNotNull(planId))
                .allMatch(deadline -> deadline.getBaseEvent() != null
                        && !deadline.getBaseEvent().isBlank());
    }

    @Test
    @DisplayName("잔금일을 모르면 마감을 지어내지 않는다")
    void should_not_invent_deadline_without_balance_date() {
        service.save(ownerId, planId, contractWith(CONTRACT, null, null));

        Map<String, Deadline> written = byFact();

        // 잔금일도 전입일도 없으면 대출 신청 마감(FCT-103)의 기준(빠른 날)조차 없다.
        assertThat(written).doesNotContainKeys("FCT-103", "FCT-104", "FCT-105", "FCT-107", "FCT-109");
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
    @DisplayName("반환보증은 전입신고와 확정일자가 둘 다 끝난 날 기준이다")
    void should_base_guarantee_on_the_later_of_both_steps() {
        // FCT-109 는 "전입·확정일자 완료 후 즉시"다. 한쪽만 보고 계산하면 아직 가입할 수
        // 없는 사람에게 이미 지났다고 표시된다.
        LocalDate moveIn = BALANCE.plusDays(1);
        LocalDate confirmed = BALANCE.plusDays(3);

        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, moveIn, confirmed));

        Deadline guarantee = byFact().get("FCT-109");
        assertThat(guarantee.getDueDate()).isEqualTo(confirmed);
        assertThat(guarantee.getBaseEvent()).isEqualTo("PROTECTION_COMPLETED");
    }

    @Test
    @DisplayName("한쪽만 끝났으면 완료 기준으로 잡지 않는다")
    void should_not_use_completion_base_when_only_one_step_is_done() {
        LocalDate moveIn = BALANCE.plusDays(1);

        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, moveIn, null));

        Deadline guarantee = byFact().get("FCT-109");
        assertThat(guarantee.getDueDate()).isEqualTo(BALANCE);
        assertThat(guarantee.getBaseEvent()).isEqualTo("BALANCE_DATE");
        assertThat(guarantee.getLabel()).contains("예정");
    }

    @Test
    @DisplayName("모든 마감은 근거 팩트를 남긴다")
    void should_record_fact_code_on_every_deadline() {
        service.save(ownerId, planId, contractWith(CONTRACT, BALANCE, null));

        assertThat(deadlines.findAllByPlanIdAndTaskIdIsNotNull(planId))
                .allMatch(deadline -> deadline.getFactCode() != null);
    }
}
