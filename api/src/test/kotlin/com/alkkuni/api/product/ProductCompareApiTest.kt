package com.alkkuni.api.product

import com.alkkuni.core.product.ExportName
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

// 성분 사전(V7) 연결과 제품별 성분 행 묶기를 실제 DB로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ProductCompareApiTest(
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
    }

    @Test
    fun `성분을 행, 제품을 요청 순서의 열로 놓고 없는 성분은 빈 목록이다`() {
        val a = product("멀티 A").also { ingredient(it, "아연", "8.5"); ingredient(it, "비타민 C", "100") }
        val b = product("멀티 B").also { ingredient(it, "아연", "12") }

        mvc.get("/api/products/compare") { param("ids", "$b,$a") }.andExpect {
            status { isOk() }
            jsonPath("$.data.products[*].id") { value(contains(b.toInt(), a.toInt())) }
            // 두 제품에 모두 있는 성분이 먼저 온다
            jsonPath("$.data.ingredients[*].name") { value(contains("아연", "비타민 C")) }
            jsonPath("$.data.ingredients[0].values[0][0].dailyAmount") { value(12) }
            jsonPath("$.data.ingredients[0].values[1][0].dailyAmount") { value(8.5) }
            jsonPath("$.data.ingredients[1].values[0].length()") { value(0) }
            jsonPath("$.data.ingredients[1].values[1][0].dailyUnit") { value("mg") }
        }
    }

    @Test
    fun `제품에 브랜드를 붙이고 없으면 null이다`() {
        val a = product("ATOMY 아연").also { ingredient(it, "아연", "8"); linkBrand(it, "애터미") }
        val b = product("아연 B").also { ingredient(it, "아연", "8") }

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.products[0].brand.name") { value("애터미") }
            jsonPath("$.data.products[1].brand") { value(null as Any?) }
        }
    }

    @Test
    fun `제품에 기능성 카테고리를 붙이고 없으면 빈 목록이다`() {
        val a = product("루테인 A").also { ingredient(it, "아연", "8") }
        val b = product("아연 B").also { ingredient(it, "아연", "8") }
        jdbc.update("INSERT INTO product_category (product_id, category) VALUES (?, 'EYE')", a)

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.products[0].categories[0].code") { value("EYE") }
            jsonPath("$.data.products[1].categories") { isEmpty() }
        }
    }

    @Test
    fun `제품에 수출용 여부를 붙인다`() {
        val a = product("아연 A(전량수출용)").also { ingredient(it, "아연", "8") }
        val b = product("아연 B").also { ingredient(it, "아연", "8") }

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.products[*].exportOnly") { value(contains(true, false)) }
        }
    }

    @Test
    fun `함량 칸마다 기준값 판정을 붙인다`() {
        // 아연 기준 8.5mg, 상한 35mg. 상세·목록 API와 같은 Ingredient 판정을 쓴다
        val a = product("A").also { ingredient(it, "아연", "12") }
        val b = product("B").also { ingredient(it, "아연", "80") }

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.ingredients[0].referenceAmount") { value(8.5) }
            jsonPath("$.data.ingredients[0].referenceUnit") { value("mg") }
            jsonPath("$.data.ingredients[0].defaultUnit") { value(null as Any?) }
            jsonPath("$.data.ingredients[0].values[0][0].referencePercent") { value(141) }
            jsonPath("$.data.ingredients[0].values[0][0].meetsReference") { value(true) }
            jsonPath("$.data.ingredients[0].values[1][0].meetsReference") { value(null as Any?) }
            jsonPath("$.data.ingredients[0].values[1][0].exceedsUpperLimit") { value(true) }
        }
    }

    @Test
    fun `성분의 주 단위를 내보낸다`() {
        // 엽산은 mg DFE로 단위가 섞여 주 단위가 있다. 화면은 이 단위와 다른 값을 "다른 단위 표기"로 본다
        val a = product("A").also { ingredient(it, "엽산", "0.4") }
        val b = product("B").also { ingredient(it, "엽산", "0.2") }

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.ingredients[0].defaultUnit") { value("mg DFE") }
        }
    }

    @Test
    fun `같은 성분 행이 여러 개면 합치지 않고 모두 내보낸다`() {
        // 멀티팩(구성품별 합산)과 수출국별 규격(하나만)을 원문 없이 구분할 수 없어 판단을 화면에 맡긴다.
        val pack = product("멀티팩").also { ingredient(it, "아연", "5.95"); ingredient(it, "아연", "2.55") }
        val single = product("아연정").also { ingredient(it, "아연", "8.5") }

        mvc.get("/api/products/compare") { param("ids", "$pack,$single") }.andExpect {
            jsonPath("$.data.ingredients.length()") { value(1) }
            jsonPath("$.data.ingredients[0].values[0][*].dailyAmount") { value(contains(5.95, 2.55)) }
        }
    }

    @Test
    fun `사전에 없는 성분은 원문 이름이 같을 때만 같은 행이다`() {
        val a = product("A").also { ingredient(it, null, "3", rawName = "미확인성분X") }
        val b = product("B").also { ingredient(it, null, "4", rawName = "미확인성분X") }

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.ingredients.length()") { value(1) }
            jsonPath("$.data.ingredients[0].ingredientId") { value(null as Any?) }
            jsonPath("$.data.ingredients[0].name") { value("미확인성분X") }
        }
    }

    @Test
    fun `사전에 없는 성분은 띄어쓰기·대소문자만 달라도 같은 행이다`() {
        val a = product("A").also { ingredient(it, null, "3", rawName = "Unknown X") }
        val b = product("B").also { ingredient(it, null, "4", rawName = "unknownx") }

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.ingredients.length()") { value(1) }
        }
    }

    @Test
    fun `제품 수가 같은 성분끼리는 앞 열 제품의 성분이 먼저다`() {
        // b를 나중에 적재해 성분 행 id가 더 커도, 요청에서 b가 첫 열이면 b의 성분이 위에 온다.
        val a = product("A").also { ingredient(it, "아연", "8") }
        val b = product("B").also { ingredient(it, "철", "12") }

        mvc.get("/api/products/compare") { param("ids", "$b,$a") }.andExpect {
            jsonPath("$.data.ingredients[*].name") { value(contains("철", "아연")) }
        }
    }

    @Test
    fun `제품마다 하루치 확인 여부와 검수 상태를 내보낸다`() {
        val a = product("A", parseStatus = "NEEDS_REVIEW", basisCheck = "UNVERIFIED")
        val b = product("B", basisCheck = "DAILY")

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            jsonPath("$.data.products[0].parseStatus") { value("NEEDS_REVIEW") }
            jsonPath("$.data.products[0].basisCheck") { value("UNVERIFIED") }
            jsonPath("$.data.products[1].basisCheck") { value("DAILY") }
        }
    }

    @Test
    fun `제품은 서로 다른 2~3개여야 한다`() {
        val a = product("A")
        val b = product("B")
        // "$a,"은 빈 원소가 null로 바인딩된다
        listOf("$a", "$a,$a", "$a,$b,1,2", "$a,", "$a,,$b").forEach { ids ->
            mvc.get("/api/products/compare") { param("ids", ids) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.details[0].field") { value("ids") }
            }
        }
    }

    @Test
    fun `없거나 삭제된 제품이 있으면 404다`() {
        val a = product("A")
        val deleted = product("B", status = "DELETED")

        listOf("$a,999999", "$a,$deleted").forEach { ids ->
            mvc.get("/api/products/compare") { param("ids", ids) }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("PRODUCT_NOT_FOUND") }
            }
        }
    }

    @Test
    fun `성공 응답에 60초 공개 캐시 헤더가 붙는다`() {
        val a = product("A")
        val b = product("B")

        mvc.get("/api/products/compare") { param("ids", "$a,$b") }.andExpect {
            header { string("Cache-Control", "max-age=60, public") }
        }
    }

    private fun linkBrand(productId: Long, brandName: String) {
        jdbc.update("UPDATE product SET brand_id = (SELECT id FROM brand WHERE name = ?) WHERE id = ?", brandName, productId)
    }

    private fun product(
        name: String,
        status: String = "ACTIVE",
        parseStatus: String = "PARSED",
        basisCheck: String? = null,
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, basis_check, export_only)
        VALUES (?, ?, false, ?, ?, '1', ?, ?) RETURNING id
        """,
        Long::class.java,
        "R${seq++}", name, status, parseStatus, basisCheck, ExportName.isExportOnly(name),
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
}
