-- 구매 링크를 쿠팡 파트너스에서 네이버 쇼핑 커넥트로 바꾼다(ADR-009, 2026-11-25).
-- 쿠팡은 건강기능식품 상품 대부분이 "법적으로 또는 판매자의 요청에 따라 파트너스 링크 생성이 제한된 상품"이라 링크를 만들 수 없었다.
-- 운영에 넣은 쿠팡 링크는 없다(2026-11-25 운영 count 0). 혹시 남아 있으면 새 제약에서 실패하므로 지우고 바꾼다.
DELETE FROM shopping_link WHERE store = 'COUPANG';
ALTER TABLE shopping_link DROP CONSTRAINT shopping_link_store_check;
ALTER TABLE shopping_link ADD CONSTRAINT shopping_link_store_check CHECK (store IN ('NAVER'));
