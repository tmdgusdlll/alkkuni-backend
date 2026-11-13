package com.alkkuni.batch.product

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals

@SpringBootTest(
    properties = [
        "spring.flyway.locations=filesystem:../api/src/main/resources/db/migration",
        "foodsafety.api-key=test-key",
    ],
)
@Testcontainers
@ActiveProfiles("test")
class ExportMarkerTest(
    @Autowired private val marker: ExportMarker,
    @Autowired private val jdbc: JdbcTemplate,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE product, product_ingredient")
    }

    @Test
    fun `기존 제품을 제품명으로 다시 판정하고 몇 번 돌려도 같다`() {
        // 마이그레이션 직후처럼 모두 false로 들어 있다. 국내 제품이 true로 잘못 남은 경우도 되돌린다.
        val export = product("1", "석류미인(전량수출용)", exportOnly = false)
        val country = product("2", "석류미인(영국, 독일)", exportOnly = false)
        val domestic = product("3", "석류미인", exportOnly = true)

        val first = marker.mark()

        assertEquals(true, exportOf(export))
        assertEquals(true, exportOf(country))
        assertEquals(false, exportOf(domestic))
        assertEquals(3, first.changed)
        assertEquals(2, first.exportOnly)
        assertEquals(0, marker.mark().changed)
    }

    private fun product(reportNo: String, name: String, exportOnly: Boolean): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, export_only)
        VALUES (?, ?, false, 'ACTIVE', 'PARSED', '1', ?) RETURNING id
        """,
        Long::class.java,
        reportNo, name, exportOnly,
    )!!

    private fun exportOf(id: Long): Boolean =
        jdbc.queryForObject("SELECT export_only FROM product WHERE id = ?", Boolean::class.java, id)!!
}
