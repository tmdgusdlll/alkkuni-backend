-- 검색 관련도순의 신제품(중립) 판정이 검색마다 max(production_year)로 최신 실적 연도를 구한다(ProductSearchRepository.salesTier).
-- 인덱스가 없으면 product 전체를 훑는다(로컬 4.6만 개 57ms). 인덱스가 있으면 끝값 하나만 읽는다.
CREATE INDEX idx_product_production_year ON product (production_year);
