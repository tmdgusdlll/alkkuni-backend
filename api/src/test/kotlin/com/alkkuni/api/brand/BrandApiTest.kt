package com.alkkuni.api.brand

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
    fun `공개 제품이 있는 브랜드를 국내 제품 수 많은 순으로 내고 국내·수출을 나눠 센다`() {
        product("광동", "광동 A")
        product("광동", "광동 B")
        product("애터미", "ATOMY A")
        product("애터미", "ATOMY B(전량수출용)")
        product("애터미", "ATOMY C(태국)")
        product("애터미", "ATOMY 원료", rawMaterial = true) // 세지 않는다
        product("애터미", "ATOMY 단종", status = "DELETED") // 세지 않는다
        product("일동", "일동 D(수출용)") // 수출용만 있어도 목록에 나오고 맨 뒤다
        product("일양", "일양 원료", rawMaterial = true) // 공개 제품이 없어 목록에서 빠진다

        mvc.get("/api/brands").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=60, public") }
            // 전체 수로는 애터미(3)가 광동(2)보다 많지만 국내 제품 수로 정렬한다
            jsonPath("$.data[*].name") { value(contains("광동", "애터미", "일동")) }
            jsonPath("$.data[*].domesticCount") { value(contains(2, 1, 0)) }
            jsonPath("$.data[*].exportCount") { value(contains(0, 2, 1)) }
        }
    }

    private fun product(brandName: String, name: String, rawMaterial: Boolean = false, status: String = "ACTIVE") {
        jdbc.update(
            """
            INSERT INTO product (report_no, name, raw_material, status, parse_status, parser_version, brand_id, export_only)
            VALUES (?, ?, ?, ?, 'PARSED', '1', (SELECT id FROM brand WHERE name = ?), ?)
            """,
            "R${seq++}", name, rawMaterial, status, brandName, ExportName.isExportOnly(name),
        )
    }
}
