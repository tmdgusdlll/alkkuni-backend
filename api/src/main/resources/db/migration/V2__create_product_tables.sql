-- 제품·성분 함량·수집 커서 테이블 (core 엔티티와 1:1, ddl-auto=validate로 검증)
-- erd.sql과 다른 점: confidence·review_status·raw_text 제외, parse_status·parse_issue 추가,
-- ingredient_alias·unit_conversion은 정규화 단계에서 추가한다.

CREATE TABLE product (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_no         VARCHAR(50)  NOT NULL UNIQUE, -- 식약처 품목제조번호, upsert 키
    name              VARCHAR(500) NOT NULL,
    manufacturer      VARCHAR(255),
    intake_method     TEXT,
    standard_text     TEXT,                         -- STDR_STND 원문 (파서 입력)
    raw_material      BOOLEAN      NOT NULL,
    source_hash       VARCHAR(64),                  -- 원문 해시, 바뀐 것만 재파싱
    source_updated_at TIMESTAMP,
    status            VARCHAR(20)  NOT NULL,
    parse_status      VARCHAR(20)  NOT NULL,
    parse_issue       TEXT                          -- 검수 사유
);

CREATE TABLE ingredient (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,      -- 표준 성분명
    default_unit VARCHAR(20)
);

-- 엔티티는 FK 값만 들고 있고(연관관계 매핑 없음), 참조 무결성은 여기 FK 제약이 지킨다.
CREATE TABLE product_ingredient (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id        BIGINT         NOT NULL REFERENCES product (id),
    ingredient_id     BIGINT         REFERENCES ingredient (id), -- 정규화 전, 사전에 없는 성분은 null
    raw_name          TEXT           NOT NULL,  -- 구분자 없는 원문은 이름 길이에 상한이 없다
    amount            NUMERIC        NOT NULL,  -- 정밀도 미지정: CFU 15자리·극소량도 반올림 없이 원문 그대로
    unit              VARCHAR(20)    NOT NULL,
    basis_amount      NUMERIC        NOT NULL,
    basis_unit        VARCHAR(20)    NOT NULL,
    extraction_method VARCHAR(20)    NOT NULL,
    parser_version    VARCHAR(20)    NOT NULL       -- 재파싱 대상 판단용
);

-- PostgreSQL은 FK에 인덱스를 자동으로 만들지 않는다. 제품별 성분 조회가 주 경로다.
CREATE INDEX idx_product_ingredient_product_id ON product_ingredient (product_id);

CREATE TABLE ingestion_cursor (
    source              VARCHAR(20) PRIMARY KEY,    -- 예: I0030, I0490
    last_collected_date DATE        NOT NULL,
    updated_at          TIMESTAMP   NOT NULL
);
