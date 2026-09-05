package com.homerun.domain.notification.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 알림 파이프라인 설정. 스트림 키/그룹, 재처리 리퍼, 마감·정체 판정 기준을 한곳에 모은다.
 * 기본값은 코드에 두고 application.yaml 로 오버라이드한다.
 */
@ConfigurationProperties("notification")
public record NotificationProperties(
        @DefaultValue Stream stream,
        @DefaultValue Reaper reaper,
        @DefaultValue Deadline deadline,
        @DefaultValue StaleApplication staleApplication) {

    public record Stream(
            @DefaultValue("notifications:stream") String key,
            @DefaultValue("notification-workers") String group,
            @DefaultValue("worker-1") String consumer,
            @DefaultValue("10") int batchSize,
            @DefaultValue("1s") Duration pollInterval) {}

    public record Reaper(
            @DefaultValue("5m") Duration minIdle,
            @DefaultValue("3") int maxRetries,
            @DefaultValue("100") int batchSize) {}

    /** 마감 며칠 전에 알릴지. 기준일 대비 남은 일수(D-N) 목록. */
    public record Deadline(@DefaultValue({"7", "3", "1"}) List<Integer> offsetDays) {}

    /** 신청 제출(SUBMITTED/SCREENING) 후 이 일수가 지나도 결과가 없으면 넛지한다. */
    public record StaleApplication(@DefaultValue("14") int staleDays) {}
}
