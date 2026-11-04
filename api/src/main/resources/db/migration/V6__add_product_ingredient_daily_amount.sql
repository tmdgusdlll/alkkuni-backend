-- 성분 함량의 1일 기준 값. 단위는 비교할 수 있게 통일한다(mg, mg α-TE, CFU 등, core AmountUnit).
-- 원문 값(amount, unit, basis_amount, basis_unit)은 그대로 둔다. 계산할 수 없으면 NULL(core Product.dailyAmount).
ALTER TABLE product_ingredient
    ADD COLUMN daily_amount NUMERIC,
    ADD COLUMN daily_unit   VARCHAR(20);
