-- 약관에 적용 범위를 준다.
--
-- 지금까지 `is_required` 하나가 두 가지 뜻을 겸했다. `RequiredTermsAgreementFilter` 가
-- 필수 약관 미동의를 **모든 요청**에서 403 으로 끊는데, 기능별 약관을 필수로 넣으면
-- 그 기능을 쓰지도 않는 사용자까지 앱 전체가 막힌다.
--
-- 그래서 "앱을 쓰려면 반드시"(SERVICE)와 "이 기능을 쓰려면 반드시"(OPEN_BANKING)를
-- 나눈다. 필터는 SERVICE 만 본다.
ALTER TABLE terms
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'SERVICE';

ALTER TABLE terms
    ADD CONSTRAINT ck_terms_scope CHECK (scope IN ('SERVICE', 'OPEN_BANKING'));

COMMENT ON COLUMN terms.scope IS '약관 적용 범위. SERVICE 는 앱 전체 필수(필터가 막는다), 그 밖은 기능별';

-- 조회가 범위로 먼저 좁혀지므로 인덱스도 범위를 앞에 둔다.
DROP INDEX IF EXISTS ix_terms_active;
CREATE INDEX ix_terms_active ON terms (scope, is_required, effective_from, effective_to);

-- 오픈뱅킹 약관. 지금까지 프론트 화면에 문자열로만 있었고 서버는 존재를 몰랐다.
-- 선택 항목(분석·저장)은 동의하지 않아도 연동이 된다.
INSERT INTO terms (code, version, title, is_required, content_url, effective_from, scope)
VALUES
    ('OPEN_BANKING_SERVICE', '1.0', '오픈뱅킹 서비스 이용약관', true, '/terms/open-banking', DATE '2026-09-09', 'OPEN_BANKING'),
    ('OPEN_BANKING_INQUIRY', '1.0', '금융정보 조회 동의', true, '/terms/open-banking-inquiry', DATE '2026-09-09', 'OPEN_BANKING'),
    ('OPEN_BANKING_THIRD_PARTY', '1.0', '개인정보 제3자 제공 동의', true, '/terms/open-banking-third-party', DATE '2026-09-09', 'OPEN_BANKING'),
    ('OPEN_BANKING_ANALYSIS', '1.0', '조회 결과 분석·저장 동의', false, '/terms/open-banking-analysis', DATE '2026-09-09', 'OPEN_BANKING')
ON CONFLICT (code, version) DO NOTHING;
