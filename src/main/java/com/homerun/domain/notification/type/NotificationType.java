package com.homerun.domain.notification.type;

/**
 * 알림의 종류. 프론트가 아이콘·딥링크를 고르고, 스케줄러가 dedup_key 를 만들 때 쓴다.
 *
 * <p>지금은 마감/넛지 계열만 있다. 진단·정책 판정 완료 같은 이벤트 기반 알림이 필요하면
 * 여기에 값을 추가한다.
 */
public enum NotificationType {
    /** 계약 잔금일 등 계약 마감 임박(D-7·D-3·D-1). */
    CONTRACT_DEADLINE,
    /**
     * 신청 결과 대기 지연 넛지. 접수 마감 데이터가 없어 마감 대신, 제출(SUBMITTED/SCREENING) 후
     * N일이 지나도 결과가 없는 건을 알린다. 진짜 접수 마감 컬럼이 생기면 별도 타입으로 분리한다.
     */
    APPLICATION_RESULT_PENDING
}
