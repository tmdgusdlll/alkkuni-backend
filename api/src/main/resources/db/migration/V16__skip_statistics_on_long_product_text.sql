-- ANALYZE product가 로컬 5분 30초, 운영 약 11분 걸리던 문제(2026-11-17 확인).
-- 원인: DB collation(libc en_US.utf8)의 strcoll이 한글 긴 문자열에서 길이의 제곱에 비례해 느려진다.
-- 통계를 만들 때 표본 값을 정렬하는데, precautions(264초)·functionality(39초)가 대부분을 차지했다.
-- 두 열은 화면 표시용이라 WHERE·ORDER BY에 쓰지 않으므로 플래너 통계가 필요 없다. ANALYZE에서 건너뛴다.
-- 이 열을 검색 조건이나 정렬에 쓰게 되면 SET STATISTICS -1(기본값)로 되돌린다.
ALTER TABLE product ALTER COLUMN precautions SET STATISTICS 0;
ALTER TABLE product ALTER COLUMN functionality SET STATISTICS 0;
