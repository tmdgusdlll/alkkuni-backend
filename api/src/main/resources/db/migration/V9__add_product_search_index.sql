-- 제품 검색(GET /api/products)의 제품명·제조사 부분 일치(ILIKE '%q%')와 유사도(<%)를 trigram 인덱스로 처리한다.
-- 로컬 4.6만 건 실측(2026-11-07): "루테인" 목록 쿼리 42ms → 6~9ms.
-- 3자 미만("아연")은 trigram을 만들 수 없어 인덱스를 못 타고 전체 스캔 그대로다(목록 85ms, count 26ms).
-- product_ingredient(ingredient_id) 인덱스는 넣지 않는다. 같은 실측에서 성분 쪽은 전체 스캔도 수 ms였고,
-- 인덱스를 넣으면 계획이 병렬 스캔을 버려 "아연" count가 26ms → 53ms로 느려졌다.
CREATE INDEX idx_product_name_trgm ON product USING gin (name gin_trgm_ops);
CREATE INDEX idx_product_manufacturer_trgm ON product USING gin (manufacturer gin_trgm_ops);
