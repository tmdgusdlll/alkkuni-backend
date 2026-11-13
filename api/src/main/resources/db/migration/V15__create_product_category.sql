-- 기능성 카테고리(눈·간·혈당 …). 한 제품이 여러 카테고리에 든다. 성분별 목록의 카테고리 필터에 쓴다.
-- 판정은 기능성 원문(product.functionality)으로 core의 FunctionalCategory가 한다(규칙과 근거는 그 파일).
-- 카테고리 목록도 그 enum이다. category에는 enum 이름(EYE 등)을 저장한다. 원문에서 계산하는 값이라 제품이 지워지면 함께 지운다.
-- 적재 때 ProductUpserter가 함께 쓰고, 이미 적재된 제품은 이 마이그레이션 뒤 batch의 alkkuni.job=link-categories로 한 번 판정한다.
-- 규칙을 바꿀 때도 같은 작업만 다시 돌리면 된다(마이그레이션 없음).
CREATE TABLE product_category (
    product_id BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    category   VARCHAR(30) NOT NULL,
    PRIMARY KEY (product_id, category)
);
