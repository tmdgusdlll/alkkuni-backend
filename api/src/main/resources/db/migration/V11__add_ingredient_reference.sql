-- 성분별 1일 기준값과 상한. 값과 출처는 docs/daily-reference.csv(2026-11-09 검토).
-- 기준값: 비타민·무기질은 1일 영양성분 기준치(식품표시광고법 시행규칙 별표 5),
--         기능성 원료는 건강기능식품 고시형 원료 1일 섭취량의 하한(식품안전나라 I2710).
-- 상한: 2025 한국인 영양소 섭취기준 성인 상한섭취량(19세 이상 최댓값). 상한이 없는 성분은 NULL.
--       엽산 상한은 보충제 엽산 1mg을 DFE로 바꾼 1.7mg DFE다(보충제 1μg = 1.7μg DFE). 1일 함량이 mg DFE라 단위를 맞췄다.
-- 단위: product_ingredient.daily_unit과 같은 단위. daily_unit이 다른 행은 기준값·상한과 비교하지 않는다.
-- 상한 초과 여부는 저장하지 않고 응답 때 계산한다(Ingredient.exceedsUpperLimit). 값을 고치면 바로 반영된다.
ALTER TABLE ingredient ADD COLUMN reference_amount NUMERIC CHECK (reference_amount > 0);
ALTER TABLE ingredient ADD COLUMN reference_unit VARCHAR(20);
ALTER TABLE ingredient ADD COLUMN upper_limit NUMERIC CHECK (upper_limit > 0);
-- 단위 없이 값만 있으면 비교할 수 없으므로 막는다.
ALTER TABLE ingredient ADD CONSTRAINT ingredient_reference_unit_check
    CHECK ((reference_amount IS NULL AND upper_limit IS NULL) = (reference_unit IS NULL));

UPDATE ingredient SET reference_amount = 8.5, reference_unit = 'mg', upper_limit = 35 WHERE name = '아연';
UPDATE ingredient SET reference_amount = 0.01, reference_unit = 'mg', upper_limit = 0.1 WHERE name = '비타민 D';
UPDATE ingredient SET reference_amount = 1.5, reference_unit = 'mg', upper_limit = 50 WHERE name = '비타민 B6';
UPDATE ingredient SET reference_amount = 1.2, reference_unit = 'mg', upper_limit = NULL WHERE name = '비타민 B1';
UPDATE ingredient SET reference_amount = 1.4, reference_unit = 'mg', upper_limit = NULL WHERE name = '비타민 B2';
UPDATE ingredient SET reference_amount = 100, reference_unit = 'mg', upper_limit = 2000 WHERE name = '비타민 C';
UPDATE ingredient SET reference_amount = 11, reference_unit = 'mg α-TE', upper_limit = 540 WHERE name = '비타민 E';
UPDATE ingredient SET reference_amount = 0.055, reference_unit = 'mg', upper_limit = 0.4 WHERE name = '셀레늄';
UPDATE ingredient SET reference_amount = 15, reference_unit = 'mg NE', upper_limit = 850 WHERE name = '나이아신';
UPDATE ingredient SET reference_amount = 5, reference_unit = 'mg', upper_limit = NULL WHERE name = '판토텐산';
UPDATE ingredient SET reference_amount = 100000000, reference_unit = 'CFU', upper_limit = NULL WHERE name = '프로바이오틱스 수';
UPDATE ingredient SET reference_amount = 0.4, reference_unit = 'mg DFE', upper_limit = 1.70 WHERE name = '엽산';
UPDATE ingredient SET reference_amount = 0.03, reference_unit = 'mg', upper_limit = NULL WHERE name = '비오틴';
UPDATE ingredient SET reference_amount = 0.0024, reference_unit = 'mg', upper_limit = NULL WHERE name = '비타민 B12';
UPDATE ingredient SET reference_amount = 0.7, reference_unit = 'mg RAE', upper_limit = 3.0 WHERE name = '비타민 A';
UPDATE ingredient SET reference_amount = 2.4, reference_unit = 'mg', upper_limit = NULL WHERE name = '진세노사이드 Rg1, Rb1 및 Rg3의 합';
UPDATE ingredient SET reference_amount = 315, reference_unit = 'mg', upper_limit = 350 WHERE name = '마그네슘';
UPDATE ingredient SET reference_amount = 3.0, reference_unit = 'mg', upper_limit = 11 WHERE name = '망간';
UPDATE ingredient SET reference_amount = 700, reference_unit = 'mg', upper_limit = 3000 WHERE name = '칼슘';
UPDATE ingredient SET reference_amount = 500, reference_unit = 'mg', upper_limit = NULL WHERE name = 'EPA와 DHA의 합';
UPDATE ingredient SET reference_amount = 0.07, reference_unit = 'mg', upper_limit = NULL WHERE name = '비타민 K';
UPDATE ingredient SET reference_amount = 0.03, reference_unit = 'mg', upper_limit = NULL WHERE name = '크롬';
UPDATE ingredient SET reference_amount = 750, reference_unit = 'mg', upper_limit = NULL WHERE name = '총 (-)-하이드록시시트르산(HCA)';
UPDATE ingredient SET reference_amount = 12, reference_unit = 'mg', upper_limit = 45 WHERE name = '철';
UPDATE ingredient SET reference_amount = 10, reference_unit = 'mg', upper_limit = NULL WHERE name = '루테인';
UPDATE ingredient SET reference_amount = 0.8, reference_unit = 'mg', upper_limit = 10 WHERE name = '구리';
UPDATE ingredient SET reference_amount = 130, reference_unit = 'mg', upper_limit = NULL WHERE name = '실리마린';
UPDATE ingredient SET reference_amount = 5000, reference_unit = 'mg', upper_limit = NULL WHERE name = '식이섬유';
UPDATE ingredient SET reference_amount = 0.45, reference_unit = 'mg', upper_limit = NULL WHERE name = '코로솔산';
UPDATE ingredient SET reference_amount = 1500, reference_unit = 'mg', upper_limit = NULL WHERE name = '엠에스엠(MSM)';
UPDATE ingredient SET reference_amount = 0.025, reference_unit = 'mg', upper_limit = 0.65 WHERE name = '몰리브덴';
UPDATE ingredient SET reference_amount = 28, reference_unit = 'mg', upper_limit = NULL WHERE name = '플라보놀 배당체';
UPDATE ingredient SET reference_amount = 90, reference_unit = 'mg', upper_limit = NULL WHERE name = '코엔자임Q10';
UPDATE ingredient SET reference_amount = 0.15, reference_unit = 'mg', upper_limit = 2.4 WHERE name = '요오드';
UPDATE ingredient SET reference_amount = 10, reference_unit = 'mg', upper_limit = NULL WHERE name = '옥타코사놀';
UPDATE ingredient SET reference_amount = 300, reference_unit = 'mg', upper_limit = NULL WHERE name = '카테킨';
UPDATE ingredient SET reference_amount = 200, reference_unit = 'mg', upper_limit = NULL WHERE name = 'L-테아닌';
UPDATE ingredient SET reference_amount = 70, reference_unit = 'mg', upper_limit = NULL WHERE name = '로르산';
UPDATE ingredient SET reference_amount = 3000, reference_unit = 'mg', upper_limit = NULL WHERE name = '프락토올리고당';
UPDATE ingredient SET reference_amount = 0.42, reference_unit = 'mg', upper_limit = NULL WHERE name = '베타카로틴';
UPDATE ingredient SET reference_amount = 300, reference_unit = 'mg', upper_limit = NULL WHERE name = '포스파티딜세린';
UPDATE ingredient SET reference_amount = 4, reference_unit = 'mg', upper_limit = NULL WHERE name = '아스타잔틴';
UPDATE ingredient SET reference_amount = 120, reference_unit = 'mg', upper_limit = NULL WHERE name = '히알루론산';
UPDATE ingredient SET reference_amount = 500, reference_unit = 'mg', upper_limit = NULL WHERE name = 'N-아세틸글루코사민';
UPDATE ingredient SET reference_amount = 160, reference_unit = 'mg', upper_limit = NULL WHERE name = '감마리놀렌산';
UPDATE ingredient SET reference_amount = 4, reference_unit = 'mg', upper_limit = NULL WHERE name = '총 모나콜린 K';
UPDATE ingredient SET reference_amount = 3, reference_unit = 'mg', upper_limit = NULL WHERE name = '진세노사이드 Rg1과 Rb1의 합';
UPDATE ingredient SET reference_amount = 1200, reference_unit = 'mg', upper_limit = NULL WHERE name = '뮤코다당·단백';
UPDATE ingredient SET reference_amount = 60, reference_unit = 'mg', upper_limit = NULL WHERE name = '폴리감마글루탐산';
UPDATE ingredient SET reference_amount = 8, reference_unit = 'mg', upper_limit = NULL WHERE name = '총 엽록소';
UPDATE ingredient SET reference_amount = 24, reference_unit = 'mg', upper_limit = NULL WHERE name = '대두이소플라본 비배당체';
UPDATE ingredient SET reference_amount = 3000, reference_unit = 'mg', upper_limit = NULL WHERE name = '크레아틴 모노하이드레이트';
UPDATE ingredient SET reference_amount = 3500, reference_unit = 'mg', upper_limit = NULL WHERE name = '칼륨';
UPDATE ingredient SET reference_amount = 55000, reference_unit = 'mg', upper_limit = NULL WHERE name = '단백질';
