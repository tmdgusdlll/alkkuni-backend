-- 제품 이미지 1차와 구매 링크 1차(PLANNING.md "제품 이미지"·"구매 링크", ADR-008).
-- 둘 다 1차는 많이 보이는 제품만 운영자가 SQL로 넣는다. 등록·수정 API는 4단계 관리자 도구에서 만든다.

-- 이미지 URL만 저장한다. 저작권이 판매자·브랜드에 있어 파일을 내려받지 않는다. NULL이면 화면이 제형 아이콘을 보여준다.
ALTER TABLE product ADD COLUMN image_url TEXT CHECK (image_url LIKE 'https://%');

-- 구매 링크. 한 제품에 여러 개(쇼핑몰별, 상세·검색 링크)를 둘 수 있게 하고, 응답은 대표 하나만 낸다(ProductDetailService).
-- 매칭 확신도(ERD 초안의 match_confidence)는 자동 매칭을 붙일 때 더한다. 수동 링크는 사람이 확인한 것이라 필요 없다.
CREATE TABLE shopping_link (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    store      VARCHAR(20) NOT NULL CHECK (store IN ('COUPANG')),          -- 대가성 문구가 쇼핑몰(제휴 프로그램)마다 달라 화면이 이 값으로 고른다
    type       VARCHAR(20) NOT NULL CHECK (type IN ('DETAIL', 'SEARCH')),  -- DETAIL: 상품 상세, SEARCH: "제조사 + 제품명" 검색 결과(ADR-008 강등 링크)
    -- 화면이 href로 그대로 쓰므로 javascript: 같은 값이 들어가지 않게 https만 받는다.
    url        TEXT        NOT NULL CHECK (url LIKE 'https://%'),
    checked_at TIMESTAMP   NOT NULL                                        -- 사람이 같은 제품인지 확인한 시각(KST). 링크 점검 batch가 생기면 그 시각
);

CREATE INDEX idx_shopping_link_product ON shopping_link (product_id);
