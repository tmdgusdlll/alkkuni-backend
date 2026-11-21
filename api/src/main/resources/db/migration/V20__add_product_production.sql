-- 판매 추정 1차(PLANNING.md "검색 결과 표시·노출 순서" 4): 공개된 최신 연도 생산실적(I0310)을 제품에 붙인다.
-- 판매 추정 = production_qty > 0 AND NOT export_only. 수출용 판정이 바뀌어도 어긋나지 않게 판정 결과는 저장하지 않는다.
-- `alkkuni.job=production`이 연도 전체를 받아 덮어쓴다. 그해 생산실적에 없는 제품은 둘 다 NULL이다.
-- 확인 시각은 칼럼을 두지 않는다. 연도 전체를 한 번에 바꾸므로 job_run의 마지막 성공 시각이 곧 확인 시각이다.
ALTER TABLE product ADD COLUMN production_year SMALLINT;
ALTER TABLE product ADD COLUMN production_qty BIGINT CHECK (production_qty >= 0);
