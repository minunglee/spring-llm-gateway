-- 초대코드. 원문 코드는 저장하지 않고 SHA-256 해시만 저장한다 (발급 시 한 번만 보여준다)
CREATE TABLE invite_code (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code_hash    VARCHAR(64)  NOT NULL UNIQUE,
    label        VARCHAR(100) NOT NULL,
    daily_limit  INT          NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL
);

-- 초대코드별 열람 기록 (코드 입력, 이력서 열람 등)
CREATE TABLE invite_access_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invite_code_id  BIGINT       NOT NULL REFERENCES invite_code (id),
    action          VARCHAR(30)  NOT NULL,
    accessed_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_invite_access_log_code ON invite_access_log (invite_code_id, accessed_at);

-- LLM 요청이 어느 등급·초대코드에서 왔는지 기록
ALTER TABLE request_log ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'GUEST';
ALTER TABLE request_log ADD COLUMN invite_code_id BIGINT;
