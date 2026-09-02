-- 같은 계획에서 같은 정책을 두 번 신청할 수 없다.
--
-- 서비스에서 exists 로 먼저 거르지만, 조회와 저장 사이에 다른 요청이 끼어들면 둘 다
-- 통과해 중복 행이 생긴다. 도메인 불변식이면 DB 가 최종 방어선이어야 한다.

ALTER TABLE application
    ADD CONSTRAINT uq_application_plan_policy UNIQUE (plan_id, policy_id);
