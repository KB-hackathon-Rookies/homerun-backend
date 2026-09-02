package com.homerun.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간을 주입 가능하게 만든다.
 *
 * <p>정책 판정은 시행일에 따라 결과가 달라진다. LocalDate.now() 를 직접 부르면 연도가
 * 바뀌는 경계를 테스트할 수 없다.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
