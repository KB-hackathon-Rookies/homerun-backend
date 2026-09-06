package com.homerun.domain.contract.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.dto.response.ContractScheduleResponse;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.type.ApplicationMethod;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.LeaseType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractScheduleServiceTest {

    private static final LocalDate BALANCE_DATE = LocalDate.of(2026, 11, 20);
    private final ContractScheduleService service = new ContractScheduleService(
            null, null, Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneId.of("UTC")));

    @Test
    @DisplayName("청년 버팀목·HUG 다가구·방문 신청 조건에 맞춰 잔금일 역산 일정을 만든다")
    void builds_conditional_schedule_from_balance_date() {
        LeaseContract contract = contract(
                BALANCE_DATE,
                LoanProductKind.FUND_YOUTH,
                ContractCollateralMethod.HUG_SAFE_JEONSE,
                ApplicationMethod.BANK_VISIT,
                HouseType.MULTI_FAMILY);

        ContractScheduleResponse response = service.build(contract);

        assertThat(response.milestones())
                .filteredOn(item -> item.code().equals("COMPANY_DOCUMENTS"))
                .extracting(ContractScheduleResponse.Milestone::dueDate)
                .containsExactly(BALANCE_DATE.minusDays(30));
        assertThat(response.milestones())
                .extracting(ContractScheduleResponse.Milestone::code)
                .contains(
                        "COMPANY_DOCUMENTS",
                        "BANK_RESERVATION",
                        "TENANT_HOUSEHOLD_CONFIRMATION",
                        "LOAN_GUARANTEE_APPLICATION",
                        "LOAN_REVIEW",
                        "REGISTRY_RECHECK",
                        "BALANCE_AND_MOVE_IN",
                        "GUARANTEE_FEE_SUPPORT")
                .doesNotContain("RETURN_GUARANTEE_JOIN");
    }

    @Test
    @DisplayName("HUG 아파트는 전입세대 확인 없이 보증료 지원 일정을 만든다")
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
                .contains("BANK_RESERVATION", "GUARANTEE_FEE_SUPPORT")
                .doesNotContain("COMPANY_DOCUMENTS", "TENANT_HOUSEHOLD_CONFIRMATION", "RETURN_GUARANTEE_JOIN");
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

    @Test
    @DisplayName("대출 신청 절대 기한은 잔금일과 예정 입주일 중 빠른 날에서 3개월 뒤다")
    void should_calculate_absolute_application_deadline_from_earlier_planned_date() {
        LeaseContract contract = contract(
                BALANCE_DATE,
                LoanProductKind.BANK,
                ContractCollateralMethod.HF,
                ApplicationMethod.ONLINE,
                HouseType.APARTMENT);
        contract.overwrite(
                10L,
                LeaseType.JEONSE,
                200_000_000L,
                0L,
                0L,
                20_000_000L,
                LocalDate.of(2026, 10, 20),
                BALANCE_DATE,
                BALANCE_DATE.minusDays(2),
                null,
                null,
                null,
                null,
                null,
                false,
                LoanProductKind.BANK,
                ContractCollateralMethod.HF,
                ApplicationMethod.ONLINE,
                HouseType.APARTMENT);

        ContractScheduleResponse response = service.build(contract);

        assertThat(response.applicationDeadline())
                .isEqualTo(BALANCE_DATE.minusDays(2).plusMonths(3));
    }

    @Test
    @DisplayName("잔금일까지 30일 미만이면 압축 일정 경고를 표시한다")
    void should_warn_when_schedule_is_compressed() {
        ContractScheduleService nearBalanceService = new ContractScheduleService(
                null, null, Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("UTC")));

        ContractScheduleResponse response = nearBalanceService.build(contract(
                BALANCE_DATE,
                LoanProductKind.BANK,
                ContractCollateralMethod.NONE,
                ApplicationMethod.ONLINE,
                HouseType.APARTMENT));

        assertThat(response.compressedSchedule()).isTrue();
        assertThat(response.warnings()).anyMatch(message -> message.contains("30일"));
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
