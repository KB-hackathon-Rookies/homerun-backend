package com.homerun.domain.plan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.homerun.domain.plan.service.OpenBankingPlanSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 데모 전용 mock 컨트롤러는 플래그가 켜졌을 때만 존재하고, 진짜 동기화 컨트롤러는 언제나 존재한다.
 *
 * <p>빈이 없으면 매핑도 없다. 그래서 여기서 빈의 유무를 고정하는 것이 곧 운영에서 경로가 열려
 * 있지 않다는 뜻이다. HTTP 404 까지 확인하는 것은 {@code MockOpenBankingPlanSyncEndpointTest} 다.
 */
class MockOpenBankingPlanSyncRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("플래그가 없으면 데모용 mock 컨트롤러가 컨텍스트에 아예 없다")
    void should_notRegisterMockController_whenFlagIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MockOpenBankingPlanSyncController.class);
            assertThat(context).hasSingleBean(OpenBankingPlanSyncController.class);
        });
    }

    @Test
    @DisplayName("플래그가 false 면 데모용 mock 컨트롤러가 컨텍스트에 아예 없다")
    void should_notRegisterMockController_whenFlagIsFalse() {
        contextRunner
                .withPropertyValues("external-api.open-banking.mock-data=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MockOpenBankingPlanSyncController.class);
                    assertThat(context).hasSingleBean(OpenBankingPlanSyncController.class);
                });
    }

    @Test
    @DisplayName("플래그가 true 면 데모용 mock 컨트롤러도 함께 올라온다")
    void should_registerMockController_whenFlagIsTrue() {
        contextRunner
                .withPropertyValues("external-api.open-banking.mock-data=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MockOpenBankingPlanSyncController.class);
                    assertThat(context).hasSingleBean(OpenBankingPlanSyncController.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MockOpenBankingPlanSyncController.class, OpenBankingPlanSyncController.class})
    static class TestConfiguration {

        @Bean
        OpenBankingPlanSyncService openBankingPlanSyncService() {
            return mock(OpenBankingPlanSyncService.class);
        }
    }
}
