-- 주택임차차입금 원리금상환액 소득공제 계산에 쓸 수치(#225). BR-29·FR-H8-01.
--
-- 한도 400만원은 FCT-053 에 이미 있다. 여기서는 계산에 필요한 공제율·상환액 상한·간이세율을
-- 계산 가능한 형태로 심는다. 판정이 아니라 산술 계산이지만 수치를 코드에 박지 않는다.
--
-- 간이세율 16.5%는 과세표준별 실제 세율이 아니라 화면 예시용 가정이다(BR-29 명시) -- REVIEW 로
-- 두어 "가정값, 실제는 과세표준별" 을 화면에 표시하게 한다.

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source,
   confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-239', '소득공제', '주택임차차입금 공제율', '40%', 40, '%',
   '원리금상환액 대비', '국세청', 'CONFIRMED', '세법개정', 'RF-HM', 'BR-29. 한도 400만(FCT-053)', '2026-01-01', 'fact-registry'),
  ('FCT-240', '소득공제', '주택임차차입금 상환액 상한', '연 1,000만원', 10000000, '원',
   '공제 기준 상환액 상한', '국세청', 'CONFIRMED', '세법개정', 'RF-HM', 'BR-29. min(연상환액, 1천만) × 40%', '2026-01-01', 'fact-registry'),
  ('FCT-241', '소득공제', '연말정산 간이세율', '16.5%', 16.5, '%',
   '예상 환급 계산용 가정. 실제는 과세표준별', '국세청', 'REVIEW', '세법개정', 'RF-HM', 'BR-29. 화면 예시용 가정값', '2026-01-01', 'fact-registry');
