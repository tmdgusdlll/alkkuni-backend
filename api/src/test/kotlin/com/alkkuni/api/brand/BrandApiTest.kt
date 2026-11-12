package com.alkkuni.api.brand

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

// 브랜드 사전(V13)과 제품 수 집계를 실제 DB로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BrandApiTest(
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
    fun `공개 제품이 있는 브랜드만 제품 수 많은 순으로 낸다`() {
        product("광동", "광동 A")
        product("광동", "광동 B")
        product("애터미", "ATOMY A")
        product("애터미", "ATOMY 원료", rawMaterial = true) // 세지 않는다
        product("애터미", "ATOMY 단종", status = "DELETED") // 세지 않는다
        product("일양", "일양 원료", rawMaterial = true) // 공개 제품이 없어 목록에서 빠진다

        mvc.get("/api/brands").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=60, public") }
            jsonPath("$.data[*].name") { value(contains("광동", "애터미")) }
            jsonPath("$.data[*].productCount") { value(contains(2, 1)) }
        }
    }

    private fun product(brandName: String, name: String, rawMaterial: Boolean = false, status: String = "ACTIVE") {
        jdbc.update(
            """
            INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, brand_id)
            VALUES (?, ?, ?, ?, 'PARSED', '1', (SELECT id FROM brand WHERE name = ?))
            """,
            "R${seq++}", name, rawMaterial, status, brandName,
        )
    }
}
