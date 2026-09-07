package com.homerun.domain.fact.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FactRegistryTest {

    private final FactRegistry registry;

    FactRegistryTest(@Autowired FactRegistry registry) {
        this.registry = registry;
    }

    @Test
    @DisplayName("확정된 금액 팩트를 원 단위로 읽는다")
    void should_read_confirmed_money_fact() {
        // FCT-046 대상 월세 한도 연 750만원
        assertThat(registry.won("FCT-046")).isEqualTo(7_500_000L);
    }

    @Test
    @DisplayName("정정된 값도 판정에 쓸 수 있다")
    void should_allow_corrected_fact() {
        // FCT-040 서울 기준임대료 1인. "34만원"이 아니라 369,000원이다
        assertThat(registry.won("FCT-040")).isEqualTo(369_000L);
    }

    @Test
    @DisplayName("폐지된 제도의 팩트는 판정에 쓸 수 없다")
    void should_reject_retired_fact() {
        // FCT-135 중기청 대출은 폐지(RETIRED)됐다. 만료일이 없어 조회는 되지만 판정 금지.
        assertThatThrownBy(() -> registry.require("FCT-135"))
                .isInstanceOf(UnusableFactException.class)
                .hasMessageContaining("RETIRED");
    }

    @Test
    @DisplayName("유효기간(effective_to)이 지난 팩트는 조회에서 빠진다")
    void should_drop_expired_fact() {
        // FCT-004는 V66에서 effective_to 2026-09-06 으로 닫혔다. 그 이후로는 없는 값처럼 다룬다.
        assertThatThrownBy(() -> registry.require("FCT-004")).isInstanceOf(FactNotFoundException.class);
    }

    @Test
    @DisplayName("확정도가 UNKNOWN 인 수치도 막는다")
    void should_reject_unknown_fact() {
        // FCT-136 청년월세 × 전세자금대출 중복 여부. 공식 원문 미확인
        assertThatThrownBy(() -> registry.require("FCT-136")).isInstanceOf(UnusableFactException.class);
    }

    @Test
    @DisplayName("usable 로 판정 가능 여부만 미리 확인할 수 있다")
    void should_report_usability_without_throwing() {
        assertThat(registry.usable("FCT-046")).isTrue();
        assertThat(registry.usable("FCT-004")).isFalse();
    }

    @Test
    @DisplayName("없는 코드는 찾지 못했다고 알린다")
    void should_fail_on_unknown_code() {
        assertThatThrownBy(() -> registry.require("FCT-999")).isInstanceOf(FactNotFoundException.class);
    }

    @Test
    @DisplayName("변경 가능한 수치는 화면 표시가 필요하다고 알린다")
    void should_flag_provisional_fact() {
        // FCT-028 주거안정월세대출 금리. 확정도 REVIEW
        assertThat(registry.require("FCT-028").provisional()).isTrue();
        assertThat(registry.require("FCT-046").provisional()).isFalse();
    }
}
