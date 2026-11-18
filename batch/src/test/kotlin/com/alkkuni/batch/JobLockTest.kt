package com.alkkuni.batch

import org.flywaydb.core.Flyway
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 잡 단위 테스트용. DB 없이 바로 실행한다. 작업이 넣은 건수는 [run]으로 확인한다. */
fun noLock(run: JobRun = JobRun()): JobLock = object : JobLock(mock(DataSource::class.java), Clock.systemDefaultZone(), "test") {
    override fun <T> withLock(key: String, block: (JobRun) -> T): T = block(run)
}

@Testcontainers
class JobLockTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:17")
    }

    private val dataSource by lazy { DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password) }
    private val jdbc by lazy { JdbcTemplate(dataSource) }
    private val clock = Clock.fixed(Instant.parse("2026-11-18T19:00:00Z"), ZoneId.of("Asia/Seoul"))

    // 잡마다 프로세스가 따로 뜨는 상황을 흉내 낸다. 두 JobLock은 서로 다른 연결을 쓴다.
    private fun lock(job: String = "incremental") = JobLock(dataSource, clock, job)

    @BeforeTest
    fun migrate() {
        Flyway.configure().dataSource(dataSource)
            .locations("filesystem:../api/src/main/resources/db/migration").load().migrate()
        jdbc.execute("TRUNCATE job_run")
    }

    private fun runs(): List<Map<String, Any?>> = jdbc.queryForList("SELECT * FROM job_run ORDER BY id")

    @Test
    fun `같은 키가 실행 중이면 다른 프로세스는 실패하고, 끝나면 다시 잡힌다`() {
        val first = lock()
        val second = lock()

        first.withLock("I0030") {
            val e = assertFailsWith<IllegalStateException> { second.withLock("I0030") {} }
            assertEquals("I0030 작업이 이미 실행 중이다. 끝난 뒤 다시 돌린다.", e.message)
            // 다른 키는 막지 않는다
            second.withLock("I0490") {}
        }

        assertEquals("ok", second.withLock("I0030") { "ok" })
    }

    @Test
    fun `작업이 실패해도 잠금이 풀린다`() {
        val first = lock()
        assertFailsWith<IllegalStateException> { first.withLock("I0030") { error("수집 실패") } }

        assertEquals("ok", lock().withLock("I0030") { "ok" })
    }

    @Test
    fun `성공하면 작업 이름·시각·건수가 SUCCEEDED로 남는다`() {
        lock("incremental").withLock("I0030") { run ->
            run.processed = 120
            run.changed = 7
        }

        val row = runs().single()
        assertEquals("incremental", row["job_name"])
        assertEquals("SUCCEEDED", row["status"])
        // timestamptz라 KST로 넣은 시각이 같은 순간으로 읽힌다(9시간 밀리지 않는다)
        assertEquals(clock.instant(), (row["started_at"] as java.sql.Timestamp).toInstant())
        assertNotNull(row["finished_at"])
        assertEquals(120, row["processed_count"])
        assertEquals(7, row["changed_count"])
        assertNull(row["error_message"])
    }

    @Test
    fun `작업이 예외로 끝나면 FAILED와 오류 메시지가 남고 예외는 그대로 나간다`() {
        val e = assertFailsWith<IllegalStateException> {
            lock("recall").withLock("I0490") { error("total_count가 다르다") }
        }
        assertEquals("total_count가 다르다", e.message)

        val row = runs().single()
        assertEquals("recall", row["job_name"])
        assertEquals("FAILED", row["status"])
        assertNotNull(row["finished_at"])
        assertEquals("java.lang.IllegalStateException: total_count가 다르다", row["error_message"])
    }

    @Test
    fun `잠금을 못 잡으면 그 실행도 FAILED로 남는다`() {
        lock("initial-load").withLock("I0030") {
            assertFailsWith<IllegalStateException> { lock("incremental").withLock("I0030") {} }
        }

        val failed = runs().single { it["job_name"] == "incremental" }
        assertEquals("FAILED", failed["status"])
        assertEquals(
            "java.lang.IllegalStateException: I0030 작업이 이미 실행 중이다. 끝난 뒤 다시 돌린다.",
            failed["error_message"],
        )
    }

    @Test
    fun `OOM 같은 Error로 끝나도 FAILED로 남는다`() {
        assertFailsWith<OutOfMemoryError> { lock().withLock("I0030") { throw OutOfMemoryError("Java heap space") } }

        val row = runs().single()
        assertEquals("FAILED", row["status"])
        assertEquals("java.lang.OutOfMemoryError: Java heap space", row["error_message"])
    }

    @Test
    fun `작업 중 연결이 끊겨도 원래 실패 원인이 남는다`() {
        val e = assertFailsWith<IllegalStateException> {
            lock().withLock("I0030") {
                // DB 재시작처럼 잠금 연결을 끊는다. 그 뒤 unlock·FAILED 기록이 실패한다.
                jdbc.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query LIKE '%pg_try_advisory_lock%' AND pid <> pg_backend_pid()")
                error("적재 실패")
            }
        }

        assertEquals("적재 실패", e.message)
        assertTrue(e.suppressed.isNotEmpty())
    }
}
