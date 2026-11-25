-- 브랜드 대표 이름을 회사 정식명으로 바꾼다(2026-11-25 사용자). 카드 위 브랜드 줄과 브랜드 목록이 이 이름을 그대로 보여 준다.
-- 줄임말 표기("광동"·"일양"·"일동")는 V13 별칭으로 남아 연결과 검색은 그대로다. 정식명 표기도 이미 별칭에 있다.
UPDATE brand SET name = '광동제약' WHERE name = '광동';
UPDATE brand SET name = '일양약품' WHERE name = '일양';
UPDATE brand SET name = '일동제약' WHERE name = '일동';

-- "웅진" 접두어에 서로 다른 회사가 섞여 있었다. 알로에겔은 웅진식품(제조사도 웅진식품), "웅진 오직 …"은 웅진헬스원의 브랜드 오직(Ogic)이다.
-- 오직은 "웅진" 없이 "오직 관절"·"오직:프로바이오틱스"로도 신고돼 있다. "오직"은 흔한 낱말이라 첫 단어가 "오직"일 때만 맞추고,
-- 붙여 쓴 "오직:"은 따로 둔다("오직홍삼 …"은 연결하지 않는다). "웅진오직"은 "웅진"보다 길어 먼저 맞는다.
-- "웅진 어린이 홍삼"(2020, 위탁생산)은 판매사를 확인하지 못해 보류한다. 가장 긴 표기가 먼저 맞으므로 "웅진" → 웅진식품으로 내려가지 않는다.
UPDATE brand SET name = '웅진식품' WHERE name = '웅진';
INSERT INTO brand (name) VALUES ('오직');

INSERT INTO brand_alias (alias_key, brand_id, whole_word) VALUES
    ('웅진식품', (SELECT id FROM brand WHERE name = '웅진식품'), false),
    ('웅진오직', (SELECT id FROM brand WHERE name = '오직'), false),
    ('오직', (SELECT id FROM brand WHERE name = '오직'), true),
    ('오직:', (SELECT id FROM brand WHERE name = '오직'), false),
    ('웅진어린이홍삼', NULL, false);

-- 이름만 바꾸면 이미 웅진(이제 웅진식품)에 연결된 오직·어린이 홍삼 제품이 link-brands 전까지 "웅진식품"으로 보인다.
-- 바뀌는 제품만 BrandName.nameKey·firstWordKey 규칙을 SQL로 흉내 내 바로 옮긴다. 기준은 여전히 batch의 alkkuni.job=link-brands다.
-- ponytail: PostgreSQL \s는 NBSP를 공백으로 안 볼 수 있다. 그런 이름이 남아도 배포 뒤 link-brands가 맞춘다.
WITH k AS (
    SELECT id,
           regexp_replace(name, '^\s*[\[(<【]\s*', '') AS head,
           upper(regexp_replace(regexp_replace(name, '^\s*[\[(<【]\s*', ''), '\s', '', 'g')) AS key
    FROM product
)
UPDATE product p
SET brand_id = CASE WHEN k.key LIKE '웅진어린이홍삼%' THEN NULL ELSE (SELECT id FROM brand WHERE name = '오직') END
FROM k
WHERE p.id = k.id
  AND (k.key LIKE '웅진어린이홍삼%'
       OR k.key LIKE '웅진오직%'
       OR k.key LIKE '오직:%'
       OR k.head ~ '^오직([\s()\[\]<>【】,/+&_·]|$)');
