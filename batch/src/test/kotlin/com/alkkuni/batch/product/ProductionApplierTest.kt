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
class ProductionApplierTest(
    @Autowired private val applier: ProductionApplier,
    @Autowired private val jdbc: JdbcTemplate,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE product, product_ingredient, product_category, shopping_link")
    }

    @Test
    fun `받은 연도 값을 붙이고 목록에 없는 제품은 옛 연도 값을 비운다`() {
        product("1")
        product("2")
        product("3", exportOnly = true)
        val stale = product("4")
        jdbc.update("UPDATE product SET production_year = 2024, production_qty = 99 WHERE id = ?", stale)

        val result = applier.apply(2025, mapOf("1" to 10L, "2" to 0L, "3" to 5L, "없는번호" to 7L))

        assertEquals(2025 to 10L, productionOf("1"))
        assertEquals(2025 to 0L, productionOf("2"))
        assertEquals(null to null, productionOf("4"))
        assertEquals(3, result.matched)
        assertEquals(1, result.cleared)
        // 생산량 > 0 인 1·3 중 수출용 3은 빠진다.
        assertEquals(1, result.estimated)
    }

    private fun product(reportNo: String, exportOnly: Boolean = false): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, export_only)
        VALUES (?, ?, false, 'ACTIVE', 'PARSED', '1', ?) RETURNING id
        """,
        Long::class.java,
        reportNo, "제품$reportNo", exportOnly,
    )!!

    private fun productionOf(reportNo: String): Pair<Int?, Long?> = jdbc.queryForObject(
        "SELECT production_year, production_qty FROM product WHERE report_no = ?",
        { rs, _ -> rs.getObject(1, Integer::class.java)?.toInt() to rs.getObject(2, java.lang.Long::class.java)?.toLong() },
        reportNo,
    )!!
}
