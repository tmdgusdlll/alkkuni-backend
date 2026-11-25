-- 브랜드 대표 이름을 회사 정식명으로 바꾼다(2026-11-25 사용자). 카드 위 브랜드 줄과 브랜드 목록이 이 이름을 그대로 보여 준다.
-- 줄임말 표기("광동"·"일양"·"일동")는 V13 별칭으로 남아 연결과 검색은 그대로다. 정식명 표기도 이미 별칭에 있다.
UPDATE brand SET name = '광동제약' WHERE name = '광동';
UPDATE brand SET name = '일양약품' WHERE name = '일양';
UPDATE brand SET name = '일동제약' WHERE name = '일동';

-- "웅진" 접두어에 서로 다른 회사가 섞여 있었다. 알로에겔은 웅진식품(제조사도 웅진식품), "웅진 오직 …"은 웅진헬스원의 브랜드 오직(Ogic)이다.
-- 오직은 "웅진" 없이 "오직 관절"·"오직:프로바이오틱스"로도 신고돼 있다. 앞부분 일치라 "오직"으로 시작하는 다른 회사 제품이 생기면 잘못 붙는다(로컬 0개).
-- "웅진 어린이 홍삼"(2020, 위탁생산)은 판매사를 확인하지 못해 보류한다. 가장 긴 표기가 먼저 맞으므로 "웅진" → 웅진식품으로 내려가지 않는다.
UPDATE brand SET name = '웅진식품' WHERE name = '웅진';
INSERT INTO brand (name) VALUES ('오직');

INSERT INTO brand_alias (alias_key, brand_id, whole_word) VALUES
    ('웅진식품', (SELECT id FROM brand WHERE name = '웅진식품'), false),
    ('웅진오직', (SELECT id FROM brand WHERE name = '오직'), false),
    ('오직', (SELECT id FROM brand WHERE name = '오직'), false),
    ('웅진어린이홍삼', NULL, false);

-- 배포 뒤 batch의 alkkuni.job=link-brands로 기존 제품을 다시 연결한다.
