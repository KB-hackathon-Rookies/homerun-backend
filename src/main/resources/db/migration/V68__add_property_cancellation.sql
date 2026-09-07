-- 계약 해제 후 매물 해제 표시(#내부). FR-P8-06·DR-08. 해제 시 삭제하지 않고 표시만 한다.

ALTER TABLE property
    ADD COLUMN cancelled_at  TIMESTAMPTZ,
    ADD COLUMN cancel_reason TEXT;
