package com.alkkuni.api.product

import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal

// 성분 사전 연결, 성분표 순서, 신고번호로 붙는 회수 이력을 실제 DB로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ProductDetailApiTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {
    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer("postgres:17")
    }

    private var seq = 0

    @BeforeEach
    fun clean() {
        jdbc.update("DELETE FROM product_ingredient")
        jdbc.update("DELETE FROM product")
        jdbc.update("DELETE FROM recall")
    }

    @Test
    fun `제품 정보와 성분표를 원문 순서대로 내보내고 같은 성분 행도 합치지 않는다`() {
        val id = product("멀티팩", reportNo = "2004001700001", precautions = "임산부는 섭취 주의")
        ingredient(id, "비타민 C", "100")
        ingredient(id, "아연", "5.95")
        ingredient(id, "아연", "2.55")
        ingredient(id, null, "3", rawName = "미확인성분X")

        mvc.get("/api/products/$id").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=60, public") }
            jsonPath("$.data.name") { value("멀티팩") }
            jsonPath("$.data.reportNo") { value("2004001700001") }
            jsonPath("$.data.precautions") { value("임산부는 섭취 주의") }
            jsonPath("$.data.ingredients[*].rawName") { value(contains("비타민 C", "아연", "아연", "미확인성분X")) }
            jsonPath("$.data.ingredients[1].dailyAmount") { value(5.95) }
            jsonPath("$.data.ingredients[3].ingredientId") { value(null as Any?) }
            jsonPath("$.data.ingredients[3].name") { value("미확인성분X") }
        }
    }

    @Test
    fun `사전에 연결된 성분은 사전 표시명을 쓴다`() {
        val id = product("A")
        ingredient(id, "아연", "8", rawName = "아연(Zn)")
        val displayName = jdbc.queryForObject("SELECT display_name FROM ingredient WHERE name = '아연'", String::class.java)

        mvc.get("/api/products/$id").andExpect {
            jsonPath("$.data.ingredients[0].name") { value(displayName) }
            jsonPath("$.data.ingredients[0].rawName") { value("아연(Zn)") }
        }
    }

    @Test
    fun `신고번호가 정확히 같은 회수만 붙는다`() {
        val id = product("메타그린 슬림업", reportNo = "20040017031282")
        recall("20040017031282", "메타그린 슬림업", "초산에틸 검출")
        recall("20040017031282", "메타그린 슬림업", "초산에틸 검출(재검사)", listStatus = "DELISTED")
        // 이름이 같아도 신고번호가 다르면 같은 제품인지 알 수 없어 붙이지 않는다
        recall("20040017031273", "메타그린 슬림업", "다른 품목")
        recall(null, "메타그린 슬림업", "신고번호 없음")
        // 일반 식품 회수는 번호가 우연히 같아도 붙이지 않는다
        recall("20040017031282", "슬림 음료", "일반 식품", productType = "가공식품")

        mvc.get("/api/products/$id").andExpect {
            jsonPath("$.data.recalls[*].reason") { value(contains("초산에틸 검출", "초산에틸 검출(재검사)")) }
            jsonPath("$.data.recalls[1].listStatus") { value("DELISTED") }
        }
    }

    @Test
    fun `회수가 없으면 빈 목록이다`() {
        val id = product("A")

        mvc.get("/api/products/$id").andExpect {
            jsonPath("$.data.recalls.length()") { value(0) }
            jsonPath("$.data.ingredients.length()") { value(0) }
        }
    }

    @Test
    fun `없거나 삭제된 제품은 404다`() {
        val deleted = product("B", status = "DELETED")

        listOf("999999", "$deleted").forEach { id ->
            mvc.get("/api/products/$id").andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("PRODUCT_NOT_FOUND") }
                header { doesNotExist("Cache-Control") }
            }
        }
    }

    @Test
    fun `숫자가 아닌 id는 400이고 compare 경로는 상세로 잡히지 않는다`() {
        mvc.get("/api/products/abc").andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
        }
        // ids 없이 /compare를 부르면 상세의 타입 변환 실패가 아니라 비교의 파라미터 누락으로 400이다
        mvc.get("/api/products/compare").andExpect {
            status { isBadRequest() }
        }
    }

    private fun product(
        name: String,
        reportNo: String = "R${seq++}",
        status: String = "ACTIVE",
        precautions: String? = null,
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, precautions)
        VALUES (?, ?, false, ?, 'PARSED', '1', ?) RETURNING id
        """,
        Long::class.java,
        reportNo, name, status, precautions,
    )!!

    private fun ingredient(productId: Long, ingredientName: String?, dailyAmount: String, rawName: String = ingredientName!!) {
        jdbc.update(
            """
            INSERT INTO product_ingredient (product_id, ingredient_id, raw_name, amount, unit, basis_amount, basis_unit,
                                            extraction_method, parser_version, daily_amount, daily_unit)
            VALUES (?, (SELECT id FROM ingredient WHERE name = ?), ?, ?, 'mg', 1, '일', 'RULE', '1', ?, 'mg')
            """,
            productId, ingredientName, rawName, BigDecimal(dailyAmount), BigDecimal(dailyAmount),
        )
    }

    private fun recall(
        reportNo: String?,
        productName: String,
        reason: String,
        listStatus: String = "LISTED",
        productType: String = "건강기능식품",
    ) {
        jdbc.update(
            """
            INSERT INTO recall (source_seq, report_no, product_name, product_type, reason, list_status, first_seen_at, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, now(), now())
            """,
            "S${seq++}", reportNo, productName, productType, reason, listStatus,
        )
    }
}
