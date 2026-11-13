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
class CategoryLinkerTest(
    @Autowired private val linker: CategoryLinker,
    @Autowired private val jdbc: JdbcTemplate,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE product, product_ingredient, product_category")
    }

    @Test
    fun `기존 제품을 기능성 원문으로 다시 판정하고 몇 번 돌려도 같다`() {
        // 마이그레이션 직후처럼 비어 있는 제품, 옛 규칙으로 잘못 남은 행이 있는 제품, 영양소 문구만 있는 제품
        val eye = product("1", "①눈 건강에 도움을 줄 수 있음 ②간 건강에 도움을 줄 수 있음")
        val stale = product("2", "체지방 감소에 도움을 줄 수 있음")
        jdbc.update("INSERT INTO product_category VALUES (?, 'FATIGUE'), (?, 'BODY_FAT')", stale, stale)
        val vitamin = product("3", "정상적인 면역기능에 필요")

        val first = linker.link()

        assertEquals(listOf("EYE", "LIVER"), categoriesOf(eye))
        assertEquals(listOf("BODY_FAT"), categoriesOf(stale))
        assertEquals(emptyList<String>(), categoriesOf(vitamin))
        assertEquals(3, first.changed) // EYE·LIVER 넣고 FATIGUE 지움
        assertEquals(3L to 2L, first.target to first.categorized)
        assertEquals(0, linker.link().changed)
    }

    private fun product(reportNo: String, functionality: String): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, functionality)
        VALUES (?, '제품', false, 'ACTIVE', 'PARSED', '1', ?) RETURNING id
        """,
        Long::class.java,
        reportNo, functionality,
    )!!

    private fun categoriesOf(id: Long): List<String> =
        jdbc.queryForList("SELECT category FROM product_category WHERE product_id = ? ORDER BY 1", String::class.java, id).filterNotNull()
}
