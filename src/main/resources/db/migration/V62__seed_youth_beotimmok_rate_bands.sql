-- #1 청년 버팀목 금리 정밀 계산. 지금까지는 고정 범위(FCT-172 2.2% ~ FCT-173 3.3%)만 보여줬는데,
-- 소득구간표가 확보돼(KB Think 2026.08.31 기준, 국토부 주택도시기금포털 인용) 확정 금리를 계산한다.
--   최종금리 = 소득구간 기본금리 − 지방 조정(비수도권 −0.2%p) − 우대(중소·중견·창업 재직 0.3%p, 상한 0.5%p)
-- 일반 버팀목·서울시 이자지원은 2차원표/COFIX 미확보라 범위 모드(min/max)를 그대로 둔다.

-- 소득구간 기본금리 (연 부부합산 소득 구간별). 미혼 청년은 자격 소득상한(FCT-003, 5천만)에 걸려
-- 실제로는 2.2/2.5/2.9 구간만 쓰이지만, 신혼 6천~7.5천(3.3) 구간도 표 그대로 심는다.
INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source,
   confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-246', '금리', '청년 버팀목 소득구간1 상한', '연 2,000만원 이하', 20000000, '원',
   '기본금리 2.2% 적용 구간', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표. 국토부 고시', '2026-08-31', 'fact-registry'),
  ('FCT-247', '금리', '청년 버팀목 소득구간1 기본금리', '연 2.2%', 2.2, '%',
   '연소득 2천만원 이하', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표', '2026-08-31', 'fact-registry'),
  ('FCT-248', '금리', '청년 버팀목 소득구간2 상한', '연 4,000만원 이하', 40000000, '원',
   '기본금리 2.5% 적용 구간', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표', '2026-08-31', 'fact-registry'),
  ('FCT-249', '금리', '청년 버팀목 소득구간2 기본금리', '연 2.5%', 2.5, '%',
   '연소득 2천 초과 4천만원 이하', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표', '2026-08-31', 'fact-registry'),
  ('FCT-250', '금리', '청년 버팀목 소득구간3 상한', '연 6,000만원 이하', 60000000, '원',
   '기본금리 2.9% 적용 구간', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표', '2026-08-31', 'fact-registry'),
  ('FCT-251', '금리', '청년 버팀목 소득구간3 기본금리', '연 2.9%', 2.9, '%',
   '연소득 4천 초과 6천만원 이하', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표', '2026-08-31', 'fact-registry'),
  ('FCT-252', '금리', '청년 버팀목 소득구간4 상한', '연 7,500만원 이하', 75000000, '원',
   '기본금리 3.3% 적용 구간(신혼가구)', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표. 미혼 청년은 소득상한 5천만이라 실제 미사용', '2026-08-31', 'fact-registry'),
  ('FCT-253', '금리', '청년 버팀목 소득구간4 기본금리', '연 3.3%', 3.3, '%',
   '연소득 6천 초과 7천5백만원 이하', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표', '2026-08-31', 'fact-registry'),
  ('FCT-254', '금리', '청년 버팀목 지방 금리 인하', '지방(수도권 외) 0.2%p 인하', 0.2, '%',
   '서울·인천·경기 외 주택', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표. 기본금리에서 차감', '2026-08-31', 'fact-registry'),
  ('FCT-255', '금리', '청년 버팀목 중소기업 우대금리', '중소·중견 취업(창업) 청년 0.3%p', 0.3, '%',
   '중소·중견·창업지원 재직 청년', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표. 중복적용 우대 항목', '2026-08-31', 'fact-registry'),
  ('FCT-256', '금리', '청년 버팀목 우대금리 합산 상한', '우대 합산 최대 0.5%p', 0.5, '%',
   '기초수급·차상위·한부모 1.0%p, 다자녀 0.7%p 예외는 현재 스코프 밖', 'KB Think', 'CONFIRMED', '수시', 'POL-02',
   '2-8 확정표. 현재 반영 우대는 중소기업 1건이라 상한에 걸리지 않음', '2026-08-31', 'fact-registry');

-- 청년 버팀목 rule 의 rate 를 범위(min/max)에서 계산(소득구간표)으로 교체한다. 기존 min/max
-- 팩트(FCT-172/173)는 다른 참조가 있을 수 있어 삭제하지 않고 남긴다.
UPDATE policy_rule
SET rule_json = jsonb_set(
        rule_json,
        '{rate}',
        '{
            "income_bands": [
                {"ceiling_fact_code": "FCT-246", "rate_fact_code": "FCT-247"},
                {"ceiling_fact_code": "FCT-248", "rate_fact_code": "FCT-249"},
                {"ceiling_fact_code": "FCT-250", "rate_fact_code": "FCT-251"},
                {"ceiling_fact_code": "FCT-252", "rate_fact_code": "FCT-253"}
            ],
            "regional_discount_fact_code": "FCT-254",
            "sme_preference_fact_code": "FCT-255",
            "preference_cap_fact_code": "FCT-256"
        }'::jsonb)
WHERE policy_id = (SELECT id FROM policy WHERE code = 'JEONSE-YOUTH-BEOTIMMOK')
  AND rule_json ? 'rate';
