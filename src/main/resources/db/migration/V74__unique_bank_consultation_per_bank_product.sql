-- 한 매물에서 같은 (은행 + 상품) 상담은 한 건만 둔다.
--
-- 서비스가 조회 후 저장으로 덮어쓰지만, 조회와 저장 사이에 다른 요청이 끼어들면 둘 다
-- "없음"을 보고 각자 INSERT 해 행이 두 개가 된다(저장 버튼 더블클릭). 그 뒤로는 같은 키
-- 파인더가 Optional 에 두 건을 담지 못해 그 매물의 그 은행 상담이 영구히 500 이 된다.
-- 도메인 불변식이면 DB 가 최종 방어선이어야 한다(V11 과 같은 판단).
--
-- 지점(branch_name)은 키에 넣지 않는다. 같은 은행의 다른 지점을 다시 다녀온 것은 새 카드가
-- 아니라 같은 상담의 갱신이고, 상품이 다르면 별도 카드로 남아 비교할 수 있어야 한다.
--
-- 이미 중복이 쌓인 DB 에서도 그대로 돌 수 있어야 하므로 정리를 먼저 하고 제약을 건다.

-- 2루 최종 선택이 지울 행을 가리키고 있으면 FK(RESTRICT)에 막혀 삭제가 실패한다.
-- 남길 행으로 먼저 옮긴다 -- 어차피 같은 (은행 + 상품) 상담이라 선택 의미는 그대로다.
WITH ranked AS (
    SELECT id,
           first_value(id) OVER (
               PARTITION BY plan_id, property_id, bank_name, loan_product
               ORDER BY consulted_at DESC, created_at DESC, id DESC) AS keep_id
      FROM bank_consultation
)
UPDATE property_decision d
   SET consultation_id = ranked.keep_id
  FROM ranked
 WHERE d.consultation_id = ranked.id
   AND ranked.keep_id <> ranked.id;

-- 같은 키에서 가장 최근 상담 한 건만 남긴다. updated_at 컬럼이 없으므로
-- 상담일 → 생성시각 → id 순으로 최신을 고른다(목록 정렬과 같은 기준이다).
WITH ranked AS (
    SELECT id,
           first_value(id) OVER (
               PARTITION BY plan_id, property_id, bank_name, loan_product
               ORDER BY consulted_at DESC, created_at DESC, id DESC) AS keep_id
      FROM bank_consultation
)
DELETE FROM bank_consultation b
 USING ranked
 WHERE b.id = ranked.id
   AND ranked.keep_id <> ranked.id;

ALTER TABLE bank_consultation
    ADD CONSTRAINT uq_bank_consultation_plan_property_bank_product
    UNIQUE (plan_id, property_id, bank_name, loan_product);
