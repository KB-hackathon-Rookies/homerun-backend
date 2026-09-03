package com.homerun.domain.fact;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 서류 수수료 팩트가 열람과 교부를 구분하는지 본다.
 *
 * <p>둘을 한 값으로 두면 읽는 쪽이 자기 맥락에 갖다 쓴다. 실제로 ISS-01 시드가 열람가
 * 300원을 제출용 수수료로 넣었다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DocumentFeeFactTest {

    private final FactRegistry facts;

    DocumentFeeFactTest(@Autowired FactRegistry facts) {
        this.facts = facts;
    }

    @Test
    @DisplayName("전입세대확인서는 열람과 교부가 다른 팩트다")
    void should_separate_resident_list_inspection_from_issuance() {
        Fact inspection = facts.require("FCT-100");
        Fact issuance = facts.require("FCT-164");

        assertThat(inspection.requireWon()).isEqualTo(300);
        assertThat(issuance.requireWon()).isEqualTo(400);
        assertThat(inspection.item()).contains("열람");
        assertThat(issuance.item()).contains("교부");
    }

    @Test
    @DisplayName("등기사항전부증명서도 열람과 발급이 다른 팩트다")
    void should_separate_registry_inspection_from_issuance() {
        assertThat(facts.require("FCT-099").requireWon()).isEqualTo(700);
        assertThat(facts.require("FCT-165").requireWon()).isEqualTo(1000);
    }

    @Test
    @DisplayName("어느 쪽 수수료인지 항목 이름만 봐도 알 수 있다")
    void should_name_which_fee_it_is() {
        for (String code : java.util.List.of("FCT-099", "FCT-100", "FCT-164", "FCT-165", "FCT-166", "FCT-167")) {
            String item = facts.require(code).item();

            assertThat(item).as(code).containsAnyOf("열람", "교부", "발급");
        }
    }

    @Test
    @DisplayName("지역마다 갈리는 수수료는 확정으로 두지 않는다")
    void should_not_confirm_locally_varying_fee() {
        // 건축물대장 무인발급기 수수료는 자치단체 조례에 따라 달라진다. CONFIRMED 로 두면
        // 판정이 이 값을 확정치로 쓴다.
        assertThat(facts.require("FCT-167").provisional()).isTrue();
    }
}
