-- 소식 목록(GET /api/news)이 쓰는 회수 등록일·등급 (PLANNING.md "메인 통계·소식 API")
-- 매일 회수 수집이 목록 전체를 다시 받아 덮어쓰므로 배포 다음 새벽에 LISTED 행이 채워진다. 이미 DELISTED인 행은 null로 남는다.
ALTER TABLE recall
    ADD COLUMN registered_at TIMESTAMP,   -- CRET_DTM. 식약처 등록 시각(KST)
    ADD COLUMN grade         VARCHAR(20); -- RTRVL_GRDCD_NM. 1·2·3등급, 자율회수 등 원문 그대로. 빈 값은 null
