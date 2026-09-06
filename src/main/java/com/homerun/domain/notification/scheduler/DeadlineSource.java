package com.homerun.domain.notification.scheduler;

import java.time.LocalDate;
import java.util.List;

/**
 * 마감/넛지 알림 후보를 만드는 전략. 도메인마다 하나씩 두고 스케줄러가 모두 순회한다.
 *
 * <p>새 알림 트리거(예: 진짜 정책 접수 마감)가 생기면 이 인터페이스 구현을 추가하기만 하면
 * 스케줄러는 그대로 둔다.
 */
public interface DeadlineSource {

    List<DeadlineCandidate> collect(LocalDate today);
}
