-- 회수·판매중지(I0490) 목록 (core Recall 엔티티와 1:1, ddl-auto=validate로 검증)
-- erd.sql의 product_id·match_status는 회수 매칭(4주차) 때 추가한다.
CREATE TABLE recall (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_seq   VARCHAR(50)  NOT NULL UNIQUE, -- RTRVLDSUSE_SEQ
    report_no    VARCHAR(50),                  -- 품목제조보고번호. 회수의 절반 이상은 비어 있거나 다른 형식이다
    product_name VARCHAR(500) NOT NULL,
    manufacturer VARCHAR(255),
    product_type VARCHAR(50),                  -- PRDLST_TYPE. 건강기능식품 외 식품도 함께 온다
    reason       TEXT,
    expiry_text  VARCHAR(255),                 -- DISTBTMLMT. 형식이 제각각이라 원문 그대로 둔다
    list_status  VARCHAR(20)  NOT NULL,        -- LISTED / DELISTED
    first_seen_at TIMESTAMP   NOT NULL,
    last_seen_at  TIMESTAMP   NOT NULL
);

-- 회수 매칭이 신고번호로 찾는다.
CREATE INDEX idx_recall_report_no ON recall (report_no);
