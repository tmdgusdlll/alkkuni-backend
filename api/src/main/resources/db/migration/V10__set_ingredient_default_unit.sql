-- 한 성분 안에 1일 함량 단위가 섞인 성분의 주 단위. 성분별 제품 목록은 주 단위 행끼리 먼저 함량순으로 정렬하고,
-- 다른 단위 행은 그 뒤에 따로 정렬한다. 함량 범위 필터도 주 단위 행에만 건다.
-- 원료(합성/천연, 레티놀/베타카로틴, d/dl형)에 따라 환산 계수가 달라 단위끼리 환산하지 않는다.
-- 로컬 실측(2026-11-08, 원료 제외 제품 수): 엽산 mg DFE 2,790 / mg 1,699, 비타민 A mg RAE 2,527 / mg RE 1,343 / mg 191,
-- 나이아신 mg NE 4,450 / mg 1,332, 비타민 E mg α-TE 6,705 / mg 256.
-- 다른 성분은 섞인 단위가 1~5행(파서 오류 수준)이라 비워 둔다. 비어 있으면 단위를 가리지 않는다.
UPDATE ingredient SET default_unit = 'mg DFE' WHERE name = '엽산';
UPDATE ingredient SET default_unit = 'mg RAE' WHERE name = '비타민 A';
UPDATE ingredient SET default_unit = 'mg NE' WHERE name = '나이아신';
UPDATE ingredient SET default_unit = 'mg α-TE' WHERE name = '비타민 E';
