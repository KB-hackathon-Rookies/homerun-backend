-- FCT-099 · FCT-100 이 열람 수수료와 교부(발급) 수수료를 구분하지 않는다.
--
-- 서류를 "보는 것"과 "떼는 것"은 값이 다르다. 제출용은 교부라 열람가로는 안 된다.
-- 그런데 팩트에 어느 쪽인지 적혀 있지 않아, ISS-01 시드가 전입세대확인서를 열람가
-- 300원으로 넣었다가 리뷰에서 잡혔다(#59).
--
-- 기존 팩트를 지우지 않는다. 값 자체는 열람 수수료로 맞다. 무엇의 값인지만 분명히
-- 하고, 빠져 있던 교부 수수료를 새 팩트로 더한다.

UPDATE config_effective
SET item = '전입세대확인서 열람',
    value_text = '열람 건당 300원',
    note = '제출용은 교부라 이 값이 아니다(FCT-164). 온라인 발급 불가, 소요 약 5분'
WHERE fact_code = 'FCT-100';

UPDATE config_effective
SET item = '등기사항전부증명서 열람',
    value_text = '열람 건당 700원',
    value_num = 700.0,
    unit = '원',
    note = '대출·보증 제출용은 발급본이어야 한다(FCT-165)'
WHERE fact_code = 'FCT-099';

INSERT INTO config_effective
  (fact_code, category, item, value_text, value_num, unit, apply_condition, source, confidence, change_cycle, related_feature, note, effective_from, updated_by)
VALUES
  ('FCT-164', '비용', '전입세대확인서 교부', '교부 건당 400원', 400.0, '원',
   '신청 자격에 따라 500원', '정부24', 'CONFIRMED', '수시', 'ISS-01',
   '제출용은 교부다. 열람은 300원(FCT-100)', '2026-01-01', 'fact-registry'),

  ('FCT-165', '비용', '등기사항전부증명서 발급', '인터넷·무인 1,000원 / 방문 1,200원', 1000.0, '원',
   '인터넷등기소 기준', '등기사항증명서 등 수수료규칙', 'CONFIRMED', '수시', 'ISS-01',
   '방문은 1,200원이다. 열람은 700원(FCT-099)', '2026-01-01', 'fact-registry'),

  ('FCT-166', '비용', '주민등록등본 교부', '방문 400원 / 무인 200원 / 인터넷 무료', 400.0, '원',
   '무인은 방문 교부의 1/2', '주민등록법 시행규칙', 'CONFIRMED', '수시', 'ISS-01',
   '신청 사유에 따라 500원인 경우가 있다', '2026-01-01', 'fact-registry'),

  ('FCT-167', '비용', '건축물대장 교부', '등본 500원 / 초본 300원 / 인터넷 무료', 500.0, '원',
   '무인은 자치단체 조례에 따라 다름', '정부24', 'REVIEW', '수시', 'ISS-01',
   '무인발급기 수수료를 하나로 못 박을 수 없다. 지역마다 300~500원', '2026-01-01', 'fact-registry');
