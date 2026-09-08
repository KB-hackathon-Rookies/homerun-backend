-- 버팀목 중복대출 금지(NO_DUPLICATE_LOAN, V66 의 prohibited_loan_check) 사용자 확인값.
--
-- has_existing_jeonse_loan 은 "차주 본인의 기존 전세자금대출" 하나만 담는다. 공식 금지 범위는
-- 성년 세대원 전원의 기금대출과 차주·배우자의 전세자금·주택담보대출까지라, 본인 대출이
-- 없다는 답만으로는 통과를 단정할 수 없어 지금은 항상 NEED_INFO 로 남는다(FCT-258).
--
-- 사용자가 그 나머지 범위까지 없음을 명시적으로 확인한 경우에만 PASS 로 본다. 확인 자체가
-- 사용자 진술이지 은행 검증이 아니므로 NULL(안 물어봄)과 false(확인 안 함)를 구분해 담고,
-- 필수값으로 만들지 않는다 — 확인하지 않아도 1루는 그대로 끝낼 수 있어야 한다.

ALTER TABLE plan_input
    ADD COLUMN prohibited_loan_confirmed BOOLEAN;

COMMENT ON COLUMN plan_input.prohibited_loan_confirmed IS
    '세대원 기금대출과 배우자 전세·주택담보대출이 없음을 사용자가 확인했는가(FCT-258). 사용자 진술이며 은행 검증이 아니다';
