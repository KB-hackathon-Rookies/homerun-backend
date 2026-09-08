package com.homerun.global.external.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 플래그가 꺼져 있으면 모킹 구현이 컨텍스트에 아예 없어야 한다. */
class OpenBankingClientSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void should_useRealClient_whenFlagIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MockDataOpenBankingClient.class);
            assertThat(context.getBean(OpenBankingClient.class)).isInstanceOf(HttpOpenBankingClient.class);
        });
    }

    @Test
    void should_useRealClient_whenFlagIsFalse() {
        contextRunner
                .withPropertyValues("external-api.open-banking.mock-data=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MockDataOpenBankingClient.class);
                    assertThat(context.getBean(OpenBankingClient.class)).isInstanceOf(HttpOpenBankingClient.class);
                });
    }

    @Test
    void should_useMockDataClient_whenFlagIsTrue() {
        contextRunner
                .withPropertyValues("external-api.open-banking.mock-data=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MockDataOpenBankingClient.class);
                    assertThat(context.getBean(OpenBankingClient.class)).isInstanceOf(MockDataOpenBankingClient.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(MockDataOpenBankingClient.class)
    static class TestConfiguration {

        @Bean
        HttpOpenBankingClient httpOpenBankingClient() {
            return mock(HttpOpenBankingClient.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
