package com.homerun.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.contract.dto.response.ContractEntryResponse;
import com.homerun.domain.contract.repository.LeaseContractRepository;
import com.homerun.domain.contract.service.ContractEntryService;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.LeaseType;
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
class ContractEntryServiceTest {

    private final ContractEntryService service;
    private final LeaseContractRepository contracts;
    private final EntityManager em;

    private Long memberId;
    private Long planId;

    ContractEntryServiceTest(
            @Autowired ContractEntryService service,
            @Autowired LeaseContractRepository contracts,
            @Autowired EntityManager em) {
        this.service = service;
        this.contracts = contracts;
        this.em = em;
    }

    @BeforeEach
    void setUp() {
        memberId = newMember();
        planId = (Long)
                em.createNativeQuery("""
                        INSERT INTO plan (user_id, lease_type)
                        VALUES (:memberId, 'JEONSE')
                        RETURNING id
                        """).setParameter("memberId", memberId).getSingleResult();
    }

    @Test
    @DisplayName("2루 최종 매물과 상담 결과로 3루 계약 초안을 만든다")
    void should_prefill_contract_from_second_base_decision() {
        createFinalDecision("YOUTH_BEOTIMMOK", "HUG_SAFE_JEONSE", "MULTI_FAMILY");

        ContractEntryResponse response = service.prefill(memberId, planId);

        assertThat(response.propertyId()).isNotNull();
        assertThat(response.propertyAddress()).isEqualTo("서울특별시 마포구 홈런로 1");
        assertThat(response.bankName()).isEqualTo("KB국민은행");
        assertThat(response.leaseType()).isEqualTo(LeaseType.JEONSE);
        assertThat(response.deposit()).isEqualTo(120_000_000L);
        assertThat(response.loanProductKind()).isEqualTo(LoanProductKind.FUND_YOUTH);
        assertThat(response.collateralMethod()).isEqualTo(ContractCollateralMethod.HUG_SAFE_JEONSE);
        assertThat(response.houseType()).isEqualTo(HouseType.MULTI_FAMILY);
        assertThat(contracts.findByPlanId(planId)).isPresent();
    }

    @Test
    @DisplayName("2루 선택값을 다시 불러와도 3루에서 정한 잔금일과 월세는 유지한다")
    void should_keep_third_base_values_when_prefilling_again() {
        createFinalDecision("BANK_LOAN", "OTHER", "APARTMENT");
        service.prefill(memberId, planId);

        em.createNativeQuery("""
                        UPDATE lease_contract
                        SET monthly_rent = 550000, balance_date = :balanceDate
                        WHERE plan_id = :planId
                        """)
                .setParameter("balanceDate", LocalDate.of(2026, 10, 31))
                .setParameter("planId", planId)
                .executeUpdate();
        em.flush();
        em.clear();

        ContractEntryResponse response = service.prefill(memberId, planId);

        assertThat(response.collateralMethod()).isEqualTo(ContractCollateralMethod.OTHER);
        assertThat(contracts.findByPlanId(planId).orElseThrow().getMonthlyRent())
                .isEqualTo(550_000L);
        assertThat(contracts.findByPlanId(planId).orElseThrow().getBalanceDate())
                .isEqualTo(LocalDate.of(2026, 10, 31));
    }

    private Long newMember() {
        return (Long) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, nickname)
                        VALUES ('KAKAO', 'contract-entry-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult();
    }

    private void createFinalDecision(String loanProduct, String collateralMethod, String houseType) {
        Long propertyId = (Long) em.createNativeQuery("""
                        INSERT INTO property (plan_id, road_address, house_type, deposit)
                        VALUES (:planId, '서울특별시 마포구 홈런로 1', :houseType, 120000000)
                        RETURNING id
                        """)
                .setParameter("planId", planId)
                .setParameter("houseType", houseType)
                .getSingleResult();
        Long consultationId = (Long) em.createNativeQuery("""
                        INSERT INTO bank_consultation (
                            plan_id, property_id, bank_name, branch_name, result_status,
                            loan_product, collateral_method, approved_limit, quoted_rate, consulted_at
                        ) VALUES (
                            :planId, :propertyId, 'KB국민은행', '마포지점', 'POSSIBLE',
                            :loanProduct, :collateralMethod, 100000000, 2.500, DATE '2026-09-01'
                        ) RETURNING id
                        """)
                .setParameter("planId", planId)
                .setParameter("propertyId", propertyId)
                .setParameter("loanProduct", loanProduct)
                .setParameter("collateralMethod", collateralMethod)
                .getSingleResult();
        em.createNativeQuery("""
                        INSERT INTO property_decision (plan_id, property_id, consultation_id, decided_at, decision_revision)
                        VALUES (:planId, :propertyId, :consultationId, now(), 1)
                        """)
                .setParameter("planId", planId)
                .setParameter("propertyId", propertyId)
                .setParameter("consultationId", consultationId)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
