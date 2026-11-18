package com.alkkuni.batch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.sql.Connection
import java.time.Clock
import java.time.OffsetDateTime
import javax.sql.DataSource

/** 작업이 실행 기록에 남길 건수. 알려 주지 않으면 NULL로 남는다. 도중에 실패해도 그때까지 넣은 값이 남는다. */
class JobRun {
    var processed: Int? = null
    var changed: Int? = null
}

/**
 * 같은 출처를 다루는 배치가 겹쳐 돌지 않게 PostgreSQL advisory lock을 잡고, 실행마다 `job_run`에 한 줄을 남긴다.
 * 초기 적재와 증분이 동시에 돌면 같은 신고번호를 둘 다 INSERT하다 UNIQUE 위반으로 실패하고, 커서도 엇갈린다.
 *
 * 세션 잠금이라 작업이 끝날 때까지 연결 하나를 붙잡는다. 프로세스가 죽으면 연결이 끊기며 잠금도 풀린다.
 * 잠금을 못 잡으면 기다리지 않고 예외로 끝낸다. 다음 cron 실행이 다시 시도한다.
 *
 * 실행 기록:
 * - 모든 작업이 여기를 지나므로 기록도 여기서만 한다. 작업 이름은 잠금 키(여러 작업이 I0030을 같이 쓴다)가 아니라 `alkkuni.job` 값이다.
 * - 잠금 연결(autocommit)로 바로 커밋한다. 작업 트랜잭션과 따로라 작업이 롤백돼도 FAILED 기록은 남는다.
 * - 잠금 실패, OOM 같은 Error도 FAILED로 남긴다. 프로세스가 죽으면 RUNNING으로 남는다.
 * - 기록이 작업 결과를 바꾸지 않는다. 끝 기록이 실패해도 성공한 작업은 성공, 실패한 작업은 원래 예외로 끝난다.
 */
@Component
class JobLock(
    private val dataSource: DataSource,
    private val clock: Clock,
    @Value("\${alkkuni.job:unknown}") private val jobName: String,
) {

    fun <T> withLock(key: String, block: (JobRun) -> T): T =
        dataSource.connection.use { conn ->
            val id = start(conn)
            val run = JobRun()
            val result = try {
                check(query(conn, "select pg_try_advisory_lock(hashtext(?))", key)) {
                    "$key 작업이 이미 실행 중이다. 끝난 뒤 다시 돌린다."
                }
                try {
                    block(run)
                } catch (e: Throwable) {
                    // 연결이 끊겨 unlock도 실패하면 그 예외가 원래 실패 원인을 덮지 않게 붙이기만 한다.
                    runCatching { unlock(conn, key) }.onFailure(e::addSuppressed)
                    throw e
                }.also { unlock(conn, key) }
            } catch (e: Throwable) {
                runCatching { finish(conn, id, "FAILED", run, e.toString()) }.onFailure(e::addSuppressed)
                throw e
            }
            runCatching { finish(conn, id, "SUCCEEDED", run, null) }
                .onFailure { log.error("{} 성공 기록을 남기지 못했다(job_run id={})", jobName, id, it) }
            result
        }

    private fun start(conn: Connection): Long =
        conn.prepareStatement(
            "insert into job_run (job_name, status, started_at) values (?, 'RUNNING', ?) returning id",
        ).use { ps ->
            ps.setString(1, jobName)
            ps.setObject(2, now())
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }

    private fun finish(conn: Connection, id: Long, status: String, run: JobRun, error: String?) {
        conn.prepareStatement(
            """
            update job_run set status = ?, finished_at = ?, processed_count = ?, changed_count = ?, error_message = ?
            where id = ?
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, status)
            ps.setObject(2, now())
            ps.setObject(3, run.processed)
            ps.setObject(4, run.changed)
            ps.setString(5, error)
            ps.setLong(6, id)
            ps.executeUpdate()
        }
    }

    // timestamptz 칼럼에는 오프셋이 있는 값을 넣는다. LocalDateTime을 넣으면 DB 세션 시간대(운영 UTC)로 읽혀 9시간 어긋난다.
    private fun now() = OffsetDateTime.now(clock)

    private fun unlock(conn: Connection, key: String) {
        query(conn, "select pg_advisory_unlock(hashtext(?))", key)
    }

    private fun query(conn: Connection, sql: String, key: String): Boolean =
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getBoolean(1)
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(JobLock::class.java)
    }
}
