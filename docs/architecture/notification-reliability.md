# 알림 전달 안정성

알림은 PostgreSQL에 `PENDING`으로 먼저 저장하고 트랜잭션 커밋 후 Redis Stream에 발행한다. Redis
발행이 실패한 행은 5분 뒤 `PendingNotificationRepublisher`가 다시 넣는다. 같은 알림 ID가 스트림에
여러 번 들어가도 이미 `SENT` 또는 `READ`인 알림은 다시 보내지 않는다.

Firebase가 `UNREGISTERED` 또는 `INVALID_ARGUMENT`로 판정한 디바이스 토큰은 전송 결과 처리 중
삭제한다. 프론트는 로그아웃 전에 `DELETE /api/v1/notifications/tokens`에 현재 토큰을 보내 해당
기기와 계정의 연결을 끊는다.

매일 마감 스캔은 기존 3루 계약 마일스톤에 더해 다음 입주 후 알림을 만든다.

- 잔금 지급 다음 날: 반환보증 가입 대상·기한 확인
- 계약 종료 6개월 전: 갱신·퇴거 결정 기간 시작
- 계약 종료 2개월 전: 임대인 의사표시 마감 안내

각 알림은 계약 ID·기준일을 포함한 `dedupKey`로 중복 생성을 막는다.
