CREATE TABLE request_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mode            VARCHAR(20)  NOT NULL,
    model           VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    input_tokens    BIGINT       NOT NULL,
    output_tokens   BIGINT       NOT NULL,
    cost_micro_usd  BIGINT       NOT NULL,
    latency_ms      BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_request_log_created_at ON request_log (created_at);
