package com.homerun.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.contract.dto.ContractDtos.ChecklistItem;
import com.homerun.domain.contract.dto.ContractDtos.ContractGuide;
import com.homerun.domain.contract.dto.ContractDtos.SaveRequest;
import com.homerun.domain.contract.dto.ContractDtos.SpecialTermAdvice;
import com.homerun.domain.contract.dto.ContractDtos.StepView;
import com.homerun.domain.contract.service.ContractService;
import com.homerun.domain.contract.type.ChecklistStatus;
import com.homerun.domain.contract.type.ContractStep;
import com.homerun.domain.contract.type.StepStatus;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ContractServiceTest {

    private static final LocalDate BALANCE = LocalDate.of(2026, 11, 20);

    private final ContractService service;
    private final EntityManager em;

    private Long ownerId;
    private Long planId;

    ContractServiceTest(@Autowired ContractService service, @Autowired EntityManager em) {
        this.service = service;
        this.em = em;
    }

    @BeforeEach
    void setUpPlan() {
        ownerId = newMember();
        planId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", ownerId)
                        .getSingleResult();
        em.flush();
        em.clear();
    }

    private Long newMember() {
        return (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
    }

    private Long newProperty() {
        Long id = (Long) em.createNativeQuery("INSERT INTO property (plan_id) VALUES (:pid) RETURNING id")
                .setParameter("pid", planId)
                .getSingleResult();
        em.flush();
        return id;
    }

    private void recordCheck(Long propertyId, String code, String result) {
        em.createNativeQuery("""
                        INSERT INTO property_check (property_id, check_code, check_label, result)
                        VALUES (:pid, :code, :code, :result)
                        """)
                .setParameter("pid", propertyId)
                .setParameter("code", code)
                .setParameter("result", result)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    /** 보증부월세, 잔금일만 정해진 계약 전 상태. */
    private SaveRequest wolseWithDeposit() {
        return new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                70_000L,
                null,
                null,
                BALANCE,
                BALANCE,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    private static ChecklistItem item(ContractGuide guide, String code) {
        return guide.checklist().stream()
                .filter(i -> i.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("체크리스트 항목이 없다: " + code));
    }

    private static StepView step(ContractGuide guide, ContractStep step) {
        return guide.progress().steps().stream()
                .filter(s -> s.step() == step)
                .findFirst()
                .orElseThrow(() -> new AssertionError("단계가 없다: " + step));
    }

    @Test
    @DisplayName("계약 정보가 없어도 계약 전 확인 사항은 내려간다")
    void should_guide_before_contract_exists() {
        ContractGuide guide = service.guide(ownerId, planId);

        assertThat(guide.contractId()).isNull();
        assertThat(guide.progress().currentStep()).isEqualTo(ContractStep.CONTRACT_SIGNED);
        assertThat(guide.checklist())
                .extracting(ChecklistItem::code)
                .containsExactly("REGISTRY", "BUILDING_LEDGER", "OFFICIAL_PRICE", "BANK_CONSULT");
        assertThat(guide.checklist()).allMatch(i -> i.status() == ChecklistStatus.TODO);
    }

    @Test
    @DisplayName("남의 계획은 볼 수 없다")
    void should_reject_other_members_plan() {
        Long stranger = newMember();

        assertThatThrownBy(() -> service.guide(stranger, planId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PLAN_ACCESS_DENIED);
    }

    // PRP-02-01 은행 사전상담

    @Test
    @DisplayName("잔금일에서 21일을 빼 상담 착수일을 알려준다")
    void should_recommend_consult_start_three_weeks_before_balance() {
        ContractGuide guide = service.save(ownerId, planId, wolseWithDeposit());

        assertThat(guide.preConsult().consulted()).isFalse();
        assertThat(guide.preConsult().recommendedStartDate()).isEqualTo(BALANCE.minusDays(21));
    }

    @Test
    @DisplayName("대출 신청 마감은 잔금일과 전입일 중 빠른 날에서 센다")
    void should_count_loan_deadline_from_earlier_date() {
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                null,
                null,
                BALANCE,
                BALANCE.minusDays(5),
                null,
                null,
                null,
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(guide.preConsult().applyDeadline())
                .isEqualTo(BALANCE.minusDays(5).plusMonths(3));
    }

    @Test
    @DisplayName("잔금일을 모르면 상담 시점을 지어내지 않는다")
    void should_not_guess_consult_date_without_balance_date() {
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(guide.preConsult().recommendedStartDate()).isNull();
        assertThat(guide.preConsult().applyDeadline()).isNull();
        assertThat(guide.preConsult().action()).isNotNull();
    }

    @Test
    @DisplayName("전입일만 알아도 대출 신청 마감은 알려준다")
    void should_keep_deadline_when_only_move_in_date_is_known() {
        LocalDate moveIn = LocalDate.of(2026, 12, 1);
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                null,
                null,
                null,
                moveIn,
                null,
                null,
                null,
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(guide.preConsult().recommendedStartDate()).isNull();
        assertThat(guide.preConsult().applyDeadline()).isEqualTo(moveIn.plusMonths(3));
        assertThat(guide.preConsult().factCodes()).contains("FCT-103");
    }

    // PRP-02-02 계약 전 체크리스트

    @Test
    @DisplayName("검증을 돌리지 않은 서류는 통과가 아니라 확인 대기다")
    void should_not_pass_unverified_documents() {
        service.save(ownerId, planId, wolseWithDeposit());

        ContractGuide guide = service.guide(ownerId, planId);

        assertThat(item(guide, "REGISTRY").status()).isEqualTo(ChecklistStatus.TODO);
        assertThat(item(guide, "BUILDING_LEDGER").status()).isEqualTo(ChecklistStatus.TODO);
        assertThat(item(guide, "OFFICIAL_PRICE").status()).isEqualTo(ChecklistStatus.TODO);
    }

    @Test
    @DisplayName("등기부 항목이 전부 통과하면 체크리스트도 통과다")
    void should_mark_registry_done_when_all_checks_pass() {
        Long propertyId = newProperty();
        recordCheck(propertyId, "OWNER_MATCH", "PASS");
        recordCheck(propertyId, "TRUST_REGISTRATION", "PASS");
        recordCheck(propertyId, "JEONSE_RATIO", "PASS");
        service.save(ownerId, planId, withProperty(propertyId));

        ContractGuide guide = service.guide(ownerId, planId);

        assertThat(item(guide, "REGISTRY").status()).isEqualTo(ChecklistStatus.DONE);
        assertThat(item(guide, "REGISTRY").action()).isNull();
    }

    @Test
    @DisplayName("보증금 계약인데 전세가율 판정이 없으면 등기부는 통과가 아니다")
    void should_not_pass_registry_when_a_required_check_is_missing() {
        Long propertyId = newProperty();
        recordCheck(propertyId, "OWNER_MATCH", "PASS");
        recordCheck(propertyId, "TRUST_REGISTRATION", "PASS");
        service.save(ownerId, planId, withProperty(propertyId));

        assertThat(item(service.guide(ownerId, planId), "REGISTRY").status()).isEqualTo(ChecklistStatus.TODO);
    }

    @Test
    @DisplayName("보증금이 없으면 공시가격은 체크리스트에 올리지 않는다")
    void should_drop_official_price_without_deposit() {
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                0L,
                600_000L,
                0L,
                null,
                null,
                BALANCE,
                null,
                null,
                null,
                null,
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(guide.checklist()).noneMatch(i -> i.code().equals("OFFICIAL_PRICE"));
    }

    @Test
    @DisplayName("확인 못 한 항목이 하나라도 있으면 서류 전체가 확인 대기다")
    void should_keep_document_todo_when_any_check_unknown() {
        Long propertyId = newProperty();
        recordCheck(propertyId, "OWNER_MATCH", "PASS");
        recordCheck(propertyId, "TRUST_REGISTRATION", "UNKNOWN");
        service.save(ownerId, planId, withProperty(propertyId));

        assertThat(item(service.guide(ownerId, planId), "REGISTRY").status()).isEqualTo(ChecklistStatus.TODO);
    }

    @Test
    @DisplayName("막힌 항목이 있으면 체크리스트가 막힌 것으로 나온다")
    void should_block_document_when_any_check_blocks() {
        Long propertyId = newProperty();
        recordCheck(propertyId, "VIOLATION_BUILDING", "BLOCK");
        service.save(ownerId, planId, withProperty(propertyId));

        assertThat(item(service.guide(ownerId, planId), "BUILDING_LEDGER").status())
                .isEqualTo(ChecklistStatus.BLOCKED);
    }

    @Test
    @DisplayName("은행 상담일을 넣으면 체크리스트가 통과한다")
    void should_mark_bank_consult_done() {
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                null,
                null,
                BALANCE,
                null,
                null,
                null,
                BALANCE.minusDays(30),
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(item(guide, "BANK_CONSULT").status()).isEqualTo(ChecklistStatus.DONE);
        assertThat(guide.preConsult().consulted()).isTrue();
    }

    // PRP-02-03 권장 특약

    @Test
    @DisplayName("권장 특약 세 건을 팩트 원문으로 안내한다")
    void should_advise_three_special_terms() {
        ContractGuide guide = service.save(ownerId, planId, wolseWithDeposit());

        assertThat(guide.specialTerms()).hasSize(3);
        assertThat(guide.specialTerms()).allMatch(SpecialTermAdvice::applicable);
        assertThat(guide.specialTerms())
                .allMatch(t -> t.text() != null && !t.text().isBlank());
        assertThat(guide.specialTerms())
                .extracting(SpecialTermAdvice::factCode)
                .containsExactlyInAnyOrder("FCT-116", "FCT-117", "FCT-118");
    }

    @Test
    @DisplayName("보증금이 없는 순수 월세에는 특약이 해당하지 않는다")
    void should_not_apply_terms_without_deposit() {
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                0L,
                600_000L,
                0L,
                null,
                null,
                BALANCE,
                null,
                null,
                null,
                null,
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(guide.specialTerms()).noneMatch(SpecialTermAdvice::applicable);
        assertThat(guide.riskCheckRequired()).isFalse();
    }

    // PRP-02-04 계약 진행상태

    @Test
    @DisplayName("끝난 날이 있는 단계만 완료로 본다")
    void should_derive_progress_from_completed_dates() {
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                3_000_000L,
                BALANCE.minusMonths(1),
                BALANCE,
                BALANCE,
                null,
                null,
                null,
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(step(guide, ContractStep.CONTRACT_SIGNED).status()).isEqualTo(StepStatus.DONE);
        assertThat(step(guide, ContractStep.DOWN_PAYMENT).status()).isEqualTo(StepStatus.DONE);
        assertThat(step(guide, ContractStep.CONFIRMED_DATE).status()).isEqualTo(StepStatus.CURRENT);
        assertThat(step(guide, ContractStep.LOAN_APPLIED).status()).isEqualTo(StepStatus.PENDING);
        assertThat(guide.progress().currentStep()).isEqualTo(ContractStep.CONFIRMED_DATE);
    }

    @Test
    @DisplayName("잔금 예정일을 적었다고 잔금을 낸 것으로 보지 않는다")
    void should_not_treat_scheduled_balance_date_as_paid() {
        ContractGuide guide = service.save(ownerId, planId, wolseWithDeposit());

        assertThat(step(guide, ContractStep.BALANCE_PAID).status()).isNotEqualTo(StepStatus.DONE);
        assertThat(step(guide, ContractStep.BALANCE_PAID).doneAt()).isNull();
    }

    @Test
    @DisplayName("계약 정보를 다시 저장하면 계획당 한 건으로 덮어쓴다")
    void should_overwrite_single_contract_per_plan() {
        Long first = service.save(ownerId, planId, wolseWithDeposit()).contractId();
        Long second = service.save(ownerId, planId, wolseWithDeposit()).contractId();

        assertThat(second).isEqualTo(first);
    }

    // PRP-02-05, PRP-02-06 잔금 · 전입

    @Test
    @DisplayName("잔금·전입·확정일자를 같은 날로 맞추면 같은 날로 인정한다")
    void should_detect_same_day_settlement() {
        ContractGuide guide = service.save(ownerId, planId, settledOn(BALANCE, BALANCE, BALANCE));

        assertThat(guide.settlement().sameDay()).isTrue();
        assertThat(guide.settlement().protectionEffectiveAt()).isEqualTo(BALANCE.plusDays(1));
        assertThat(guide.settlement().gapDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("전입신고를 미루면 비는 기간을 날짜로 알려준다")
    void should_report_gap_when_move_in_is_late() {
        ContractGuide guide = service.save(ownerId, planId, settledOn(BALANCE, BALANCE.plusDays(3), BALANCE));

        assertThat(guide.settlement().sameDay()).isFalse();
        assertThat(guide.settlement().gapDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("월세 계약에는 전입신고의 세액공제 효과를 함께 알려준다")
    void should_mention_tax_credit_for_monthly_rent() {
        ContractGuide guide = service.save(ownerId, planId, wolseWithDeposit());

        assertThat(guide.settlement().taxCreditNote()).contains("세액공제");
        assertThat(guide.settlement().factCodes()).contains("FCT-050");
    }

    @Test
    @DisplayName("전세에는 세액공제 안내를 붙이지 않는다")
    void should_not_mention_tax_credit_for_jeonse() {
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.JEONSE,
                200_000_000L,
                0L,
                0L,
                null,
                null,
                BALANCE,
                null,
                null,
                null,
                null,
                null,
                null,
                false);

        ContractGuide guide = service.save(ownerId, planId, request);

        assertThat(guide.settlement().taxCreditNote()).isNull();
    }

    // PRP-02-07 월세 계약 안전검증

    @Test
    @DisplayName("보증금이 있는 월세는 매물 검증 대상이다")
    void should_require_risk_check_for_wolse_with_deposit() {
        ContractGuide guide = service.save(ownerId, planId, wolseWithDeposit());

        assertThat(guide.riskCheckRequired()).isTrue();
    }

    // 매물 연결과 권한

    @Test
    @DisplayName("검증을 돌리면 결과가 남아 체크리스트가 따라 움직인다")
    void should_record_verification_so_checklist_reflects_it() {
        Long propertyId = newProperty();
        service.save(ownerId, planId, withProperty(propertyId));

        service.riskCheck(ownerId, planId, cleanFacts());
        em.flush();
        em.clear();

        assertThat(item(service.guide(ownerId, planId), "BUILDING_LEDGER").status())
                .isEqualTo(ChecklistStatus.DONE);
    }

    @Test
    @DisplayName("매물을 아직 연결하지 않았으면 판정만 하고 저장하지 않는다")
    void should_only_evaluate_when_no_property_linked() {
        service.save(ownerId, planId, wolseWithDeposit());

        assertThat(service.riskCheck(ownerId, planId, cleanFacts()).findings()).isNotEmpty();
        assertThat(item(service.guide(ownerId, planId), "BUILDING_LEDGER").status())
                .isEqualTo(ChecklistStatus.TODO);
    }

    @Test
    @DisplayName("남의 계획에 달린 매물은 계약에 연결할 수 없다")
    void should_reject_property_from_another_plan() {
        Long strangerId = newMember();
        Long strangerPlanId = (Long)
                em.createNativeQuery("INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'WOLSE') RETURNING id")
                        .setParameter("uid", strangerId)
                        .getSingleResult();
        Long strangerProperty = (Long) em.createNativeQuery("INSERT INTO property (plan_id) VALUES (:pid) RETURNING id")
                .setParameter("pid", strangerPlanId)
                .getSingleResult();
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.save(ownerId, planId, withProperty(strangerProperty)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PROPERTY_NOT_IN_PLAN);
    }

    // F04 잔금일 부분 수정

    @Test
    @DisplayName("잔금일만 수정하면 상담일 등 다른 계약 값은 보존한다")
    void should_update_only_balance_date_and_preserve_other_fields() {
        // 상담일이 들어간 계약을 먼저 저장한다.
        SaveRequest request = new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                null,
                null,
                BALANCE,
                null,
                null,
                null,
                BALANCE.minusDays(30),
                null,
                null,
                false);
        service.save(ownerId, planId, request);
        em.flush();
        em.clear();

        LocalDate newBalance = BALANCE.plusDays(10);
        ContractGuide guide = service.saveBalanceDate(ownerId, planId, newBalance);

        // 잔금일은 새 값으로 바뀌고(착수일 = 잔금일 - 21),
        assertThat(guide.preConsult().recommendedStartDate()).isEqualTo(newBalance.minusDays(21));
        // 상담 사실은 그대로 보존된다(전체 덮어쓰기였다면 지워졌을 값).
        assertThat(guide.preConsult().consulted()).isTrue();
    }

    @Test
    @DisplayName("계약이 없으면 잔금일만 수정할 수 없다")
    void should_reject_balance_date_update_without_contract() {
        assertThatThrownBy(() -> service.saveBalanceDate(ownerId, planId, BALANCE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CONTRACT_NOT_FOUND);
    }

    /** 서울, 보증부월세 3천만원, 시세 3억, 공시가 2.5억, 선순위 없음, 문제 없음. */
    private static PropertyFacts cleanFacts() {
        return new PropertyFacts(
                LeaseType.WOLSE,
                30_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);
    }

    private SaveRequest withProperty(Long propertyId) {
        return new SaveRequest(
                propertyId,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                null,
                null,
                BALANCE,
                null,
                null,
                null,
                null,
                null,
                null,
                false);
    }

    private SaveRequest settledOn(LocalDate paid, LocalDate moveInReport, LocalDate confirmed) {
        return new SaveRequest(
                null,
                LeaseType.WOLSE,
                30_000_000L,
                600_000L,
                0L,
                3_000_000L,
                BALANCE.minusMonths(1),
                BALANCE,
                BALANCE,
                confirmed,
                moveInReport,
                null,
                null,
                paid,
                false);
    }
}
