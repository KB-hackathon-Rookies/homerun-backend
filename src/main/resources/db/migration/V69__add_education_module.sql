-- 홈 4-7 교육과 예방. V1 의 education_content/education_progress(미사용 스캐폴드)를 채택(Option A)해
-- 6개 교육 모듈을 콘텐츠로 심고, 인앱 레슨 본문(body)과 퀴즈 문항 뱅크를 추가한다.
-- education_trigger·intervention_log 는 팀 설계 스캐폴드라 그대로 둔다.
-- 콘텐츠는 교육용 안내이며 실제 금융 자문·CB 조회가 아니다. 신용·부채류에는 고지·출처를 본문에 둔다.

-- 인앱 레슨 본문(마크다운). 모듈 1개 = 콘텐츠 1개(단일 본문).
ALTER TABLE education_content ADD COLUMN body TEXT;

-- 퀴즈 문항 뱅크
CREATE TABLE education_quiz_question (
    id           BIGSERIAL PRIMARY KEY,
    content_id   BIGINT    NOT NULL REFERENCES education_content (id) ON DELETE RESTRICT,
    question     TEXT      NOT NULL,
    options      JSONB     NOT NULL,
    answer_index INT       NOT NULL CHECK (answer_index >= 0),
    explanation  TEXT,
    sort_order   INT       NOT NULL,
    CONSTRAINT ck_edu_quiz_options CHECK (jsonb_typeof(options) = 'array' AND jsonb_array_length(options) >= 2),
    CONSTRAINT ck_edu_quiz_answer_range CHECK (answer_index < jsonb_array_length(options))
);
CREATE INDEX idx_edu_quiz_content ON education_quiz_question (content_id, sort_order);

-- 6개 모듈 = education_content
INSERT INTO education_content (code, title, topic, source, body, estimated_minutes, is_active) VALUES
  ('DELINQUENCY', '연체 예방과 위험 관리', 'delinquency', 'KB Think',
   E'# 연체가 왜 위험한가\n\n대출 이자를 제때 못 내면 연체이자가 붙습니다. 연체 3개월 이내는 대출금리 + 4%p, 3개월 초과는 + 5%p 입니다. 기한이익 상실 후에는 원금 전체에 연체이율이 적용됩니다.\n\n**자동이체를 걸어 두면** 실수로 인한 연체를 대부분 막을 수 있습니다.\n\n> 교육용 안내이며 실제 금융 자문이 아닙니다. 출처: KB Think(2026-08-31).',
   5, true),
  ('CREDIT', '신용관리 진단', 'credit', 'KB Think',
   E'# 신용점수를 지키는 습관\n\n신용점수는 CB사(KCB·NICE)가 상환 이력·부채·카드 사용 등을 종합해 매깁니다. 연체 없이 갚고, 카드 한도의 30% 이내로 쓰고, 불필요한 대출 조회를 줄이면 개선에 도움이 됩니다.\n\n> 교육용 안내입니다. 실제 점수는 CB사 조회로 확인하세요. 출처: 금융 일반 가이드(2026).',
   5, true),
  ('DEBT', '부채 관리', 'debt', '금융위',
   E'# DSR·DTI로 보는 상환 여력\n\nDSR은 연소득 대비 모든 대출의 원리금 상환액 비율, DTI는 주택담보대출 원리금 + 기타 대출 이자 비율입니다. 비율이 낮을수록 상환 여력이 큽니다. 새 대출 전에 소득과 기존 부채로 미리 가늠해 두면 무리한 대출을 피할 수 있습니다.\n\n> 교육용 개념 설명이며 실제 심사 기준·자문이 아닙니다. 출처: 금융위 일반 안내(2026).',
   6, true),
  ('FINANCIAL_FRAUD', '금융 사기 예방', 'fraud', '금융감독원',
   E'# 금융 사기 3대 수법\n\n1. 보이스피싱 — 기관·가족을 사칭해 송금·앱 설치 유도\n2. 투자사기 — 원금 보장·고수익을 미끼로 한 유사수신\n3. 스미싱 — 택배·청첩장 문자의 악성 링크\n\n출처가 불분명한 링크·앱은 절대 열지 말고, 송금 전 공식 번호로 직접 확인하세요.',
   5, true),
  ('JEONSE_FRAUD', '전세 사기 예방', 'jeonse_fraud', '홈런 위험 규칙',
   E'# 전세 사기 3대 유형\n\n1. 깡통전세 — 보증금이 집값에 육박해 못 돌려받는 경우\n2. 불법건축물 — 근생빌라 등 대출·보증이 안 되는 매물\n3. 이중계약 — 한 집을 여러 세입자와 계약\n\n> 교육용 안내입니다. 실제 매물 판정은 2루 검증(건축물대장·등기부·반환보증)을 따르세요.',
   5, true),
  ('EXPERIENTIAL', '체험형 금융 교육', 'experiential', '홈런',
   E'# 체험형 금융 상식 점검\n\n앞의 모듈에서 배운 연체·신용·부채·사기 예방 상식을 퀴즈로 점검합니다. 틀린 문항의 해설을 다시 읽으며 개념을 굳혀 보세요.',
   4, true);

-- 퀴즈 (모듈당 3문항). answer_index 는 반드시 options 범위 안(CHECK 로도 강제).
INSERT INTO education_quiz_question (content_id, question, options, answer_index, explanation, sort_order)
SELECT c.id, q.question, q.options::jsonb, q.answer_index, q.explanation, q.sort_order
FROM education_content c
JOIN (VALUES
  ('DELINQUENCY', '대출 이자를 3개월 넘게 연체하면 가산되는 연체이율은?', '["대출금리 + 2%p","대출금리 + 4%p","대출금리 + 5%p","가산 없음"]', 2, '3개월 초과 연체는 대출금리 + 5%p 입니다.', 1),
  ('DELINQUENCY', '연체를 예방하는 가장 확실한 방법은?', '["매달 수동 이체","자동이체 등록","만기 일시 상환","카드 리볼빙"]', 1, '자동이체를 걸어 두면 실수로 인한 연체를 막을 수 있습니다.', 2),
  ('DELINQUENCY', '기한이익 상실 후 연체이율이 적용되는 범위는?', '["밀린 이자만","원금 전체","보증금","관리비"]', 1, '기한이익 상실 후에는 원금 전체에 연체이율이 붙습니다.', 3),
  ('CREDIT', '신용점수를 매기는 곳은?', '["은행 지점","CB사(KCB·NICE)","국세청","주민센터"]', 1, '신용평가사(CB사)가 상환 이력 등을 종합해 점수를 매깁니다.', 1),
  ('CREDIT', '신용점수 관리에 도움이 되는 습관은?', '["카드 한도를 꽉 채워 쓰기","연체 없이 갚기","대출 조회 자주 하기","현금서비스 자주 쓰기"]', 1, '연체 없이 갚는 것이 가장 기본이 되는 습관입니다.', 2),
  ('CREDIT', '카드 사용액은 한도의 어느 정도 이내가 권장될까?', '["30% 이내","70% 이내","100%","제한 없음"]', 0, '한도의 30% 이내 사용이 신용 관리에 유리합니다.', 3),
  ('DEBT', 'DSR이 뜻하는 것은?', '["연소득 대비 총 대출 원리금 상환 비율","집값 대비 대출 비율","월세 대비 소득","보증금 대비 부채"]', 0, 'DSR은 연소득 대비 모든 대출 원리금 상환액 비율입니다.', 1),
  ('DEBT', '상환 여력이 큰 상태는?', '["DSR이 높다","DSR이 낮다","부채가 많다","소득이 적다"]', 1, 'DSR이 낮을수록 상환 여력이 큽니다.', 2),
  ('DEBT', '무리한 대출을 피하려면 언제 상환 여력을 가늠하는 게 좋을까?', '["연체된 뒤","새 대출을 받기 전","만기 때","해당 없음"]', 1, '새 대출 전에 소득·기존 부채로 미리 가늠해 두는 것이 좋습니다.', 3),
  ('FINANCIAL_FRAUD', '기관·가족을 사칭해 송금을 유도하는 수법은?', '["보이스피싱","스미싱","깡통전세","리볼빙"]', 0, '보이스피싱은 사칭 전화로 송금·앱 설치를 유도합니다.', 1),
  ('FINANCIAL_FRAUD', '문자 속 악성 링크로 정보를 빼내는 수법은?', '["스미싱","DSR","확정일자","전입신고"]', 0, '스미싱은 택배·청첩장 등을 가장한 악성 링크 문자입니다.', 2),
  ('FINANCIAL_FRAUD', '송금 요청을 받았을 때 안전한 행동은?', '["즉시 송금","공식 번호로 직접 확인","링크 먼저 클릭","앱 설치"]', 1, '송금 전 반드시 공식 번호로 직접 확인해야 합니다.', 3),
  ('JEONSE_FRAUD', '보증금이 집값에 육박해 회수가 어려운 전세는?', '["깡통전세","안심전세","반환보증","보증부월세"]', 0, '깡통전세는 보증금 회수가 어려운 대표 위험 유형입니다.', 1),
  ('JEONSE_FRAUD', '대출·보증이 안 나오는 대표적 매물은?', '["신축 아파트","근생빌라(불법건축물)","공동주택","오피스텔(주거용)"]', 1, '근린생활시설(근생빌라)은 대출·보증이 제한됩니다.', 2),
  ('JEONSE_FRAUD', '한 집을 여러 세입자와 계약하는 사기는?', '["이중계약","확정일자","전세권","질권설정"]', 0, '이중계약은 같은 집을 여러 명과 계약하는 사기 유형입니다.', 3),
  ('EXPERIENTIAL', '연체 3개월 초과 시 가산 연체이율은?', '["+2%p","+4%p","+5%p","없음"]', 2, '3개월 초과는 대출금리 + 5%p 입니다.', 1),
  ('EXPERIENTIAL', '신용점수를 매기는 곳은?', '["CB사","주민센터","국세청","우체국"]', 0, '신용평가사(CB사)가 점수를 산정합니다.', 2),
  ('EXPERIENTIAL', '깡통전세의 특징은?', '["보증금이 집값에 육박","월세가 저렴","공시가격이 높음","대출이 필요 없음"]', 0, '깡통전세는 보증금이 집값에 육박해 회수가 어렵습니다.', 3)
) AS q(code, question, options, answer_index, explanation, sort_order)
ON c.code = q.code;
