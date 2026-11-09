package com.alkkuni.api.product

import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
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
import java.time.LocalDate

// JDSL이 만든 JPQL(EXISTS, ORDER BY 서브쿼리, NULLS LAST)이 Hibernate·PostgreSQL에서 의도대로 도는지가 핵심이라 실제 DB로 확인한다.
// 성분 사전(V7)과 주 단위(V10)는 마이그레이션 데이터를 그대로 쓴다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ProductExploreApiTest(
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
    fun `고른 성분이 모두 든 제품만 나오고 제외 성분이 든 제품은 빠진다`() {
        val both = product("A").also { has(it, "아연", "8"); has(it, "셀레늄", "0.05") }
        product("B").also { has(it, "아연", "10") } // 셀레늄 없음
        product("C").also { has(it, "아연", "9"); has(it, "셀레늄", "0.05"); has(it, "철", "12") } // 철 제외

        mvc.get("/api/products/explore") {
            param("ingredient", "${id("아연")},${id("셀레늄")}")
            param("exclude", "${id("철")}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.items[*].id") { value(contains(both.toInt())) }
            // 고른 성분 순서대로 함량 열이 붙는다.
            jsonPath("$.data.items[0].amounts[*].ingredientId") { value(contains(id("아연").toInt(), id("셀레늄").toInt())) }
            jsonPath("$.data.items[0].amounts[0].values[0].dailyAmount") { value(8) }
        }
    }

    @Test
    fun `원료 품목과 삭제된 제품은 빠지고 제형으로 거를 수 있다`() {
        val tablet = product("A", form = "정").also { has(it, "아연", "8") }
        val capsule = product("B", form = "캡슐").also { has(it, "아연", "8") }
        product("C", form = "분말").also { has(it, "아연", "8") }
        product("D", form = "정", rawMaterial = true).also { has(it, "아연", "8") }
        product("E", form = "정", status = "DELETED").also { has(it, "아연", "8") }

        mvc.get("/api/products/explore") { param("ingredient", "${id("아연")}"); param("form", "정,캡슐") }.andExpect {
            jsonPath("$.data.totalCount") { value(2) }
            jsonPath("$.data.items[*].id") { value(containsInAnyOrder(tablet.toInt(), capsule.toInt())) }
        }
        // 제형은 성분 개수 제한(5개)과 별개다. 6개를 골라도 된다.
        mvc.get("/api/products/explore") { param("ingredient", "${id("아연")}"); param("form", "정,캡슐,분말,액상,환,과립") }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(3) }
        }
    }

    @Test
    fun `함량순은 주 단위 값, 다른 단위 값, 모르는 값 순이다`() {
        // 엽산 주 단위는 mg DFE(V10). mg 행은 숫자가 커도 mg DFE 행 뒤에 따로 정렬한다.
        val dfeLow = product("A").also { has(it, "엽산", "0.4", "mg DFE") }
        val mgHigh = product("B").also { has(it, "엽산", "0.9", "mg") }
        val unknown = product("C").also { has(it, "엽산", null) }
        val dfeHigh = product("D").also { has(it, "엽산", "0.8", "mg DFE") }
        val mgLow = product("E").also { has(it, "엽산", "0.6", "mg") }

        mvc.get("/api/products/explore") { param("ingredient", "${id("엽산")}") }.andExpect {
            jsonPath("$.data.items[*].id") {
                value(contains(dfeHigh.toInt(), dfeLow.toInt(), mgHigh.toInt(), mgLow.toInt(), unknown.toInt()))
            }
            jsonPath("$.data.ingredients[0].defaultUnit") { value("mg DFE") }
            jsonPath("$.data.items[2].amounts[0].values[0].dailyUnit") { value("mg") }
        }
    }

    @Test
    fun `같은 성분 행이 여러 개면 정렬은 주 단위 최댓값으로 하고 응답에는 모두 원문 순서로 낸다`() {
        // 주 단위 최댓값은 0.3이다. mg 행 0.9가 커도 0.5 mg DFE 제품보다 뒤에 온다.
        val multi = product("A").also {
            has(it, "엽산", "0.2", "mg DFE")
            has(it, "엽산", "0.9", "mg")
            has(it, "엽산", "0.3", "mg DFE")
        }
        val single = product("B").also { has(it, "엽산", "0.5", "mg DFE") }

        mvc.get("/api/products/explore") { param("ingredient", "${id("엽산")}") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(single.toInt(), multi.toInt())) }
            jsonPath("$.data.items[1].amounts[0].values[*].dailyAmount") { value(contains(0.2, 0.9, 0.3)) }
        }
    }

    @Test
    fun `1일 함량을 모르는 행은 원문 값을 함께 낸다`() {
        product("A").also { has(it, "아연", null) }

        mvc.get("/api/products/explore") { param("ingredient", "${id("아연")}") }.andExpect {
            jsonPath("$.data.items[0].amounts[0].values[0].dailyAmount") { value(null as Any?) }
            jsonPath("$.data.items[0].amounts[0].values[0].amount") { value(1) }
            jsonPath("$.data.items[0].amounts[0].values[0].unit") { value("mg") }
        }
    }

    @Test
    fun `다른 단위 묶음 안에서는 단위별로 나눠 정렬한다`() {
        // 비타민 A 주 단위는 mg RAE. mg RE와 mg는 서로 비교하지 않고 단위 내림차순(mg RE → mg)으로 묶는다.
        val re = product("A").also { has(it, "비타민 A", "0.7", "mg RE") }
        val mg = product("B").also { has(it, "비타민 A", "1.0", "mg") }
        val rae = product("C").also { has(it, "비타민 A", "0.1", "mg RAE") }

        mvc.get("/api/products/explore") { param("ingredient", "${id("비타민 A")}") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(rae.toInt(), re.toInt(), mg.toInt())) }
        }
    }

    @Test
    fun `함량 범위는 기준 성분의 주 단위 값에만 걸린다`() {
        val inRange = product("A").also { has(it, "엽산", "0.5", "mg DFE"); has(it, "아연", "1") }
        product("B").also { has(it, "엽산", "0.9", "mg DFE"); has(it, "아연", "1") } // 범위 밖
        product("C").also { has(it, "엽산", "0.5", "mg"); has(it, "아연", "1") } // 다른 단위
        product("D").also { has(it, "엽산", null); has(it, "아연", "1") } // 모름

        // 기준 성분을 sort=amount:{엽산}으로 고른다. 첫 성분(아연)이 아니어도 된다.
        mvc.get("/api/products/explore") {
            param("ingredient", "${id("아연")},${id("엽산")}")
            param("sort", "amount:${id("엽산")}")
            param("min", "0.4")
            param("max", "0.6")
        }.andExpect {
            jsonPath("$.data.baseIngredientId") { value(id("엽산").toInt()) }
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.items[*].id") { value(contains(inRange.toInt())) }
        }
    }

    @Test
    fun `주 단위가 없는 성분은 단위를 가리지 않고 숫자로 정렬한다`() {
        val low = product("A").also { has(it, "아연", "3") }
        val high = product("B").also { has(it, "아연", "15") }

        mvc.get("/api/products/explore") { param("ingredient", "${id("아연")}") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(high.toInt(), low.toInt())) }
            jsonPath("$.data.ingredients[0].defaultUnit") { value(null as Any?) }
        }
    }

    @Test
    fun `기준 충족은 고른 성분 모두 1일 함량이 기준값 이상인 제품만 남긴다`() {
        // 기준값(V11): 아연 8.5mg, 엽산 0.4mg DFE. 엽산 mg 행은 환산 표기만 빠진 단위라 상한 판정과 같이 비교한다.
        val meets = product("A").also { has(it, "아연", "8.5"); has(it, "엽산", "0.4", "mg DFE") }
        val plainMg = product("B").also { has(it, "아연", "10"); has(it, "엽산", "0.5", "mg") }
        // 같은 성분 행이 여러 개면 하나라도 기준 이상이면 충족이다.
        val multi = product("C").also { has(it, "아연", "1"); has(it, "아연", "9"); has(it, "엽산", "0.4", "mg DFE") }
        product("D").also { has(it, "아연", "8"); has(it, "엽산", "0.4", "mg DFE") } // 아연 미달
        product("E").also { has(it, "아연", "9"); has(it, "엽산", null) } // 엽산 모름

        mvc.get("/api/products/explore") {
            param("ingredient", "${id("아연")},${id("엽산")}")
            param("meetsReference", "true")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(3) }
            jsonPath("$.data.items[*].id") { value(containsInAnyOrder(meets.toInt(), plainMg.toInt(), multi.toInt())) }
        }
        // 끄면 거르지 않는다.
        mvc.get("/api/products/explore") { param("ingredient", "${id("아연")},${id("엽산")}") }.andExpect {
            jsonPath("$.data.totalCount") { value(5) }
        }
    }

    @Test
    fun `기준값이 없는 성분을 고르고 기준 충족을 켜면 400이다`() {
        val noRef = jdbc.queryForObject("SELECT id FROM ingredient WHERE reference_amount IS NULL ORDER BY id LIMIT 1", Long::class.java)!!

        mvc.get("/api/products/explore") {
            param("ingredient", "${id("아연")},$noRef")
            param("meetsReference", "true")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.details[0].field") { value("meetsReference") }
        }
        mvc.get("/api/products/explore") { param("ingredient", "$noRef") }.andExpect { status { isOk() } }
    }

    @Test
    fun `최신 신고순과 페이지 나누기`() {
        val old = product("A", reportedOn = LocalDate.of(2020, 1, 1)).also { has(it, "아연", "1") }
        val none = product("B", reportedOn = null).also { has(it, "아연", "1") }
        val new = product("C", reportedOn = LocalDate.of(2026, 1, 1)).also { has(it, "아연", "1") }

        mvc.get("/api/products/explore") { param("ingredient", "${id("아연")}"); param("sort", "latest") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(new.toInt(), old.toInt(), none.toInt())) }
        }
        mvc.get("/api/products/explore") {
            param("ingredient", "${id("아연")}"); param("sort", "latest"); param("size", "2"); param("page", "1")
        }.andExpect {
            jsonPath("$.data.totalCount") { value(3) }
            jsonPath("$.data.items[*].id") { value(contains(none.toInt())) }
        }
    }

    @Test
    fun `잘못된 요청은 400과 필드를 알려준다`() {
        val zinc = id("아연")
        fun expectField(field: String, vararg params: Pair<String, String>) =
            mvc.get("/api/products/explore") { params.forEach { (k, v) -> param(k, v) } }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("INVALID_INPUT") }
                jsonPath("$.error.details[0].field") { value(field) }
                header { doesNotExist("Cache-Control") }
            }

        expectField("ingredient", "ingredient" to "1,2,3,4,5,6")
        expectField("ingredient", "ingredient" to "$zinc,$zinc")
        expectField("ingredient", "ingredient" to "$zinc,")
        expectField("ingredient", "ingredient" to "999999")
        expectField("exclude", "ingredient" to "$zinc", "exclude" to "$zinc")
        expectField("sort", "ingredient" to "$zinc", "sort" to "popular")
        expectField("sort", "ingredient" to "$zinc", "sort" to "amount:${id("철")}")
        expectField("min", "ingredient" to "$zinc", "min" to "5", "max" to "1")
        // PostgreSQL numeric을 넘는 값이 DB까지 가 500이 되지 않는다.
        mvc.get("/api/products/explore") { param("ingredient", "$zinc"); param("min", "1e999999999") }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `성분 목록 API는 사전 전체와 주 단위를 내려준다`() {
        mvc.get("/api/ingredients").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=60, public") }
            jsonPath("$.data[?(@.name == '엽산')].defaultUnit") { value(contains("mg DFE")) }
            jsonPath("$.data[?(@.name == '아연')].id") { value(contains(id("아연").toInt())) }
            // 기준 충족 필터를 켤 수 있는지 화면이 판단한다. 기준값이 없으면 null.
            jsonPath("$.data[?(@.name == '엽산')].referenceAmount") { value(contains(0.4)) }
            jsonPath("$.data[?(@.name == '엽산')].referenceUnit") { value(contains("mg DFE")) }
        }
    }

    private fun id(name: String): Long = jdbc.queryForObject("SELECT id FROM ingredient WHERE name = ?", Long::class.java, name)!!

    private fun product(
        name: String,
        form: String? = null,
        reportedOn: LocalDate? = null,
        rawMaterial: Boolean = false,
        status: String = "ACTIVE",
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, reported_on, form)
        VALUES (?, ?, ?, ?, 'PARSED', '1', ?, ?) RETURNING id
        """,
        Long::class.java,
        "R${seq++}", name, rawMaterial, status, reportedOn, form,
    )!!

    private fun has(productId: Long, ingredientName: String, dailyAmount: String?, unit: String = "mg") {
        jdbc.update(
            """
            INSERT INTO product_ingredient (product_id, ingredient_id, raw_name, amount, unit, basis_amount, basis_unit,
                                            extraction_method, parser_version, daily_amount, daily_unit)
            VALUES (?, (SELECT id FROM ingredient WHERE name = ?), ?, 1, 'mg', 1, '일', 'RULE', '1', ?, ?)
            """,
            productId, ingredientName, ingredientName, dailyAmount?.let(::BigDecimal), dailyAmount?.let { unit },
        )
    }
}
