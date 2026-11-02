package com.alkkuni.batch.product

import com.alkkuni.core.parser.IngredientAmountParser
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
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest(
    properties = [
        // 스키마는 api가 가진 마이그레이션 파일로 만든다. 테스트 작업 디렉터리는 batch 모듈이다.
        "spring.flyway.locations=filesystem:../api/src/main/resources/db/migration",
        "foodsafety.api-key=test-key",
    ],
)
@Testcontainers
@ActiveProfiles("test")
class ProductUpserterTest(
    @Autowired private val upserter: ProductUpserter,
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
    fun `같은 행을 두 번 넣어도 제품·성분 행 수가 같다`() {
        val rows = listOf(row("A", VITAMIN), row("B", "1. 성상 : 분말"))

        assertEquals(2, upserter.upsert(rows))
        val first = counts()
        assertEquals(0, upserter.upsert(rows))

        assertEquals(first, counts())
        assertEquals(2 to 2, first)
        // 함량이 없어 성분 행이 없는 B도 판정 버전이 남는다(재판정 대상을 찾을 수 있다)
        assertEquals(
            IngredientAmountParser.VERSION,
            jdbc.queryForObject("SELECT parser_version FROM product WHERE report_no = 'B'", String::class.java),
        )
    }

    @Test
    fun `기준규격이 바뀌면 성분 행을 지우고 새로 넣는다`() {
        upserter.upsert(listOf(row("A", VITAMIN)))
        upserter.upsert(listOf(row("A", "2. 비타민C : 표시량(100mg/1g)")))

        assertEquals(1 to 1, counts())
        assertEquals("비타민C", jdbc.queryForObject("SELECT raw_name FROM product_ingredient", String::class.java))
    }

    @Test
    fun `검수한 제품은 원문이 같으면 다른 값만 갱신하고 성분 행은 그대로 둔다`() {
        upserter.upsert(listOf(row("A", VITAMIN)))
        jdbc.update("UPDATE product SET parse_status = 'REVIEWED'")

        upserter.upsert(listOf(row("A", VITAMIN, name = "새 이름")))

        assertEquals("REVIEWED", jdbc.queryForObject("SELECT parse_status FROM product", String::class.java))
        assertEquals("새 이름", jdbc.queryForObject("SELECT name FROM product", String::class.java))
        assertEquals(1 to 2, counts())
    }

    private fun counts() =
        jdbc.queryForObject("SELECT count(*) FROM product", Int::class.java)!! to
            jdbc.queryForObject("SELECT count(*) FROM product_ingredient", Int::class.java)!!

    private fun row(reportNo: String, standardText: String, name: String = "제품") = I0030Row(
        reportNo = reportNo,
        name = name,
        manufacturer = "제조사",
        intakeMethod = null,
        standardText = standardText,
        sourceUpdatedAt = LocalDateTime.of(2026, 11, 1, 0, 0),
    )

    private val VITAMIN = "2.감마리놀렌산 : 표시량(240mg/2g)의 80~120%\n3.비타민E : 표시량(17mg/2g)의 80~150%"
}
