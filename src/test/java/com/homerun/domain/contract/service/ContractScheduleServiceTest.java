package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.dto.response.ContractScheduleResponse;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.type.ApplicationMethod;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.LeaseType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractScheduleServiceTest {

    private static final LocalDate BALANCE_DATE = LocalDate.of(2026, 11, 20);
    private final ContractScheduleService service = new ContractScheduleService(null, null);

    @Test
    @DisplayName("기금대출과 다가구·방문 신청 조건에 맞춰 잔금일 역산 일정을 만든다")
    void builds_conditional_schedule_from_balance_date() {
        LeaseContract contract = contract(
                BALANCE_DATE,
                LoanProductKind.FUND_YOUTH,
                ContractCollateralMethod.HF,
                ApplicationMethod.BANK_VISIT,
                HouseType.MULTI_FAMILY);

        ContractScheduleResponse response = service.build(contract);

        assertThat(response.milestones())
                .filteredOn(item -> item.code().equals("LOAN_PREPARATION"))
                .extracting(ContractScheduleResponse.Milestone::dueDate)
                .containsExactly(BALANCE_DATE.minusDays(30));
        assertThat(response.milestones())
                .extracting(ContractScheduleResponse.Milestone::code)
                .contains(
                        "BANK_VISIT_RESERVATION", "TENANT_PRIORITY_CHECK", "REGISTRY_RECHECK", "RETURN_GUARANTEE_JOIN")
                .doesNotContain("ONLINE_APPLICATION_CHECK", "GUARANTEE_FEE_SUPPORT");
    }

    @Test
    @DisplayName("HUG 안심전세는 반환보증 가입 대신 보증료 지원 일정을 만든다")
    void branches_hug_schedule() {
        LeaseContract contract = contract(
                BALANCE_DATE,
                LoanProductKind.BANK,
                ContractCollateralMethod.HUG_SAFE_JEONSE,
                ApplicationMethod.ONLINE,
                HouseType.APARTMENT);

        ContractScheduleResponse response = service.build(contract);

        assertThat(response.milestones())
                .extracting(ContractScheduleResponse.Milestone::code)
                .contains("ONLINE_APPLICATION_CHECK", "GUARANTEE_FEE_SUPPORT")
                .doesNotContain("BANK_VISIT_RESERVATION", "TENANT_PRIORITY_CHECK", "RETURN_GUARANTEE_JOIN");
    }

    @Test
    @DisplayName("잔금일이 없으면 임의 일정을 만들지 않고 입력 안내를 준다")
    void warns_when_balance_date_is_missing() {
        ContractScheduleResponse response = service.build(contract(
                null,
                LoanProductKind.BANK,
                ContractCollateralMethod.NONE,
                ApplicationMethod.ONLINE,
                HouseType.APARTMENT));

        assertThat(response.milestones()).isEmpty();
        assertThat(response.warnings()).isNotEmpty();
    }

    private LeaseContract contract(
            LocalDate balanceDate,
            LoanProductKind product,
            ContractCollateralMethod collateral,
            ApplicationMethod application,
            HouseType houseType) {
        LeaseContract contract = new LeaseContract(1L, LeaseType.JEONSE, 200_000_000L, 0L);
        contract.overwrite(
                10L,
                LeaseType.JEONSE,
                200_000_000L,
                0L,
                0L,
                20_000_000L,
                LocalDate.of(2026, 10, 20),
                balanceDate,
                balanceDate,
                null,
                null,
                null,
                null,
                null,
                false,
                product,
                collateral,
                application,
                houseType);
        return contract;
    }
}
