package com.homerun.domain.notification.type;

/**
 * 알림 한 건의 상태.
 *
 * <p>PENDING 으로 만들어져 스트림에 실린다. 컨슈머가 FCM 전송에 성공하면 SENT, 재시도
 * 상한까지 실패하면 리퍼가 FAILED 로 확정한다. READ 는 사용자가 인박스에서 읽음 처리한 것으로,
 * 전송 성공/실패와는 다른 축이지만 조회를 단순하게 하려고 같은 컬럼에 둔다.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    READ
}
