-- 흔한 원료 이름 검색어를 고시 지표 성분으로 묶는다.
-- 사전에는 고시 성분 이름(실리마린, EPA와 DHA의 합)만 있어 "밀크씨슬"·"오메가3" 검색이 성분으로 걸리지 않았고,
-- 그래서 이름이 그냥 "밀크씨슬"인 생산 0 제품이 정확 일치로 맨 위를 차지했다(성분 이름 검색은 정확 일치를 쓰지 않는다).
-- 오메가3는 식물성(알파-리놀렌산 포함 합) 제품도 있지만 대부분(로컬 2,714 대 32)인 EPA와 DHA의 합으로 둔다.
-- 성분 연결(link-ingredients)에도 쓰인다. 원문이 "밀크씨슬"인 성분 행(로컬 3개)은 모두 130mg(실리마린 고시량)이라 실리마린으로 연결돼도 맞다.
-- 대표 이름이 없으면 ingredient_id가 NULL이 되어 NOT NULL 위반으로 실패한다.
INSERT INTO ingredient_alias (alias_key, ingredient_id)
SELECT v.alias_key, (SELECT id FROM ingredient WHERE name = v.name)
FROM (VALUES
    ('밀크씨슬', '실리마린'),
    ('밀크시슬', '실리마린'),
    ('오메가3', 'EPA와 DHA의 합'),
    ('오메가-3', 'EPA와 DHA의 합')
) AS v(alias_key, name);
