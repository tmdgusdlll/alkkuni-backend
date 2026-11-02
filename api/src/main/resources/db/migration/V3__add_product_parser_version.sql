-- 제품을 몇 번 규칙으로 판정했는지 남긴다. 규칙이 바뀌면 이 값이 옛 버전인 제품만 다시 판정한다.
-- product_ingredient.parser_version만으로는 성분 행이 없는 제품(함량 0건, 원료성 제품 대부분)을 찾을 수 없다.
ALTER TABLE product ADD COLUMN parser_version VARCHAR(20);
-- 이 마이그레이션 전에 적재된 제품(로컬뿐)은 모두 버전 1 규칙으로 판정됐다.
UPDATE product SET parser_version = '1';
ALTER TABLE product ALTER COLUMN parser_version SET NOT NULL;
