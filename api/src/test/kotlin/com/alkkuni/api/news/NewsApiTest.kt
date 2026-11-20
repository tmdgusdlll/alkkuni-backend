package com.alkkuni.api.news

import org.hamcrest.Matchers.contains
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

// 소식 목록이 건강기능식품 회수만, 등록일 최신순(없으면 뒤)으로 나오는지 실제 DB로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class NewsApiTest(
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
        jdbc.update("DELETE FROM recall")
    }

    @Test
    fun `건강기능식품 회수만 등록일 최신순으로 나오고 등록일이 없으면 뒤로 간다`() {
        recall("오래된 회수", "2026-10-01 10:00:00")
        recall("등록일 없는 회수", null, listStatus = "DELISTED") // 목록에서 빠져도 남는다
        recall("최신 회수", "2026-11-03 17:34:32.325706", grade = "1등급")
        recall("가공식품 회수", "2026-11-04 09:00:00", productType = "가공식품") // 나오지 않는다

        mvc.get("/api/news").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=60, public") }
            jsonPath("$.data.totalCount") { value(3) }
            jsonPath("$.data.items[*].title") { value(contains("최신 회수", "오래된 회수", "등록일 없는 회수")) }
            jsonPath("$.data.items[0].type") { value("RECALL") }
            jsonPath("$.data.items[0].manufacturer") { value("제조사") }
            jsonPath("$.data.items[0].reason") { value("사유") }
            jsonPath("$.data.items[0].grade") { value("1등급") }
            jsonPath("$.data.items[0].date") { value("2026-11-03") }
            jsonPath("$.data.items[1].grade") { value(nullValue()) }
            jsonPath("$.data.items[2].date") { value(nullValue()) }
        }
    }

    @Test
    fun `페이지로 넘기고 범위를 벗어난 크기는 거절한다`() {
        recall("첫째", "2026-11-03 00:00:00")
        recall("둘째", "2026-11-02 00:00:00")
        recall("셋째", "2026-11-01 00:00:00")

        mvc.get("/api/news") { param("page", "1"); param("size", "2") }.andExpect {
            jsonPath("$.data.totalCount") { value(3) }
            jsonPath("$.data.items[*].title") { value(contains("셋째")) }
        }
        mvc.get("/api/news") { param("size", "51") }.andExpect { status { isBadRequest() } }
    }

    private fun recall(
        productName: String,
        registeredAt: String?,
        grade: String? = null,
        listStatus: String = "LISTED",
        productType: String = "건강기능식품",
    ) {
        jdbc.update(
            """
            INSERT INTO recall (source_seq, product_name, manufacturer, product_type, reason, list_status,
                                first_seen_at, last_seen_at, registered_at, grade)
            VALUES (?, ?, '제조사', ?, '사유', ?, now(), now(), ?::timestamp, ?)
            """,
            "S${seq++}", productName, productType, listStatus, registeredAt, grade,
        )
    }
}
