-- 회원 표시명을 nickname → name 으로 통일한다. 프론트 회원가입(AU-04)이 닉네임이 아니라
-- 실명(이름)을 받으므로 도메인 용어를 name 으로 맞춘다.
--
-- app_user 에는 V1 부터 name 컬럼이 이미 있으나(미매핑) 비어 있다. 기존 nickname 값을 name 으로
-- 옮긴 뒤 nickname 컬럼을 제거한다. name 에 이미 값이 있으면 덮어쓰지 않는다.
--
-- 주의(API breaking change): 응답 JSON 필드가 nickname → name 으로 바뀐다. 프론트 대응 필요.
UPDATE app_user SET name = nickname WHERE name IS NULL AND nickname IS NOT NULL;
ALTER TABLE app_user DROP COLUMN nickname;
