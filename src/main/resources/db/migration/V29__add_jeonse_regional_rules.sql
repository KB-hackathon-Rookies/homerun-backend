-- 신규 사전진단 지역 선택값. 행정구역 코드나 과밀억제권역을 정책 수도권으로 추측하지 않는다.
ALTER TABLE region ADD COLUMN policy_area VARCHAR(20);
ALTER TABLE region ADD CONSTRAINT ck_region_policy_area
    CHECK (policy_area IN ('SEOUL', 'CAPITAL', 'NON_CAPITAL'));

INSERT INTO region (code, name, level, is_seoul, policy_area) VALUES
 ('JEONSE_SEOUL', '서울', 'POLICY_OPTION', true, 'SEOUL'),
 ('JEONSE_INCHEON', '인천', 'POLICY_OPTION', false, 'CAPITAL'),
 ('JEONSE_GYEONGGI', '경기', 'POLICY_OPTION', false, 'CAPITAL'),
 ('JEONSE_OTHER', '그 외 지역(비수도권)', 'POLICY_OPTION', false, 'NON_CAPITAL');

-- 2026-09-05 공식 페이지 확인. effective_from은 확인하여 등록한 시점이며 고시 시행일을 뜻하지 않는다.
-- 규칙 전체는 기존과 같이 사람 검수 전 DRAFT로 둔다.
INSERT INTO config_effective
 (fact_code, category, item, value_text, value_num, unit, apply_condition, source, source_url,
  confidence, change_cycle, related_feature, note, effective_from, updated_by)
SELECT code, '전세대출', item, description, value, unit, '일반가구 신규계약, 특례 제외', '주택도시기금',
 'https://nhuf.molit.go.kr/FP/FP05/FP0502/FP05020101.jsp', 'CONFIRMED', '고시', 'POL-01',
 '실제 담보별 한도 및 재직 1년 미만 제한 가능성은 은행 추가 확인', DATE '2026-09-05', 'regional-rules'
FROM (VALUES
 ('FCT-192', '일반 버팀목 수도권 보증금 상한', '수도권 임차보증금 3억원 이하', 300000000, '원'),
 ('FCT-193', '일반 버팀목 비수도권 보증금 상한', '비수도권 임차보증금 2억원 이하', 200000000, '원'),
 ('FCT-194', '일반 버팀목 수도권 대출 한도', '수도권 최대 1억2천만원', 120000000, '원'),
 ('FCT-195', '일반 버팀목 비수도권 대출 한도', '비수도권 최대 8천만원', 80000000, '원'),
 ('FCT-196', '일반 버팀목 신규계약 대출 비율', '신규계약 보증금의 70% 이내', 70, '%')
) AS facts(code, item, description, value, unit);

INSERT INTO config_effective
 (fact_code, category, item, value_text, value_num, unit, apply_condition, source, source_url,
  confidence, change_cycle, related_feature, note, effective_from, updated_by)
SELECT code, '전세대출', item, description, value, unit, '서울시 청년 임차보증금 이자지원', '서울주거포털',
 'https://housing.seoul.go.kr/site/main/content/sh01_040901', 'CONFIRMED', '고시', 'POL-01',
 '실제 소득·주택·은행 심사 및 추천서 발급 요건 추가 확인', DATE '2026-09-05', 'regional-rules'
FROM (VALUES
 ('FCT-197', '서울시 청년 임차보증금 대출 한도', '최대 2억원', 200000000, '원'),
 ('FCT-198', '서울시 청년 임차보증금 대출 비율', '임차보증금의 90% 이내', 90, '%'),
 ('FCT-199', '서울시 청년 임차보증금 연령 상한', '만 19세 이상 39세 이하', 39, '세')
) AS facts(code, item, description, value, unit);

-- 기존 FCT-034의 복합 주택요건에서 보증금 수치만 분리한다. 전체 요건 검수는 유지한다.
INSERT INTO config_effective
 (fact_code, category, item, value_text, value_num, unit, apply_condition, source, source_url,
  confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES ('FCT-200', '전세대출', '서울시 청년 임차보증금 대상 보증금 상한', '임차보증금 3억원 이하',
 300000000, '원', '서울시 청년 임차보증금 이자지원', '서울주거포털',
 'https://housing.seoul.go.kr/site/main/content/sh01_040901', 'REVIEW', '고시', 'POL-01',
 'FCT-034 및 확정 서비스 플로우의 보증금 수치 분리. 공고 전체 주택요건 추가 검수 필요', '2026-09-05', 'regional-rules');

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by)
SELECT policy_id, 3,
 rule_json || jsonb_build_object(
 'conditions', (rule_json->'conditions') || '[
   {"code":"INCOME_CAP","field":"monthly_income","op":"annual_lte","fact_code":"FCT-003"},
   {"code":"NET_ASSET_CAP","field":"net_assets","op":"lte","fact_code":"FCT-170"},
   {"code":"DEPOSIT_CAP","field":"hope_deposit","op":"lte_by_region","fact_code":"FCT-192","alt_fact_code":"FCT-193"}
 ]'::jsonb,
 'amount', '{"ratio_fact_code":"FCT-196","cap_fact_code":"FCT-194","non_capital_cap_fact_code":"FCT-195"}'::jsonb,
 'note', '일반가구 신규계약 사전진단. 본인 소득 MVP이며 혼인 특례는 추가하지 않음. 재직 1년 미만·담보별 한도·세대 무주택·증빙 소득은 은행 확인. 금리는 미확인이라 비움'),
 'DRAFT', DATE '2026-09-05', 'regional-rules'
FROM policy_rule WHERE policy_id = (SELECT id FROM policy WHERE code='JEONSE-GENERAL-BEOTIMMOK') AND version=2;

INSERT INTO policy_rule (policy_id, version, rule_json, status, effective_from, drafted_by)
SELECT policy_id, 3,
 rule_json || jsonb_build_object(
 'conditions', (rule_json->'conditions') || '[
   {"code":"REGION_TARGET","field":"region_id","op":"region_eq","value":"SEOUL"},
   {"code":"DEPOSIT_CAP","field":"hope_deposit","op":"lte","fact_code":"FCT-200"},
   {"code":"AGE_UPPER_BOUND","field":"birth_date","op":"age_within_years_adjusted","fact_code":"FCT-199"}
 ]'::jsonb,
 'amount', '{"ratio_fact_code":"FCT-198","cap_fact_code":"FCT-197"}'::jsonb,
 'note', '서울시 청년 이자지원. 서울 지역·19~39세 확인. 기존 주택요건 추가확인은 유지하며 COFIX 금리는 임의 입력하지 않음'),
 'DRAFT', DATE '2026-09-05', 'regional-rules'
FROM policy_rule WHERE policy_id=(SELECT id FROM policy WHERE code='JEONSE-SEOUL-INTEREST-SUPPORT') AND version=2;
