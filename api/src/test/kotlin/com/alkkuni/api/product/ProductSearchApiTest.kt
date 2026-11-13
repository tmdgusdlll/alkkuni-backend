package com.alkkuni.api.product

import com.alkkuni.core.product.ExportName
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

// pg_trgm 유사도와 성분 사전(V7)이 실제 PostgreSQL에서 어떻게 동작하는지가 핵심이라 실제 DB로 검색 API를 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ProductSearchApiTest(
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
    fun `제품명·제조사 일치가 성분만 일치한 제품보다 앞에 온다`() {
        val byIngredient = product("멀티비타민 골드", reportedOn = LocalDate.of(2026, 1, 1))
        ingredient(byIngredient, "아연", "8.5")
        val byName = product("고함량아연 플러스", reportedOn = LocalDate.of(2020, 1, 1))
        val byManufacturer = product("데일리 케어", manufacturer = "아연제약", reportedOn = LocalDate.of(2019, 1, 1))
        product("루테인") // 걸리지 않는다

        mvc.get("/api/products") { param("q", "아연") }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(3) }
            jsonPath("$.data.matchedIngredient.name") { value("아연") }
            jsonPath("$.data.items[*].id") { value(contains(byName.toInt(), byManufacturer.toInt(), byIngredient.toInt())) }
            jsonPath("$.data.items[2].dailyAmount") { value(8.5) }
            jsonPath("$.data.items[2].dailyUnit") { value("mg") }
        }
    }

    @Test
    fun `원료 품목과 삭제된 제품은 빠진다`() {
        val active = product("아연 플러스")
        product("아연 원료", rawMaterial = true)
        product("아연 단종", status = "DELETED")

        mvc.get("/api/products") { param("q", "아연") }.andExpect {
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.items[*].id") { value(contains(active.toInt())) }
        }
    }

    @Test
    fun `오타가 조금 있어도 제품명 유사도로 찾는다`() {
        val vitaminC = product("고려은단 비타민C 1000")

        mvc.get("/api/products") { param("q", "비타민씨") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(vitaminC.toInt())) }
            jsonPath("$.data.matchedIngredient") { value(null as Any?) }
        }
    }

    @Test
    fun `LIKE 와일드카드는 글자 그대로 찾는다`() {
        product("비타민 100% 플러스")
        product("비타민 1000")

        mvc.get("/api/products") { param("q", "0%") }.andExpect {
            jsonPath("$.data.totalCount") { value(1) }
        }
    }

    @Test
    fun `1자 검색어는 성분명과 정확히 일치할 때만 받고 성분으로만 찾는다`() {
        val iron = product("멀티미네랄")
        ingredient(iron, "철", "12")
        product("철분왕") // 성분 행이 없으면 제품명에 "철"이 있어도 걸리지 않는다

        mvc.get("/api/products") { param("q", "철") }.andExpect {
            status { isOk() }
            jsonPath("$.data.items[*].id") { value(contains(iron.toInt())) }
        }
        mvc.get("/api/products") { param("q", "가") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_INPUT") }
            jsonPath("$.error.details[0].field") { value("q") }
        }
    }

    @Test
    fun `함량순은 1일 함량 높은순이고 모르는 값은 뒤에 둔다`() {
        val low = product("아연 A").also { ingredient(it, "아연", "3") }
        val unknown = product("아연 B").also { ingredient(it, "아연", null) }
        val high = product("아연 C").also { ingredient(it, "아연", "15") }

        mvc.get("/api/products") { param("q", "아연"); param("sort", "amount") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(high.toInt(), low.toInt(), unknown.toInt())) }
        }
    }

    @Test
    fun `함량마다 하루치 확인 여부와 검수 상태를 함께 내보낸다`() {
        // 검수 대기인 값도 화면이 검증된 값과 구분할 수 있어야 한다.
        product("아연 A", parseStatus = "NEEDS_REVIEW", basisCheck = "UNVERIFIED").also { ingredient(it, "아연", "20") }
        product("아연 B", basisCheck = "DAILY").also { ingredient(it, "아연", "8.5") }

        mvc.get("/api/products") { param("q", "아연"); param("sort", "amount") }.andExpect {
            jsonPath("$.data.items[0].parseStatus") { value("NEEDS_REVIEW") }
            jsonPath("$.data.items[0].basisCheck") { value("UNVERIFIED") }
            jsonPath("$.data.items[1].parseStatus") { value("PARSED") }
            jsonPath("$.data.items[1].basisCheck") { value("DAILY") }
        }
    }

    @Test
    fun `성분 검색이면 상한 2배 초과 여부를 내보내고, 함량순에서는 그 값을 뒤로 보낸다`() {
        // 아연(V11): 상한 35mg, 2배 70mg 초과면 오류 의심. 70은 경계라 정상 값이다.
        // 제품 parseStatus는 PARSED여도 화면이 "검수 대기"로 구분해야 하고, 함량순 1위를 오류 값이 차지하면 안 된다.
        val over = product("아연 A").also { ingredient(it, "아연", "90") }
        val atMargin = product("아연 B").also { ingredient(it, "아연", "70") }
        val low = product("아연 C").also { ingredient(it, "아연", "10") }

        mvc.get("/api/products") { param("q", "아연"); param("sort", "amount") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(atMargin.toInt(), low.toInt(), over.toInt())) }
            jsonPath("$.data.items[*].exceedsUpperLimit") { value(contains(false, false, true)) }
            // 정렬만 뒤로 가고 값은 그대로 낸다
            jsonPath("$.data.items[2].dailyAmount") { value(90) }
        }
        // 성분 검색이 아니면 판정할 함량이 없어 false
        mvc.get("/api/products") { param("q", "아연 A") }.andExpect {
            jsonPath("$.data.matchedIngredient") { value(null as Any?) }
            jsonPath("$.data.items[0].exceedsUpperLimit") { value(false) }
        }
    }

    @Test
    fun `주 단위가 있는 성분은 검색에서도 성분별 목록과 같은 값·순서를 쓴다`() {
        // 엽산 주 단위는 mg DFE(V10). mg 0.6이 숫자로는 커도 0.4 mg DFE를 고르고, mg 값만 있는 제품은 뒤에 둔다.
        val mixed = product("엽산 A").also { ingredient(it, "엽산", "0.4", "mg DFE"); ingredient(it, "엽산", "0.6") }
        val mgOnly = product("엽산 B").also { ingredient(it, "엽산", "0.9") }
        val dfe = product("엽산 C").also { ingredient(it, "엽산", "0.5", "mg DFE") }

        mvc.get("/api/products") { param("q", "엽산"); param("sort", "amount") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(dfe.toInt(), mixed.toInt(), mgOnly.toInt())) }
            jsonPath("$.data.items[1].dailyAmount") { value(0.4) }
            jsonPath("$.data.items[1].dailyUnit") { value("mg DFE") }
        }
    }

    @Test
    fun `성분이 아닌 검색어로 함량순을 요청하면 400이다`() {
        mvc.get("/api/products") { param("q", "고려은단"); param("sort", "amount") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.details[0].field") { value("sort") }
        }
        mvc.get("/api/products") { param("q", "고려은단"); param("sort", "popular") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.details[0].field") { value("sort") }
        }
    }

    @Test
    fun `최신 신고순은 신고일 내림차순이고 신고일 없는 제품은 뒤에 둔다`() {
        val old = product("아연 A", reportedOn = LocalDate.of(2020, 1, 1))
        val none = product("아연 B", reportedOn = null)
        val new = product("아연 C", reportedOn = LocalDate.of(2026, 1, 1))

        mvc.get("/api/products") { param("q", "아연"); param("sort", "latest") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(new.toInt(), old.toInt(), none.toInt())) }
        }
    }

    @Test
    fun `페이지를 나눠도 전체 건수는 그대로다`() {
        repeat(3) { product("아연 $it") }

        mvc.get("/api/products") { param("q", "아연"); param("size", "2"); param("page", "1") }.andExpect {
            jsonPath("$.data.totalCount") { value(3) }
            jsonPath("$.data.items.length()") { value(1) }
        }
    }

    @Test
    fun `브랜드 표기로 검색하면 제품명에 브랜드 이름이 없는 그 브랜드 제품도 찾는다`() {
        // V13: 애터미의 표기에 "ATOMY"가 있다. 제품명에 "애터미"가 없어 이름 검색으로는 안 걸린다.
        val atomy = product("ATOMY 비타민C").also { linkBrand(it, "애터미") }
        product("비타민C 1000") // 브랜드 없음

        mvc.get("/api/products") { param("q", "애터미") }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.matchedBrand.name") { value("애터미") }
            jsonPath("$.data.items[*].id") { value(contains(atomy.toInt())) }
            jsonPath("$.data.items[0].brand.name") { value("애터미") }
        }
        // 브랜드 표기가 아니면 matchedBrand는 null, 브랜드 없는 제품의 brand도 null
        mvc.get("/api/products") { param("q", "비타민C") }.andExpect {
            jsonPath("$.data.matchedBrand") { value(null as Any?) }
            jsonPath("$.data.items[?(@.name == '비타민C 1000')].brand") { value(contains(null as Any?)) }
        }
    }

    @Test
    fun `브랜드로 찾은 제품은 제품명 유사도와 상관없이 이름만 일치한 다른 제품보다 앞에 온다`() {
        // 이름에 "애터미"가 있지만 브랜드가 아닌 제품(유사도 높음)과, 이름에 없는 애터미 제품(유사도 0에 가까움)
        val other = product("애터미 공동구매 비타민")
        val atomy = product("ATOMY 비타민C").also { linkBrand(it, "애터미") }

        mvc.get("/api/products") { param("q", "애터미") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(atomy.toInt(), other.toInt())) }
        }
    }

    @Test
    fun `검색어가 브랜드면 그 브랜드 제품끼리는 제품명 유사도 대신 최신순이다`() {
        // 실제 "애터미" 검색: 걸리는 제품이 모두 애터미 제품이라, 유사도로 정렬하면 이름에 "애터미"가 없는 ATOMY 제품이 전부 뒤로 간다.
        val oldNamed = product("애터미 헤모힘", reportedOn = LocalDate.of(2018, 1, 1)).also { linkBrand(it, "애터미") }
        val newEnglish = product("ATOMY 비타민C", reportedOn = LocalDate.of(2024, 1, 1)).also { linkBrand(it, "애터미") }

        mvc.get("/api/products") { param("q", "애터미") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(newEnglish.toInt(), oldNamed.toInt())) }
        }
    }

    @Test
    fun `수출용 제품은 어느 정렬이든 국내 제품 뒤에 온다`() {
        // 최신이고 이름도 더 비슷한 수출용 제품들. 표기는 "수출용"·"전량수출용"·괄호 안 국가명.
        val exportWord = product("애터미 석류미인(전량수출용)", reportedOn = LocalDate.of(2026, 7, 1)).also { linkBrand(it, "애터미") }
        val exportCountry = product("애터미 석류미인 (영국, 독일)", reportedOn = LocalDate.of(2026, 7, 2)).also { linkBrand(it, "애터미") }
        val domestic = product("애터미 헤모힘", reportedOn = LocalDate.of(2018, 1, 1)).also { linkBrand(it, "애터미") }

        for (sort in listOf("relevance", "latest")) {
            mvc.get("/api/products") { param("q", "애터미"); param("sort", sort) }.andExpect {
                jsonPath("$.data.items[0].id") { value(domestic.toInt()) }
                jsonPath("$.data.items[*].id") { value(containsInAnyOrder(domestic.toInt(), exportWord.toInt(), exportCountry.toInt())) }
            }
        }
    }

    @Test
    fun `수출겸용·수출명·원산지 표기는 수출용으로 보지 않는다`() {
        val dualUse = product("다이어트 유산균 (수출겸용)", reportedOn = LocalDate.of(2020, 1, 1))
        val exportName = product("혼합 유산균(수출명:Rofit)", reportedOn = LocalDate.of(2020, 1, 2))
        val origin = product("유산균 크릴오일(호주산)", reportedOn = LocalDate.of(2020, 1, 3))
        val export = product("유산균 골드(수출용)", reportedOn = LocalDate.of(2026, 1, 1))

        mvc.get("/api/products") { param("q", "유산균"); param("sort", "latest") }.andExpect {
            jsonPath("$.data.items[*].id") { value(contains(origin.toInt(), exportName.toInt(), dualUse.toInt(), export.toInt())) }
        }
    }

    @Test
    fun `market으로 국내용만·수출용만 고르고 응답에 수출용 여부를 붙인다`() {
        val export = product("유산균 골드(수출용)", reportedOn = LocalDate.of(2026, 1, 1))
        val domestic = product("유산균 베이직", reportedOn = LocalDate.of(2020, 1, 1))

        mvc.get("/api/products") { param("q", "유산균") }.andExpect {
            jsonPath("$.data.totalCount") { value(2) }
            jsonPath("$.data.items[*].exportOnly") { value(contains(false, true)) }
        }
        mvc.get("/api/products") { param("q", "유산균"); param("market", "domestic") }.andExpect {
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.items[*].id") { value(contains(domestic.toInt())) }
        }
        mvc.get("/api/products") { param("q", "유산균"); param("market", "export") }.andExpect {
            jsonPath("$.data.totalCount") { value(1) }
            jsonPath("$.data.items[*].id") { value(contains(export.toInt())) }
        }
        mvc.get("/api/products") { param("q", "유산균"); param("market", "korea") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.details[0].field") { value("market") }
        }
    }

    @Test
    fun `성공 응답만 60초 공개 캐시 헤더가 붙는다`() {
        mvc.get("/api/products") { param("q", "아연") }.andExpect {
            header { string("Cache-Control", "max-age=60, public") }
        }
        mvc.get("/api/products") { param("q", "가") }.andExpect {
            status { isBadRequest() }
            header { doesNotExist("Cache-Control") }
        }
    }

    private fun linkBrand(productId: Long, brandName: String) {
        jdbc.update("UPDATE product SET brand_id = (SELECT id FROM brand WHERE name = ?) WHERE id = ?", brandName, productId)
    }

    private fun product(
        name: String,
        manufacturer: String? = null,
        reportedOn: LocalDate? = null,
        rawMaterial: Boolean = false,
        status: String = "ACTIVE",
        parseStatus: String = "PARSED",
        basisCheck: String? = null,
    ): Long = jdbc.queryForObject(
        """
        INSERT INTO product (report_no, name, manufacturer, raw_material, status, parse_status, parser_version, reported_on, basis_check, export_only)
        VALUES (?, ?, ?, ?, ?, ?, '1', ?, ?, ?) RETURNING id
        """,
        Long::class.java,
        "R${seq++}", name, manufacturer, rawMaterial, status, parseStatus, reportedOn, basisCheck, ExportName.isExportOnly(name),
    )!!

    private fun ingredient(productId: Long, ingredientName: String, dailyAmount: String?, unit: String = "mg") {
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
