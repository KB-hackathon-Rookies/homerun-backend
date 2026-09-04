-- 기존 기본값 0과 사용자가 명시적으로 입력한 0을 구분한다.
-- 기존 병역기간 값은 보존하지만 재확인 전에는 진단 초깃값으로 사용하지 않는다.
ALTER TABLE app_user ADD COLUMN military_months_confirmed BOOLEAN NOT NULL DEFAULT false;
