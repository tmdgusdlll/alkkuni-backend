package com.alkkuni.batch

import org.mockito.Mockito.mock
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 잡 단위 테스트용. DB 없이 바로 실행한다. */
fun noLock(): JobLock = object : JobLock(mock(DataSource::class.java)) {
    override fun <T> withLock(key: String, block: () -> T): T = block()
}

@Testcontainers
class JobLockTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:17")
    }

    // 잡마다 프로세스가 따로 뜨는 상황을 흉내 낸다. 두 JobLock은 서로 다른 연결을 쓴다.
    private fun lock() = JobLock(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password))

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
}
