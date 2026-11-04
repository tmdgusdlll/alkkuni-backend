-- 섭취방법(intake_method)을 파싱한 값과, 기준규격 분모가 하루치인지 확인한 결과.
-- 1회 중량은 무게면 mg, 부피면 ml로 통일한다(intake_amount_unit). 밀도를 몰라 둘 사이는 바꾸지 않는다.
ALTER TABLE product
    ADD COLUMN intake_times_per_day   INTEGER,
    ADD COLUMN intake_units_per_time  NUMERIC,
    ADD COLUMN intake_unit            VARCHAR(20),
    ADD COLUMN intake_amount_per_time NUMERIC,
    ADD COLUMN intake_amount_unit     VARCHAR(10),
    ADD COLUMN basis_check            VARCHAR(20);
