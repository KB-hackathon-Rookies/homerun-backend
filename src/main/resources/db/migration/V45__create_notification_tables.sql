-- 알림(FCM + Redis Stream) 서브시스템.
--
-- device_token: 회원 한 명이 여러 기기를 쓸 수 있어 member 당 N 개다. FCM 토큰은
--   앱 재설치·갱신으로 바뀌므로 token 을 유니크로 두고, 같은 토큰이 다시 등록되면
--   last_seen_at 만 갱신한다(업서트).
-- notification: 발송 이력이자 인박스다. 스트림에는 id 만 싣고 본문 원본은 여기에 둔다.
--   dedup_key 는 스케줄러가 같은 마감/넛지를 하루에 여러 번 돌려도 1건만 만들도록
--   막는 멱등키다(유니크). 즉석 알림은 dedup_key 가 NULL 이라 제약에 걸리지 않는다.
--   retry_count 는 재처리 리퍼가 재시도 상한을 판정할 때 쓴다.

CREATE TABLE device_token (
    id           BIGSERIAL   PRIMARY KEY,
    member_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token        VARCHAR(512) NOT NULL,
    platform     VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE device_token
    ADD CONSTRAINT ck_device_token_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB'));

CREATE UNIQUE INDEX uq_device_token_token ON device_token (token);
CREATE INDEX ix_device_token_member ON device_token (member_id);

CREATE TABLE notification (
    id          BIGSERIAL    PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type        VARCHAR(50)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    data        JSONB,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count INT          NOT NULL DEFAULT 0,
    dedup_key   VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,
    read_at     TIMESTAMPTZ
);

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ'));

-- dedup_key 는 NULL 을 허용하되(즉석 알림), 값이 있으면 전역 유일해야 한다.
-- Postgres 는 NULL 을 서로 다른 값으로 취급하므로 유니크 인덱스로 충분하다.
CREATE UNIQUE INDEX uq_notification_dedup_key ON notification (dedup_key);
CREATE INDEX ix_notification_member_created ON notification (member_id, created_at DESC);
CREATE INDEX ix_notification_member_unread ON notification (member_id) WHERE read_at IS NULL;
