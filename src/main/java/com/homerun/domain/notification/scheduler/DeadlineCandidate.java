package com.homerun.domain.notification.scheduler;

import com.homerun.domain.notification.type.NotificationType;
import java.util.Map;

/**
 * 스케줄러가 만들 알림 한 건의 재료. 각 {@link DeadlineSource} 가 도메인 데이터를 훑어 이 목록을
 * 돌려주면 스케줄러가 프로듀서로 발행한다.
 *
 * @param dedupKey 멱등키. 같은 마감/넛지를 여러 번 돌려도 1건만 만들도록 막는다.
 */
public record DeadlineCandidate(
        Long memberId, NotificationType type, String title, String body, Map<String, String> data, String dedupKey) {}
