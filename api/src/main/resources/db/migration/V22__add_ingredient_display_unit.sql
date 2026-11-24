-- 화면에 함량을 보여줄 질량 단위. NULL이면 저장 단위(mg) 그대로 보여준다.
-- 1일 함량(daily_amount)은 비교·정렬을 위해 mg으로 저장하므로, 비타민 D 10μg이 0.01mg으로 보였다.
-- 영양성분 기준치(식품표시광고법 시행규칙 별표 5)를 μg으로 적는 성분만 μg으로 보여준다.
-- 환산 표기는 화면이 값의 단위에서 이어 붙인다(엽산 "mg DFE" → "μg DFE", 비타민 A "mg RAE" → "μg RAE").
-- 저장·계산(정렬, 함량 범위, 기준 대비 %, 상한)은 바뀌지 않는다.
ALTER TABLE ingredient ADD COLUMN display_unit VARCHAR(20) CHECK (display_unit IN ('μg'));

DO $$
DECLARE
    updated INT;
BEGIN
    UPDATE ingredient SET display_unit = 'μg'
    WHERE name IN ('비타민 D', '비타민 K', '비타민 B12', '비오틴', '셀레늄', '크롬', '요오드', '몰리브덴', '엽산', '비타민 A');
    GET DIAGNOSTICS updated = ROW_COUNT;
    -- 이름 오타로 일부 성분이 mg으로 남는 것을 막는다
    IF updated <> 10 THEN
        RAISE EXCEPTION 'display_unit 대상 성분 10개 중 %개만 찾음', updated;
    END IF;
END $$;
