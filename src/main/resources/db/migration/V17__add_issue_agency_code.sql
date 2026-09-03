-- PLC-01-05 발급처별 업무 묶음.
--
-- agency 는 화면에 그대로 쓰는 자유 문자열이라 묶는 기준이 못 된다. '주민센터'와
-- '주민센터·구청'과 '세무서·주민센터'는 사람 눈에는 같은 곳인데 문자열로는 다르다.
-- 그대로 묶으면 같은 주민센터를 세 번 가는 계획이 나온다.
--
-- 묶는 기준을 따로 둔다. agency 는 그 방법의 설명으로 남긴다.

ALTER TABLE document_issue_method ADD COLUMN agency_code VARCHAR(30);

COMMENT ON COLUMN document_issue_method.agency_code IS '방문을 묶는 기준. 화면 문구는 agency 를 쓴다';

UPDATE document_issue_method SET agency_code = 'ONLINE' WHERE method = 'ONLINE';

UPDATE document_issue_method m SET agency_code = v.agency_code
FROM (VALUES
  ('REGISTRY_CERT', 'KIOSK', 'COURT_KIOSK'),
  ('REGISTRY_CERT', 'VISIT', 'REGISTRY_OFFICE'),
  ('BUILDING_LEDGER', 'KIOSK', 'CIVIL_KIOSK'),
  ('BUILDING_LEDGER', 'VISIT', 'COMMUNITY_CENTER'),
  ('RESIDENT_REGISTRATION', 'KIOSK', 'CIVIL_KIOSK'),
  ('RESIDENT_REGISTRATION', 'VISIT', 'COMMUNITY_CENTER'),
  ('FAMILY_RELATION', 'KIOSK', 'CIVIL_KIOSK'),
  ('FAMILY_RELATION', 'VISIT', 'COMMUNITY_CENTER'),
  ('RESIDENT_LIST', 'VISIT', 'COMMUNITY_CENTER'),
  ('INCOME_CERT', 'KIOSK', 'CIVIL_KIOSK'),
  ('INCOME_CERT', 'VISIT', 'COMMUNITY_CENTER'),
  ('EMPLOYMENT_CERT', 'VISIT', 'EMPLOYER'),
  ('NATIONAL_TAX_PAYMENT', 'VISIT', 'TAX_OFFICE'),
  ('LOCAL_TAX_PAYMENT', 'KIOSK', 'CIVIL_KIOSK'),
  ('LOCAL_TAX_PAYMENT', 'VISIT', 'COMMUNITY_CENTER'),
  ('LEASE_AGREEMENT', 'VISIT', 'SELF')
) AS v (code, method, agency_code)
WHERE m.method = v.method
  AND m.document_type_id = (SELECT id FROM document_type WHERE code = v.code);

-- 소득금액증명은 세무서에서도 되지만 주민센터로 묶는다. 등본·가족관계증명서와 같은
-- 창구에서 끝나므로 걸음 수가 줄어든다. 세무서 선택지는 안내 문구에 남긴다.
UPDATE document_issue_method SET agency = '주민센터', note = '세무서에서도 뗄 수 있다. 평일 09:00~18:00'
WHERE agency = '세무서·주민센터';

ALTER TABLE document_issue_method ALTER COLUMN agency_code SET NOT NULL;
