package com.homerun.domain.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.property.dto.request.PropertyFacts;
import com.homerun.domain.property.dto.response.CheckFinding;
import com.homerun.domain.property.dto.response.PropertyVerification;
import com.homerun.domain.property.service.PropertyVerificationService;
import com.homerun.domain.property.type.CheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PropertyVerificationServiceTest {

    private final PropertyVerificationService service;

    PropertyVerificationServiceTest(@Autowired PropertyVerificationService service) {
        this.service = service;
    }

    /** 서울, 전세 2억, 시세 3억, 공시가 2.5억, 선순위 없음, 문제 없음. */
    private static PropertyFacts clean() {
        return new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
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

    private static CheckFinding find(PropertyVerification result, String code) {
        return result.findings().stream()
                .filter(f -> f.checkCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("검증 항목이 없다: " + code));
    }

    @Test
    @DisplayName("문제가 없으면 계약을 막지 않는다")
    void should_allow_contract_when_clean() {
        PropertyVerification result = service.verify(clean());

        assertThat(result.contractable()).isTrue();
        assertThat(result.blocking()).isEmpty();
    }

    @Test
    @DisplayName("위반건축물이면 계약을 막는다")
    void should_block_violation_building() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                true,
                false,
                false,
                false);

        PropertyVerification result = service.verify(facts);

        assertThat(result.contractable()).isFalse();
        assertThat(find(result, "VIOLATION_BUILDING").result()).isEqualTo(CheckResult.BLOCK);
        assertThat(find(result, "VIOLATION_BUILDING").summary()).contains("대출과 보증이 모두 거절");
    }

    @Test
    @DisplayName("신탁등기가 있으면 계약을 막는다")
    void should_block_trust_registration() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                true,
                false,
                false);

        assertThat(service.verify(facts).contractable()).isFalse();
    }

    @Test
    @DisplayName("소유자가 다르면 계약을 막는다")
    void should_block_owner_mismatch() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                false,
                false,
                false,
                false,
                false);

        assertThat(service.verify(facts).contractable()).isFalse();
    }

    @Test
    @DisplayName("전세가율 80% 초과는 막지 않고 경고한다")
    void should_warn_but_not_block_high_ratio() {
        // 선순위 5천만 + 보증금 2억 = 2.5억, 시세 3억 → 83.3%
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                50_000_000L,
                true,
                false,
                false,
                false,
                false);

        PropertyVerification result = service.verify(facts);

        assertThat(find(result, "JEONSE_RATIO").result()).isEqualTo(CheckResult.WARN);
        assertThat(find(result, "JEONSE_RATIO").summary()).contains("83.3");
        assertThat(result.contractable()).isTrue();
    }

    @Test
    @DisplayName("전세가율 기준은 확정 전이라 변경 가능으로 표시한다")
    void should_flag_ratio_threshold_as_provisional() {
        // FCT-119 확정도 REVIEW
        assertThat(find(service.verify(clean()), "JEONSE_RATIO").provisional()).isTrue();
    }

    @Test
    @DisplayName("보증금이 공시가격 126%를 넘으면 반환보증에 가입할 수 없다")
    void should_block_when_over_126_percent() {
        // 공시가 1.5억 × 1.26 = 1.89억 < 보증금 2억
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                150_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);

        PropertyVerification result = service.verify(facts);

        assertThat(find(result, "OFFICIAL_PRICE_126").result()).isEqualTo(CheckResult.BLOCK);
        assertThat(result.contractable()).isFalse();
    }

    @Test
    @DisplayName("서울 보증금 1.65억 이하면 5,500만원까지 최우선변제 대상이다")
    void should_protect_small_tenant_in_seoul() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.WOLSE,
                50_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);

        CheckFinding finding = find(service.verify(facts), "SMALL_TENANT");

        assertThat(finding.result()).isEqualTo(CheckResult.PASS);
        assertThat(finding.summary()).contains("55,000,000원");
    }

    @Test
    @DisplayName("보증금이 변제 한도를 넘으면 보호되지 않는 금액을 알려준다")
    void should_report_unprotected_amount() {
        // 보증금 1억, 서울 한도 5,500만 → 4,500만 미보호
        PropertyFacts facts = new PropertyFacts(
                LeaseType.WOLSE,
                100_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);

        CheckFinding finding = find(service.verify(facts), "SMALL_TENANT");

        assertThat(finding.result()).isEqualTo(CheckResult.WARN);
        assertThat(finding.summary()).contains("45,000,000원은 보호되지 않는다");
    }

    @Test
    @DisplayName("보증금이 지역 상한을 넘으면 최우선변제 대상이 아니다")
    void should_report_not_small_tenant() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);

        assertThat(find(service.verify(facts), "SMALL_TENANT").summary()).contains("최우선변제 대상이 아니다");
    }

    @Test
    @DisplayName("경기도는 과밀억제 여부를 몰라 판정하지 않는다")
    void should_not_judge_unknown_region() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.WOLSE,
                50_000_000L,
                "41135",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);

        assertThat(find(service.verify(facts), "SMALL_TENANT").result()).isEqualTo(CheckResult.UNKNOWN);
    }

    @Test
    @DisplayName("다가구는 막지 않고 은행 사전상담을 권한다")
    void should_warn_multi_household() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                true,
                false);

        PropertyVerification result = service.verify(facts);

        assertThat(find(result, "MULTI_HOUSEHOLD").result()).isEqualTo(CheckResult.WARN);
        assertThat(find(result, "MULTI_HOUSEHOLD").action()).contains("전입세대확인서");
        assertThat(result.contractable()).isTrue();
    }

    @Test
    @DisplayName("임대인 체납은 조세채권 우선을 알린다")
    void should_warn_landlord_tax_unpaid() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                false,
                true);

        assertThat(find(service.verify(facts), "LANDLORD_TAX").summary()).contains("우선 변제");
    }

    @Test
    @DisplayName("확인하지 못한 항목은 통과가 아니라 UNKNOWN 이다")
    void should_not_treat_unknown_as_pass() {
        // 지역도 시세도 모르는 상태. 보증금만 안다
        PropertyFacts facts =
                new PropertyFacts(LeaseType.JEONSE, 200_000_000L, null, null, null, null, null, null, null, null, null);

        PropertyVerification result = service.verify(facts);

        assertThat(result.overall()).isEqualTo(CheckResult.UNKNOWN);
        assertThat(result.contractable()).isTrue();
        assertThat(result.findings()).allSatisfy(f -> assertThat(f.result()).isEqualTo(CheckResult.UNKNOWN));
        // 항목이 목록에서 빠지면 전체 판정이 PASS 로 보인다. 개수까지 확인한다.
        assertThat(result.findings())
                .extracting(CheckFinding::checkCode)
                .contains("OWNER_MATCH", "VIOLATION_BUILDING", "TRUST_REGISTRATION", "MULTI_HOUSEHOLD", "LANDLORD_TAX");
    }

    @Test
    @DisplayName("선순위채권을 모르면 전세가율을 계산하지 않는다")
    void should_not_compute_ratio_without_senior_debt() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                null,
                true,
                false,
                false,
                false,
                false);

        CheckFinding finding = find(service.verify(facts), "JEONSE_RATIO");

        assertThat(finding.result()).isEqualTo(CheckResult.UNKNOWN);
        assertThat(finding.summary()).contains("선순위채권");
    }

    @Test
    @DisplayName("다가구 여부를 모르면 목록에서 빠지지 않고 UNKNOWN 으로 남는다")
    void should_keep_unknown_multi_household_in_findings() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                null,
                false);

        PropertyVerification result = service.verify(facts);

        assertThat(find(result, "MULTI_HOUSEHOLD").result()).isEqualTo(CheckResult.UNKNOWN);
        assertThat(result.overall()).isNotEqualTo(CheckResult.PASS);
    }

    @Test
    @DisplayName("선순위채권이 크면 담보인정비율에서 막힌다")
    void should_block_when_ltv_exceeded() {
        // 시세 3억 × 90% = 2.7억. 보증금 2억 + 선순위 1억 = 3억 초과
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                100_000_000L,
                true,
                false,
                false,
                false,
                false);

        CheckFinding finding = find(service.verify(facts), "OFFICIAL_PRICE_126");

        assertThat(finding.result()).isEqualTo(CheckResult.BLOCK);
        assertThat(finding.summary()).contains("선순위채권 합계");
        assertThat(service.verify(facts).contractable()).isFalse();
    }

    @Test
    @DisplayName("보증금만 낮아도 선순위채권이 크면 통과시키지 않는다")
    void should_not_pass_on_deposit_alone() {
        // 공시가 2.5억 × 1.26 = 3.15억 이라 보증금 2억은 통과하지만
        // 담보인정 2.7억을 보증금+선순위 3억이 넘는다
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                100_000_000L,
                true,
                false,
                false,
                false,
                false);

        assertThat(find(service.verify(facts), "OFFICIAL_PRICE_126").result()).isNotEqualTo(CheckResult.PASS);
    }

    @Test
    @DisplayName("충북 같은 그 밖의 지역은 OTHER 구간으로 판정한다")
    void should_classify_other_region() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.WOLSE,
                50_000_000L,
                "43111",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);

        CheckFinding finding = find(service.verify(facts), "SMALL_TENANT");

        assertThat(finding.result()).isNotEqualTo(CheckResult.UNKNOWN);
        assertThat(finding.summary()).contains("그 밖의 지역");
    }

    @Test
    @DisplayName("전체 판정은 가장 나쁜 항목을 따른다")
    void should_take_worst_result_as_overall() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                true,
                false,
                true,
                false);

        // WARN(다가구)과 BLOCK(위반건축물)이 함께 있으면 BLOCK
        assertThat(service.verify(facts).overall()).isEqualTo(CheckResult.BLOCK);
    }

    @Test
    @DisplayName("막는 항목이 목록 앞에 온다")
    void should_sort_blocking_first() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.JEONSE,
                200_000_000L,
                "11620",
                300_000_000L,
                250_000_000L,
                0L,
                true,
                true,
                false,
                false,
                false);

        assertThat(service.verify(facts).findings().get(0).result()).isEqualTo(CheckResult.BLOCK);
    }

    @Test
    @DisplayName("보증금이 있는 월세도 공시가격 기준으로 반환보증 가능성을 본다")
    void should_check_official_price_for_wolse_with_deposit() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.WOLSE,
                200_000_000L,
                "11620",
                300_000_000L,
                100_000_000L,
                0L,
                true,
                false,
                false,
                false,
                false);

        assertThat(find(service.verify(facts), "OFFICIAL_PRICE_126").result()).isEqualTo(CheckResult.BLOCK);
    }

    @Test
    @DisplayName("보증금이 없는 순수 월세는 반환보증을 따지지 않는다")
    void should_skip_official_price_without_deposit() {
        PropertyFacts facts = new PropertyFacts(
                LeaseType.WOLSE, 0L, "11620", 300_000_000L, 250_000_000L, 0L, true, false, false, false, false);

        assertThat(service.verify(facts).findings())
                .noneMatch(f -> f.checkCode().equals("OFFICIAL_PRICE_126"));
    }
}
