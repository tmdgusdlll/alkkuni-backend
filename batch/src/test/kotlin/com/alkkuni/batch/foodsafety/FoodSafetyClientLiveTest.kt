package com.alkkuni.batch.foodsafety

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실제 식약처 API 호출 확인. CI와 평소 빌드에서는 건너뛴다.
 * 실행: set -a; source .env; set +a; FOODSAFETY_LIVE_TEST=true ./gradlew :batch:test --tests '*LiveTest'
 * CHNG_DT 없는 호출 1회가 시간당 한도(100회)를 쓴다.
 */
@EnabledIfEnvironmentVariable(named = "FOODSAFETY_LIVE_TEST", matches = "true")
class FoodSafetyClientLiveTest {
    private val client = FoodSafetyClient(
        RestClient.builder(),
        JsonMapper.builder().build(),
        System.getenv("FOODSAFETY_API_KEY").orEmpty(),
    )

    @Test
    fun `I0030을 한 번에 1000건 받는다`() {
        val started = System.nanoTime()
        val page = client.fetchPage("I0030", 1, FoodSafetyClient.PAGE_SIZE)
        println("I0030 total=${page.totalCount} rows=${page.rows.size} ${(System.nanoTime() - started) / 1_000_000}ms")

        assertEquals(FoodSafetyClient.PAGE_SIZE, page.rows.size)
        assertTrue(page.totalCount > 40_000)
    }

    @Test
    fun `CHNG_DT로 최근 변경분을 받는다`() {
        val page = client.fetchPage("I0030", 1, FoodSafetyClient.PAGE_SIZE, LocalDate.now().minusDays(7))
        println("I0030 CHNG_DT 최근 7일 total=${page.totalCount}")

        assertTrue(page.totalCount > 0)
    }

    @Test
    fun `I0490 회수 목록을 받는다`() {
        val page = client.fetchPage("I0490", 1, FoodSafetyClient.PAGE_SIZE)
        println("I0490 total=${page.totalCount} rows=${page.rows.size}")

        assertTrue(page.rows.isNotEmpty())
    }
}
