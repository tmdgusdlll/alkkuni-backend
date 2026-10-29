-- 알꾼이 ERD 초안 (ERDCloud 가져오기용). Flyway 마이그레이션이 아니다.

CREATE TABLE product (
    id                BIGINT       NOT NULL,
    report_no         VARCHAR(50)  NOT NULL,
    name              VARCHAR(500) NOT NULL,
    manufacturer      VARCHAR(255),
    intake_method     TEXT,
    standard_text     TEXT,
    raw_material      BOOLEAN      NOT NULL,
    source_hash       VARCHAR(64),
    source_updated_at TIMESTAMP,
    status            VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (report_no)
);

CREATE TABLE ingredient (
    id           BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    default_unit VARCHAR(20),
    PRIMARY KEY (id),
    UNIQUE (name)
);

CREATE TABLE ingredient_alias (
    id            BIGINT       NOT NULL,
    ingredient_id BIGINT       NOT NULL,
    alias         VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (alias),
    FOREIGN KEY (ingredient_id) REFERENCES ingredient (id)
);

CREATE TABLE unit_conversion (
    id            BIGINT         NOT NULL,
    ingredient_id BIGINT         NOT NULL,
    from_unit     VARCHAR(20)    NOT NULL,
    to_unit       VARCHAR(20)    NOT NULL,
    factor        NUMERIC(20, 8) NOT NULL,
    source_form   VARCHAR(100),
    PRIMARY KEY (id),
    FOREIGN KEY (ingredient_id) REFERENCES ingredient (id)
);

CREATE TABLE product_ingredient (
    id                BIGINT         NOT NULL,
    product_id        BIGINT         NOT NULL,
    ingredient_id     BIGINT,
    raw_name          VARCHAR(255)   NOT NULL,
    raw_text          TEXT           NOT NULL,
    amount            NUMERIC(20, 6),
    unit              VARCHAR(20),
    basis_amount      NUMERIC(20, 6),
    basis_unit        VARCHAR(20),
    extraction_method VARCHAR(20)    NOT NULL,
    confidence        NUMERIC(4, 3),
    parser_version    VARCHAR(20)    NOT NULL,
    review_status     VARCHAR(20)    NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (product_id) REFERENCES product (id),
    FOREIGN KEY (ingredient_id) REFERENCES ingredient (id)
);

CREATE TABLE recall (
    id            BIGINT       NOT NULL,
    source_seq    VARCHAR(50)  NOT NULL,
    report_no     VARCHAR(50),
    product_name  VARCHAR(500) NOT NULL,
    manufacturer  VARCHAR(255),
    reason        TEXT,
    expiry_text   VARCHAR(255),
    list_status   VARCHAR(20)  NOT NULL,
    product_id    BIGINT,
    match_status  VARCHAR(20)  NOT NULL,
    first_seen_at TIMESTAMP    NOT NULL,
    last_seen_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (source_seq),
    FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE recall_match_candidate (
    id         BIGINT        NOT NULL,
    recall_id  BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    score      NUMERIC(4, 3) NOT NULL,
    status     VARCHAR(20)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (recall_id, product_id),
    FOREIGN KEY (recall_id) REFERENCES recall (id),
    FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE shopping_link (
    id               BIGINT        NOT NULL,
    product_id       BIGINT        NOT NULL,
    url              TEXT          NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    match_confidence NUMERIC(4, 3),
    checked_at       TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE member (
    id         BIGINT       NOT NULL,
    email      VARCHAR(255),
    nickname   VARCHAR(100),
    created_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE social_account (
    id               BIGINT       NOT NULL,
    member_id        BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (provider, provider_user_id),
    FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE TABLE drawer_item (
    id         BIGINT    NOT NULL,
    member_id  BIGINT    NOT NULL,
    product_id BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (member_id, product_id),
    FOREIGN KEY (member_id) REFERENCES member (id),
    FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE notification (
    id            BIGINT      NOT NULL,
    member_id     BIGINT      NOT NULL,
    recall_id     BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    attempt_count INT         NOT NULL,
    sent_at       TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (member_id, recall_id),
    FOREIGN KEY (member_id) REFERENCES member (id),
    FOREIGN KEY (recall_id) REFERENCES recall (id)
);

CREATE TABLE ingestion_cursor (
    source              VARCHAR(20) NOT NULL,
    last_collected_date DATE        NOT NULL,
    updated_at          TIMESTAMP   NOT NULL,
    PRIMARY KEY (source)
);
