-- batch 작업 실행 기록. 실행마다 한 줄. batch의 JobLock.withLock이 모든 작업에서 쓴다(작업마다 따로 쓰지 않는다).
-- 알림은 batch가 보내지 않고 Grafana가 "작업별 마지막 SUCCEEDED 시각"을 본다(PLANNING.md "모니터링·알림").
-- 그래야 cron이 빠지거나 서버가 죽어 아예 안 돈 경우도 잡힌다.
-- 4단계 관리자 수동 실행 요청(collect_request)도 이 테이블에 합친다(2026-11-18). 요청 행이 그대로 실행 기록이 된다.
-- 그때 status에 REQUESTED를 더하고, 요청자·요청 시각 칼럼을 추가하고, started_at의 NOT NULL을 푼다.
-- 시각은 다른 테이블(KST TIMESTAMP)과 달리 timestamptz다. 주 용도가 "now()로부터 몇 시간 지났나"인데,
-- TIMESTAMP에 KST를 넣으면 DB now()(UTC)와 9시간 어긋난다. timestamptz는 어느 세션 시간대에서 빼도 맞다.
CREATE TABLE job_run (
    id              BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(50) NOT NULL,       -- alkkuni.job 값(incremental, recall …)
    status          VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,                -- RUNNING이 오래 남아 있으면 프로세스가 죽은 것이다
    processed_count INT,                        -- 받거나 살펴본 건수. 작업이 알려 줄 때만
    changed_count   INT,                        -- 새로 쓰거나 바뀐 건수. 작업이 알려 줄 때만
    error_message   TEXT
);

-- "작업별 마지막 성공 시각"과 최근 실행 목록 조회용
CREATE INDEX idx_job_run_job_started ON job_run (job_name, started_at DESC);
