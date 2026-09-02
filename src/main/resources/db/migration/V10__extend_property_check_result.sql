-- 매물 검증 결과를 네 단계로 넓힌다.
--
-- 기존 PASS·FAIL·UNKNOWN 으로는 "이 집으로는 대출이 아예 안 된다"와 "되긴 하지만
-- 위험하다"를 구분할 수 없다. 둘은 사용자가 할 일이 다르다.
--   위반건축물(FCT-120)  → 대출·보증 전부 거절. 다른 집을 봐야 한다        = BLOCK
--   전세가율 80% 초과(FCT-119) → 계약은 되지만 깡통전세 가능성이 있다      = WARN
--
-- FAIL 은 남겨 둔다. 이미 쓰고 있는 코드가 없지만 제약만 넓히고 값을 없애면
-- 기존 데이터가 있을 때 복구가 어려워진다.

ALTER TABLE property_check DROP CONSTRAINT ck_check_result;
ALTER TABLE property_check
    ADD CONSTRAINT ck_check_result
        CHECK (result IN ('PASS', 'WARN', 'BLOCK', 'FAIL', 'UNKNOWN'));

-- 전세가율 구간(FCT-119). 확정도가 REVIEW 라 값이 바뀔 수 있다.
ALTER TABLE property
    ADD CONSTRAINT ck_jeonse_ratio_level
        CHECK (jeonse_ratio_level IS NULL
               OR jeonse_ratio_level IN ('SAFE', 'CAUTION', 'RISK', 'UNKNOWN'));

-- 소액임차인 최우선변제 기준을 숫자로 분해한다(FCT-063~066).
-- 원문 한 줄에 "보증금 상한"과 "변제 한도" 두 값이 들어 있어 계산기가 쓸 수 없다.
-- FCT-142~155 는 월세대출 분해분으로 예약돼 있어 156 부터 쓴다.

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source, confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-156', '최우선변제', '서울 보증금 상한', '1억 6,500만원', 165000000.0, '원', '서울특별시', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-063 분해', '2026-01-01', 'fact-registry'),
  ('FCT-157', '최우선변제', '서울 변제 한도', '5,500만원', 55000000.0, '원', '서울특별시', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-063 분해', '2026-01-01', 'fact-registry'),
  ('FCT-158', '최우선변제', '과밀억제권역 보증금 상한', '1억 4,500만원', 145000000.0, '원', '수도권 과밀억제·세종·용인·화성·김포', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-064 분해', '2026-01-01', 'fact-registry'),
  ('FCT-159', '최우선변제', '과밀억제권역 변제 한도', '4,800만원', 48000000.0, '원', '수도권 과밀억제·세종·용인·화성·김포', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-064 분해', '2026-01-01', 'fact-registry'),
  ('FCT-160', '최우선변제', '광역시 보증금 상한', '8,500만원', 85000000.0, '원', '광역시·안산·광주·파주·이천·평택', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-065 분해', '2026-01-01', 'fact-registry'),
  ('FCT-161', '최우선변제', '광역시 변제 한도', '2,800만원', 28000000.0, '원', '광역시·안산·광주·파주·이천·평택', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-065 분해', '2026-01-01', 'fact-registry'),
  ('FCT-162', '최우선변제', '그 밖의 지역 보증금 상한', '7,500만원', 75000000.0, '원', '그 밖의 지역', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-066 분해', '2026-01-01', 'fact-registry'),
  ('FCT-163', '최우선변제', '그 밖의 지역 변제 한도', '2,500만원', 25000000.0, '원', '그 밖의 지역', '주택임대차보호법', 'CONFIRMED', '연1회', 'PRP-01', 'FCT-066 분해', '2026-01-01', 'fact-registry');
