package com.alkkuni.api.stats

import org.hamcrest.Matchers.nullValue
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

// 메인 통계 숫자가 검색 목록과 같은 조건으로 세지는지 실제 DB로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class StatsApiTest(
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
        jdbc.update("DELETE FROM ingestion_cursor")
    }

    @Test
    fun `제품 수는 검색 목록 전체 개수와 같고 기준일은 I0030 커서 날짜다`() {
        product("국내 제품")
        product("수출 제품(전량수출용)", exportOnly = true) // 목록 기본값(market=all)에 나와 센다
        product("원료 제품", rawMaterial = true) // 세지 않는다
        product("단종 제품", status = "DELETED") // 세지 않는다
        jdbc.update("INSERT INTO ingestion_cursor VALUES ('I0030', '2026-11-20', now())")
        jdbc.update("INSERT INTO ingestion_cursor VALUES ('I0490', '2026-11-01', now())") // 다른 출처는 보지 않는다
        val ingredientCount = jdbc.queryForObject("SELECT count(*) FROM ingredient", Long::class.java)!!

        mvc.get("/api/products") { param("q", "제품") }.andExpect {
            jsonPath("$.data.totalCount") { value(2) }
        }
        mvc.get("/api/stats").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=60, public") }
            jsonPath("$.data.productCount") { value(2) }
            jsonPath("$.data.ingredientCount") { value(ingredientCount) }
            jsonPath("$.data.dataDate") { value("2026-11-20") }
        }
    }

    @Test
    fun `첫 적재 전이라 커서가 없으면 기준일은 null이다`() {
        mvc.get("/api/stats").andExpect {
            status { isOk() }
            jsonPath("$.data.productCount") { value(0) }
            jsonPath("$.data.dataDate") { value(nullValue()) }
        }
    }

    private fun product(name: String, exportOnly: Boolean = false, rawMaterial: Boolean = false, status: String = "ACTIVE") {
        jdbc.update(
            """
            INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, export_only)
            VALUES (?, ?, ?, ?, 'PARSED', '1', ?)
            """,
            "R${seq++}", name, rawMaterial, status, exportOnly,
        )
    }
}
