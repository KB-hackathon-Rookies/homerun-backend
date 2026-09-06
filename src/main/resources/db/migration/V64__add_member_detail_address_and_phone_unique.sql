-- 회원가입 확장(AU-04). app_user 에는 name·phone·phone_verified_at·birth_date·residence_region_id
-- 컬럼이 V1 부터 있으나, 상세 주소만 없어 추가한다.
ALTER TABLE app_user ADD COLUMN detail_address VARCHAR(255);

-- 활성 회원 기준 휴대전화 번호 중복 방지. 탈퇴(deleted_at IS NOT NULL) 회원의 번호는 재사용
-- 가능하도록 부분 유니크 인덱스로 둔다. 번호는 정규화(숫자만, 01012345678)해서 저장한다.
CREATE UNIQUE INDEX ux_app_user_phone_active ON app_user (phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
