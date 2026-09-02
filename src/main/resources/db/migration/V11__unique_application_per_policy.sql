-- 같은 계획에서 같은 정책을 두 번 신청할 수 없다.
--
-- 서비스에서 exists 로 먼저 거르지만, 조회와 저장 사이에 다른 요청이 끼어들면 둘 다
-- 통과해 중복 행이 생긴다. 도메인 불변식이면 DB 가 최종 방어선이어야 한다.

ALTER TABLE application
    ADD CONSTRAINT uq_application_plan_policy UNIQUE (plan_id, policy_id);

-- 가구원 한 명에게 살아 있는 동의 링크는 하나만 둔다.
--
-- 서비스에서 발급 전에 기존 토큰을 UPDATE 로 폐기하지만, 활성 토큰이 없는 상태에서 두
-- 요청이 동시에 들어오면 둘 다 0행을 폐기한 뒤 각자 INSERT 해 링크가 두 개 살아난다.
-- 부분 유니크 인덱스로 DB 가 막는다.

CREATE UNIQUE INDEX uq_consent_token_active
    ON consent_token (member_id)
    WHERE used_at IS NULL AND revoked_at IS NULL AND member_id IS NOT NULL;
