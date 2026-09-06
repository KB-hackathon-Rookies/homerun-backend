package com.homerun.global.external.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SMS 발송 빈 구성. 실 제공사 구현체({@link SmsSender})가 등록돼 있으면 그것을 쓰고, 없으면
 * {@link StubSmsSender} 로 대체한다. 실 구현체를 빈으로 추가하는 순간 스텁은 밀려난다.
 */
@Configuration
public class SmsConfig {

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender stubSmsSender() {
        return new StubSmsSender();
    }
}
