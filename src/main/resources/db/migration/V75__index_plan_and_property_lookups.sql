-- 외래키를 거꾸로 되짚는 목록 조회를 인덱스로 받친다.
--
-- PostgreSQL 은 FK 제약을 걸어도 참조하는 쪽 컬럼에 인덱스를 만들어 주지 않는다. 참조받는
-- 쪽(PK)만 인덱스가 있어서, "이 계획의 매물 전부" 처럼 자식 테이블을 plan_id·property_id 로
-- 되짚는 조회는 인덱스 없이 테이블을 통째로 훑는다. 지금은 행이 적어 Seq Scan 이 오히려
-- 빠르지만, 매물 목록 화면은 매물 하나당 검증 조회가 한 번씩 더 나가는 구조라 데이터가
-- 늘어난 뒤에는 전체 스캔이 화면 한 번에 여러 번 겹친다.
--
-- 시드 DB(계획 4만 · 매물 20만 · 검증 220만)에서 재현한 값이다. EXPLAIN ANALYZE 10회 중앙값:
--   property   WHERE plan_id     : 21.26ms (Seq Scan)          → 0.38ms (Index Scan)
--   property_check WHERE property_id : 104.33ms (Parallel Seq Scan) → 0.49ms (Index Scan)
--
-- property 에는 uq_property_selected_per_plan (plan_id) WHERE is_selected = true 가 이미
-- 있지만 최종 선택된 한 행만 담는 부분 인덱스다. 조회 조건이 인덱스 조건을 함축하지 않으면
-- 옵티마이저가 쓸 수 없어서, "이 계획의 모든 매물" 에는 여전히 Seq Scan 으로 돌아간다.
-- 같은 컬럼이라고 중복이 아니다 -- 인덱스를 지우고 EXPLAIN 을 다시 떠서 확인했다.
--
-- bank_consultation 은 여기 없다. V74 의 uq_bank_consultation_plan_property_bank_product 가
-- (plan_id, property_id) 를 선두 컬럼으로 가져 같은 조회를 이미 받쳐 준다.
--
-- CONCURRENTLY 는 쓰지 않는다. Flyway 는 마이그레이션을 트랜잭션 안에서 돌리는데
-- CREATE INDEX CONCURRENTLY 는 트랜잭션 안에서 실패한다. 지금 규모에서는 쓰기 잠금이
-- 순식간에 끝나므로 굳이 트랜잭션을 깨면서까지 쓸 이유가 없다.

-- PropertyRepository.findAllByPlanIdOrderByIdAsc — 매물 후보 목록(PropertyCandidateService)
CREATE INDEX ix_property_plan ON property (plan_id);

-- PropertyCheckRepository.findByPropertyIdOrderById — 신호등 판정(PropertyTrafficLightResolver)
CREATE INDEX ix_property_check_property ON property_check (property_id);

-- FixedExpenseRepository.findAllByPlanIdAndActiveTrueOrderByIdAsc — 고정지출 목록·현금흐름·연체위험
CREATE INDEX ix_fixed_expense_plan ON fixed_expense (plan_id);
