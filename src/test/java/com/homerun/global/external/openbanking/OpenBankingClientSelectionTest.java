package com.homerun.global.external.openbanking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.homerun.domain.openbanking.type.Persona;
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
    void should_notRegisterPersonaSelection_whenFlagIsFalse() {
        contextRunner
                .withPropertyValues("external-api.open-banking.mock-data=false")
                .run(context -> assertThat(context).doesNotHaveBean(MockPersonaSelection.class));
    }

    @Test
    void should_useMockDataClient_whenFlagIsTrue() {
        contextRunner
                .withPropertyValues("external-api.open-banking.mock-data=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(MockDataOpenBankingClient.class);
                    assertThat(context.getBean(OpenBankingClient.class)).isInstanceOf(MockDataOpenBankingClient.class);
                    // 페르소나를 고른 적 없는 회원이 보게 될 시연 기본값.
                    assertThat(context.getBean(MockPersonaSelection.class).defaultPersona())
                            .isEqualTo(Persona.KIM_FIRST);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MockDataOpenBankingClient.class, MockPersonaSelection.class})
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
