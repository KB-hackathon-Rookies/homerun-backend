package com.homerun.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.contract.controller.ContractChecklistGuideController;
import com.homerun.domain.contract.dto.response.ContractChecklistGuideResponse.Item;
import com.homerun.domain.contract.service.ContractChecklistGuideService;
import com.homerun.domain.contract.type.ContractChecklistPhase;
import com.homerun.global.exception.GlobalExceptionHandler;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ContractChecklistGuideTest {
    private final ContractChecklistGuideService service = new ContractChecklistGuideService();
    private final MockMvcTester mvc = MockMvcTester.of(
            List.of(new ContractChecklistGuideController(service)),
            builder -> builder.setControllerAdvice(new GlobalExceptionHandler()).build());

    @Test
    void should_returnAllItemsInStableOrder_withoutPlan() {
        var response = service.get(null);
        assertThat(response.items()).hasSize(8);
        assertThat(response.items()).extracting(Item::sequence).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(response.items()).extracting(Item::code).doesNotHaveDuplicates();
        assertThat(response.notice()).contains("판정하지 않으며");
        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.action()).isNotBlank();
            assertThat(item.caution()).isNotBlank();
            assertThat(URI.create(item.officialUrl()).getScheme()).isEqualTo("https");
            assertThat(URI.create(item.officialUrl()).getHost())
                    .isIn("www.iros.go.kr", "www.gov.kr", "onestop.khug.or.kr");
        });
    }

    @ParameterizedTest
    @EnumSource(ContractChecklistPhase.class)
    void should_filterByPhase(ContractChecklistPhase phase) {
        assertThat(service.get(phase).items()).isNotEmpty().allMatch(item -> item.phase() == phase);
        assertThat(mvc.get().uri("/api/v1/contract-checklist").param("phase", phase.name()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.items[0].phase")
                .isEqualTo(phase.name());
    }

    @Test
    void should_wrapResponse_when_phaseIsOmitted() {
        assertThat(mvc.get().uri("/api/v1/contract-checklist"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.data.items")
                .asArray()
                .hasSize(8);
    }

    @Test
    void should_return400_when_phaseIsInvalid() {
        assertThat(mvc.get().uri("/api/v1/contract-checklist").param("phase", "INVALID"))
                .hasStatus(400);
    }
}
