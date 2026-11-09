-- 기능성 원료 상한: 건강기능식품 고시형 원료 1일 섭취량의 최고값(식품안전나라 I2710 DAY_INTK_HIGHLIMIT, 2026-11-09 조회).
-- 안전 상한이 아니라 고시가 인정한 최대 섭취량이다. 판정은 2배를 넘을 때만 하므로(Ingredient.suspectAbove) 표기·파싱 오류를 잡는 용도다.
-- 넣지 않은 성분(값과 이유는 docs/daily-reference.csv의 note):
--   프로바이오틱스 수(100억 CFU)·폴리감마글루탐산(70mg): 개별인정형 제품이 고시 최고량을 정상으로 넘는다(2배 초과가 각각 62·17개, 대부분 실제 제품).
--   실리마린·포스파티딜세린·크레아틴: 고시 하한과 최고값이 같은 고정량이라 고함량 수출용 제품이 걸린다.
--   식이섬유: 원료마다 최고량이 달라(구아검 11g, 대두 60g, 귀리 제한 없음) 하나로 정할 수 없다.
-- 로컬 기준 새로 2배를 넘는 제품은 13개다(베타카로틴 420mg, HCA 5,400mg, 엽록소 2,000mg 등).
UPDATE ingredient SET upper_limit = 80 WHERE name = '진세노사이드 Rg1, Rb1 및 Rg3의 합';
UPDATE ingredient SET upper_limit = 80 WHERE name = '진세노사이드 Rg1과 Rb1의 합';
UPDATE ingredient SET upper_limit = 2240 WHERE name = 'EPA와 DHA의 합';
UPDATE ingredient SET upper_limit = 1500 WHERE name = '총 (-)-하이드록시시트르산(HCA)';
UPDATE ingredient SET upper_limit = 20 WHERE name = '루테인';
UPDATE ingredient SET upper_limit = 1.3 WHERE name = '코로솔산';
UPDATE ingredient SET upper_limit = 2000 WHERE name = '엠에스엠(MSM)';
UPDATE ingredient SET upper_limit = 36 WHERE name = '플라보놀 배당체';
UPDATE ingredient SET upper_limit = 100 WHERE name = '코엔자임Q10';
UPDATE ingredient SET upper_limit = 40 WHERE name = '옥타코사놀';
UPDATE ingredient SET upper_limit = 1000 WHERE name = '카테킨';
UPDATE ingredient SET upper_limit = 250 WHERE name = 'L-테아닌';
UPDATE ingredient SET upper_limit = 115 WHERE name = '로르산';
UPDATE ingredient SET upper_limit = 8000 WHERE name = '프락토올리고당';
UPDATE ingredient SET upper_limit = 7 WHERE name = '베타카로틴';
UPDATE ingredient SET upper_limit = 12 WHERE name = '아스타잔틴';
UPDATE ingredient SET upper_limit = 240 WHERE name = '히알루론산';
UPDATE ingredient SET upper_limit = 1000 WHERE name = 'N-아세틸글루코사민';
UPDATE ingredient SET upper_limit = 300 WHERE name = '감마리놀렌산';
UPDATE ingredient SET upper_limit = 8 WHERE name = '총 모나콜린 K';
UPDATE ingredient SET upper_limit = 1500 WHERE name = '뮤코다당·단백';
UPDATE ingredient SET upper_limit = 150 WHERE name = '총 엽록소';
UPDATE ingredient SET upper_limit = 27 WHERE name = '대두이소플라본 비배당체';
