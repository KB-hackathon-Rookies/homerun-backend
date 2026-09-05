package com.homerun.domain.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 알림 스트림 컨슈머 그룹을 애플리케이션 시작 시 만들어 둔다.
 *
 * <p>스트림이 없으면 XADD 로 만들 수 없으므로 초기 레코드 하나로 스트림을 생성한 뒤, 그룹을
 * latest 오프셋에서 만든다(초기 레코드는 그룹에 전달되지 않는다). 이미 그룹이 있으면 BUSYGROUP
 * 예외를 무시한다.
 */
@Configuration
public class RedisStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConfig.class);

    @Bean
    ApplicationRunner notificationStreamGroupInitializer(
            StringRedisTemplate redisTemplate, NotificationProperties properties) {
        return args -> {
            String key = properties.stream().key();
            String group = properties.stream().group();
            try {
                if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
                    redisTemplate.opsForStream().add(key, java.util.Map.of("_init", "1"));
                }
                redisTemplate.opsForStream().createGroup(key, ReadOffset.latest(), group);
                log.info("알림 컨슈머 그룹 생성: key={}, group={}", key, group);
            } catch (RuntimeException exception) {
                // BUSYGROUP: 이미 존재. 정상 흐름이다.
                log.debug("알림 컨슈머 그룹이 이미 존재하거나 초기화 skip: {}", exception.getMessage());
            }
        };
    }
}
