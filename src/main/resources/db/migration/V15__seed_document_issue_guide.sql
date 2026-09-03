-- ISS-01 발급 안내. document_type 시드와 발급방법 테이블.
--
-- 발급방법이 서류마다 여러 개다. 기관·딥링크·준비물·수수료가 방법마다 다르므로
-- document_type 컬럼으로는 담기지 않는다.
--
-- 수수료를 방법에 둔 게 핵심이다. 주민등록등본은 정부24 온라인이 무료고 주민센터 방문이
-- 400원이다. "온라인 우선"(ISS-01-02)이 사용자에게 실제로 이득인 근거가 이 차이다.

CREATE TABLE document_issue_method (
    id               BIGSERIAL   PRIMARY KEY,
    document_type_id BIGINT      NOT NULL REFERENCES document_type (id),
    method           VARCHAR(20) NOT NULL,
    agency           VARCHAR(100),
    url              TEXT,
    fee              INT         NOT NULL DEFAULT 0,
    requirements     TEXT,
    note             TEXT,
    sort_order       INT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_issue_method CHECK (method IN ('ONLINE', 'KIOSK', 'VISIT'))
);

CREATE INDEX idx_issue_method_document ON document_issue_method (document_type_id, sort_order);
CREATE UNIQUE INDEX uq_issue_method_document ON document_issue_method (document_type_id, method);

COMMENT ON COLUMN document_issue_method.fee IS '이 발급방법의 수수료(원). 같은 서류도 방법마다 다르다';
COMMENT ON COLUMN document_issue_method.requirements IS '방문·무인 발급 시 챙길 것 (ISS-01-04)';
COMMENT ON COLUMN document_issue_method.note IS '방문 전 확인사항 — 운영시간, 처리 가능 업무 (ISS-01-05)';

-- ------------------------------------------------------------
-- 서류 카탈로그
--
-- 3루 흐름(매물 검증 · 계약 실행 · 정책 신청)이 실제로 참조하는 것만 넣는다.
-- validity_days 와 fee 는 팩트 레지스트리와 값을 맞춘다.
--   FCT-099 등기사항전부증명서 열람 700 / 발급 1,000원
--   FCT-100 전입세대확인서 300원, 주민센터 방문만 가능
--   FCT-112 서류 유효기간 1개월 이내 발급분
-- ------------------------------------------------------------

INSERT INTO document_type (code, name, issuer, issue_url, online_available, validity_days, fee, note) VALUES
  ('REGISTRY_CERT', '등기사항전부증명서', '인터넷등기소', 'http://www.iros.go.kr', true, 30, 1000,
   '소유자·근저당·선순위채권을 확인한다. 계약 당일 다시 떼어 확인한다'),
  ('BUILDING_LEDGER', '건축물대장', '정부24', 'https://www.gov.kr', true, 30, 0,
   '위반건축물 여부를 확인한다. 근생빌라는 대출·보증이 전부 거절된다'),
  ('RESIDENT_REGISTRATION', '주민등록등본', '정부24', 'https://www.gov.kr', true, 30, 0,
   '온라인은 무료, 주민센터 방문은 400원'),
  ('FAMILY_RELATION', '가족관계증명서', '전자가족관계등록시스템', 'https://efamily.scourt.go.kr', true, 30, 0,
   '가구원 확인에 쓴다'),
  ('RESIDENT_LIST', '전입세대확인서', '주민센터', NULL, false, 30, 300,
   '온라인 발급이 안 되는 유일한 서류다. 다가구주택 대출에 필수'),
  ('INCOME_CERT', '소득금액증명', '홈택스', 'https://www.hometax.go.kr', true, 90, 0,
   '전년도 소득이 기준이다. 5월 종합소득세 신고 전에는 전전년도로 나온다'),
  ('EMPLOYMENT_CERT', '재직증명서', '재직 회사', NULL, false, 30, 0,
   '회사 양식이라 발급까지 며칠 걸릴 수 있다'),
  ('NATIONAL_TAX_PAYMENT', '국세 납세증명서', '홈택스', 'https://www.hometax.go.kr', true, 30, 0,
   '임대인 체납 확인에 쓴다. 조세채권이 보증금보다 우선한다'),
  ('LOCAL_TAX_PAYMENT', '지방세 납세증명서', '위택스', 'https://www.wetax.go.kr', true, 30, 0,
   '국세와 별개다. 둘 다 확인해야 한다'),
  ('LEASE_AGREEMENT', '임대차계약서', '본인 보관', NULL, false, NULL, 0,
   '확정일자를 받은 원본이 필요하다. 사본은 인정되지 않는 경우가 많다');

-- ------------------------------------------------------------
-- 발급방법
--
-- sort_order 는 권할 순서다. 온라인이 항상 앞에 온다(ISS-01-02).
-- ------------------------------------------------------------

INSERT INTO document_issue_method (document_type_id, method, agency, url, fee, requirements, note, sort_order)
SELECT d.id, m.method, m.agency, m.url, m.fee, m.requirements, m.note, m.sort_order
FROM (VALUES
  ('REGISTRY_CERT', 'ONLINE', '인터넷등기소', 'http://www.iros.go.kr', 1000, NULL,
   '열람 700원, 발급 1,000원. 대출·보증 제출용은 발급본이어야 한다', 1),
  ('REGISTRY_CERT', 'VISIT', '등기소·무인발급기', NULL, 1200, '신분증, 주소 또는 부동산고유번호',
   '주소만으로 못 찾는 경우가 있어 부동산고유번호를 미리 확인해 두면 빠르다', 2),

  ('BUILDING_LEDGER', 'ONLINE', '정부24', 'https://www.gov.kr', 0, NULL,
   '무료다. 집합건물은 전유부, 그 외는 일반건축물대장을 뗀다', 1),
  ('BUILDING_LEDGER', 'KIOSK', '무인민원발급기', NULL, 300, '신분증',
   '24시간 되는 곳이 많다', 2),
  ('BUILDING_LEDGER', 'VISIT', '주민센터·구청', NULL, 500, '신분증',
   '평일 09:00~18:00', 3),

  ('RESIDENT_REGISTRATION', 'ONLINE', '정부24', 'https://www.gov.kr', 0, NULL,
   '무료다. 주민등록번호 뒷자리 표시 여부를 제출처에 맞춰 고른다', 1),
  ('RESIDENT_REGISTRATION', 'KIOSK', '무인민원발급기', NULL, 400, '신분증 또는 지문',
   '지하철역·주민센터 앞에 많다. 24시간 되는 곳이 있다', 2),
  ('RESIDENT_REGISTRATION', 'VISIT', '주민센터', NULL, 400, '신분증',
   '평일 09:00~18:00. 점심시간에도 대체로 처리된다', 3),

  ('FAMILY_RELATION', 'ONLINE', '전자가족관계등록시스템', 'https://efamily.scourt.go.kr', 0, NULL,
   '무료다. 상세·일반 중 제출처가 요구하는 것을 고른다', 1),
  ('FAMILY_RELATION', 'KIOSK', '무인민원발급기', NULL, 500, '신분증', NULL, 2),
  ('FAMILY_RELATION', 'VISIT', '주민센터', NULL, 1000, '신분증', '평일 09:00~18:00', 3),

  ('RESIDENT_LIST', 'VISIT', '주민센터', NULL, 300,
   '신분증, 임대차계약서 또는 매매계약서',
   '온라인·무인 발급이 안 된다. 이해관계인만 뗄 수 있어 계약서를 반드시 가져가야 한다. 처리 약 5분', 1),

  ('INCOME_CERT', 'ONLINE', '홈택스', 'https://www.hometax.go.kr', 0, NULL,
   '무료다. 정부24에서도 뗄 수 있다', 1),
  ('INCOME_CERT', 'KIOSK', '무인민원발급기', NULL, 0, '신분증', NULL, 2),
  ('INCOME_CERT', 'VISIT', '세무서·주민센터', NULL, 0, '신분증', '평일 09:00~18:00', 3),

  ('EMPLOYMENT_CERT', 'VISIT', '재직 회사 인사팀', NULL, 0, '회사 내부 신청 절차',
   '회사 양식이라 며칠 걸릴 수 있다. 은행 상담 전에 미리 요청해 둔다', 1),

  ('NATIONAL_TAX_PAYMENT', 'ONLINE', '홈택스', 'https://www.hometax.go.kr', 0, NULL,
   '무료다. 임대인 본인만 뗄 수 있으므로 계약 자리에서 요청한다', 1),
  ('NATIONAL_TAX_PAYMENT', 'VISIT', '세무서', NULL, 0, '신분증', '평일 09:00~18:00', 2),

  ('LOCAL_TAX_PAYMENT', 'ONLINE', '위택스', 'https://www.wetax.go.kr', 0, NULL,
   '무료다. 국세와 별개라 둘 다 받아야 한다', 1),
  ('LOCAL_TAX_PAYMENT', 'KIOSK', '무인민원발급기', NULL, 0, '신분증', NULL, 2),
  ('LOCAL_TAX_PAYMENT', 'VISIT', '주민센터·구청', NULL, 0, '신분증', '평일 09:00~18:00', 3),

  ('LEASE_AGREEMENT', 'VISIT', '본인 보관', NULL, 0, '계약 시 받은 원본',
   '확정일자 도장이 찍힌 원본이 필요하다. 잃어버리면 재발급이 안 된다', 1)
) AS m (code, method, agency, url, fee, requirements, note, sort_order)
JOIN document_type d ON d.code = m.code;
