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
        // 상한을 크게 넘어 검수 대기인 값이 함량순 1위여도, 화면이 검증된 값과 구분할 수 있어야 한다.
        product("아연 A", parseStatus = "NEEDS_REVIEW", basisCheck = "UNVERIFIED").also { ingredient(it, "아연", "90") }
        product("아연 B", basisCheck = "DAILY").also { ingredient(it, "아연", "8.5") }

        mvc.get("/api/products") { param("q", "아연"); param("sort", "amount") }.andExpect {
            jsonPath("$.data.items[0].parseStatus") { value("NEEDS_REVIEW") }
            jsonPath("$.data.items[0].basisCheck") { value("UNVERIFIED") }
            jsonPath("$.data.items[1].parseStatus") { value("PARSED") }
            jsonPath("$.data.items[1].basisCheck") { value("DAILY") }
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
    fun `성공 응답만 60초 공개 캐시 헤더가 붙는다`() {
        mvc.get("/api/products") { param("q", "아연") }.andExpect {
            header { string("Cache-Control", "max-age=60, public") }
        }
        mvc.get("/api/products") { param("q", "가") }.andExpect {
            status { isBadRequest() }
            header { doesNotExist("Cache-Control") }
        }
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
        INSERT INTO product (report_no, name, manufacturer, raw_material, status, parse_status, parser_version, reported_on, basis_check)
        VALUES (?, ?, ?, ?, ?, ?, '1', ?, ?) RETURNING id
        """,
        Long::class.java,
        "R${seq++}", name, manufacturer, rawMaterial, status, parseStatus, reportedOn, basisCheck,
    )!!

    private fun ingredient(productId: Long, ingredientName: String, dailyAmount: String?) {
        jdbc.update(
            """
            INSERT INTO product_ingredient (product_id, ingredient_id, raw_name, amount, unit, basis_amount, basis_unit,
                                            extraction_method, parser_version, daily_amount, daily_unit)
            VALUES (?, (SELECT id FROM ingredient WHERE name = ?), ?, 1, 'mg', 1, '일', 'RULE', '1', ?, ?)
            """,
            productId, ingredientName, ingredientName, dailyAmount?.let(::BigDecimal), dailyAmount?.let { "mg" },
        )
    }
}
