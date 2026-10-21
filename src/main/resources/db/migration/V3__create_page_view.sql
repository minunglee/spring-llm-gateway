-- minunglee.cloud 방문 기록. 원래 IP는 저장하지 않고, 하루 단위로만 같은 방문자인지 구분하는 해시를 남긴다
CREATE TABLE page_view (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    viewed_at     TIMESTAMPTZ  NOT NULL,
    path          VARCHAR(200) NOT NULL,
    referrer_host VARCHAR(100),           -- 다른 사이트에서 왔을 때만 (예: www.saramin.co.kr)
    visitor       CHAR(16)     NOT NULL,  -- (IP 해시 + 브라우저 + 날짜)의 해시
    device        VARCHAR(8)   NOT NULL   -- mobile · desktop
);

CREATE INDEX idx_page_view_viewed_at ON page_view (viewed_at);
