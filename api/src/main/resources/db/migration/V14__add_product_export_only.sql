-- 수출용 제품 표시. 국내에서 살 수 없는 제품이라 검색·목록에서 기본은 국내 제품 뒤로 보내고, market 필터로 고를 수 있게 한다.
-- 판정은 제품명 표기로 core의 ExportName이 한다(규칙과 근거는 그 파일). 적재 때 Product가 이름을 정하면서 함께 계산한다.
-- 이미 적재된 제품은 이 마이그레이션 뒤 batch의 alkkuni.job=mark-export로 한 번 판정한다(그 전까지는 모두 false).
-- 규칙을 바꿀 때도 같은 작업만 다시 돌리면 된다(마이그레이션 없음).
ALTER TABLE product ADD COLUMN export_only BOOLEAN NOT NULL DEFAULT false;
