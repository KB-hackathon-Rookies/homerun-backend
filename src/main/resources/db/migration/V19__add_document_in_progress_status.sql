-- EVI-01-04 서류 준비 상태.
--
-- 지금 상태는 NEEDED · ISSUED · SUBMITTED · EXPIRED 인데 "준비 중"이 없다.
-- 재직증명서처럼 회사에 요청해 두고 며칠 기다리는 서류가 여기 해당한다. 요청해 둔 것을
-- NEEDED 로 두면 사용자가 매번 다시 요청해야 하는 줄 안다.

ALTER TABLE user_document DROP CONSTRAINT ck_doc_status;

ALTER TABLE user_document
    ADD CONSTRAINT ck_doc_status
        CHECK (status IN ('NEEDED', 'IN_PROGRESS', 'ISSUED', 'SUBMITTED', 'EXPIRED'));

-- 같은 계획에서 같은 서류를 두 번 관리하지 않는다. 정책별 제출은 policy_id 로 갈리므로
-- 정책이 없는 행(계획 단위 보유)만 유일하면 된다.
CREATE UNIQUE INDEX uq_user_document_plan_type
    ON user_document (plan_id, document_type_id)
    WHERE policy_id IS NULL;

COMMENT ON COLUMN user_document.status IS '미준비 · 준비 중 · 준비 완료 · 제출 완료 · 재발급 필요';
COMMENT ON COLUMN user_document.expires_at IS '인정 기간이 있는 서류만 채운다. 모르면 비워 둔다';
