package com.alkkuni.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 실제 PostgreSQL에 붙어 컨텍스트가 뜨고 Flyway 마이그레이션이 적용되는지 확인한다.
@SpringBootTest
@Testcontainers
class AlkkuniApiApplicationTest(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val healthEndpoint: HealthEndpoint,
) {
    companion object {
        // 로컬 compose와 같은 버전
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @Test
    fun `Flyway가 V1을 적용해 pg_trgm 확장이 생긴다`() {
        val versions = jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success",
            String::class.java,
        )
        assertTrue("1" in versions, "V1이 적용되지 않음: $versions")

        val extensions = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'",
            Int::class.java,
        )
        assertEquals(1, extensions)
    }

    @Test
    fun `pg_trgm이 한글을 글자로 인식해 trigram을 만든다`() {
        // 로케일이 C면 한글을 글자로 보지 않아 빈 배열이 나온다.
        // 멀티바이트 trigram은 해시(0x...)로 저장되므로 내용 대신 개수로 확인한다: "  비", " 비타", "비타민", "타민 "
        val trigrams = jdbcTemplate.queryForList("SELECT unnest(show_trgm('비타민'))", String::class.java)
        assertEquals(4, trigrams.size, "한글 trigram 개수가 다름: $trigrams")
    }

    @Test
    fun `헬스 체크가 UP이다`() {
        assertEquals(Status.UP, healthEndpoint.health().status)
    }
}
