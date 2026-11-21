package com.alkkuni.batch.product

import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.noLock
import com.alkkuni.batch.raw.LocalRawStore
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.manyTimes
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProductionCollectJobTest {
    // 2026년에 돌면 작년은 2025, 재작년은 2024다.
    private val clock = Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
    private val client = FoodSafetyClient(builder, JsonMapper.builder().build(), "k")
    private val applier = mock(ProductionApplier::class.java).also {
        `when`(it.apply(anyInt(), anyMap())).thenReturn(ProductionApplier.Result(0, 0, 0))
    }
    private val job = ProductionCollectJob(client, LocalRawStore(Files.createTempDirectory("raw")), applier, noLock(), clock)

    @Test
    fun `작년 실적이 있으면 작년 것을 받아 반영한다`() {
        givenYear(2025, total = 2, rows = listOf("1" to "10", "2" to "0"))
        givenYear(2024, total = 2)

        job.run(DefaultApplicationArguments())

        assertEquals(2025 to mapOf("1" to 10L, "2" to 0L), applied())
    }

    @Test
    fun `작년 실적이 아직 없으면 재작년 것을 쓴다`() {
        givenYear(2025, total = 0)
        givenYear(2024, total = 1, rows = listOf("1" to "5"))

        job.run(DefaultApplicationArguments())

        assertEquals(2024 to mapOf("1" to 5L), applied())
    }

    @Test
    fun `작년 실적이 재작년의 90% 미만이면 덜 찬 것으로 보고 재작년 것을 쓴다`() {
        // 10건 중 8건: 절반은 넘지만 90%에 못 미친다.
        givenYear(2025, total = 8)
        givenYear(2024, total = 10, rows = (1..10).map { "$it" to "1" })

        job.run(DefaultApplicationArguments())

        assertEquals(2024, applied()!!.first)
    }

    @Test
    fun `받은 행이 total_count보다 적으면 반영하지 않고 실패한다`() {
        givenYear(2025, total = 3, rows = listOf("1" to "10", "2" to "0"))
        givenYear(2024, total = 3)

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertTrue(e.message!!.contains("받은 행 2건이 total_count 3건보다 적다"), e.message)
        assertEquals(null, applied())
    }

    @Test
    fun `연도를 고를 때는 있었는데 전체 조회가 비어 오면 반영하지 않고 실패한다`() {
        // 고를 때 1건 조회는 46,271건, 바로 뒤 전체 조회는 INFO-200. 그대로 반영하면 모든 제품 값이 지워진다.
        givenCount(2025, 46_271)
        givenCount(2024, 41_896)
        givenFull(2025, total = 0)

        assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }
        assertEquals(null, applied())
    }

    @Test
    fun `같은 신고번호가 두 번 와도 큰 값으로 합쳐 반영한다`() {
        givenYear(2025, total = 3, rows = listOf("1" to "10", "1" to "30", "2" to "0"))
        givenYear(2024, total = 3)

        job.run(DefaultApplicationArguments())

        assertEquals(2025 to mapOf("1" to 30L, "2" to 0L), applied())
    }

    @Test
    fun `다 받은 뒤 다시 센 total_count가 바뀌었으면 반영하지 않고 실패한다`() {
        // 고를 때는 2건, 다 받은 뒤 다시 세니 3건(수집 도중 데이터가 바뀜).
        givenCount(2025, 2, 3)
        givenCount(2024, 2)
        givenFull(2025, total = 2, rows = listOf("1" to "10", "2" to "0"))

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertTrue(e.message!!.contains("다시 세니"), e.message)
        assertEquals(null, applied())
    }

    @Test
    fun `두 해 모두 실적이 없으면 실패한다`() {
        givenYear(2025, total = 0)
        givenYear(2024, total = 0)

        assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }
        assertEquals(null, applied())
    }

    /** 건수 확인(1건 조회)과 전체 조회 응답을 같은 건수로 둔다. */
    private fun givenYear(year: Int, total: Int, rows: List<Pair<String, String>> = emptyList()) {
        givenCount(year, total)
        givenFull(year, total, rows)
    }

    /** 건수 확인(1건 조회) 응답. 여러 값을 주면 부를 때마다 차례로 돌려주고, 마지막 값은 계속 돌려준다. */
    private fun givenCount(year: Int, vararg totals: Int) {
        val url = "${FoodSafetyClient.BASE_URL}/k/I0310/json/1/1/EVL_YR=$year"
        totals.dropLast(1).forEach { server.expect(once(), requestTo(url)).andRespond(respond(year, it, emptyList())) }
        server.expect(manyTimes(), requestTo(url)).andRespond(respond(year, totals.last(), emptyList()))
    }

    private fun givenFull(year: Int, total: Int, rows: List<Pair<String, String>> = emptyList()) {
        server.expect(manyTimes(), requestTo("${FoodSafetyClient.BASE_URL}/k/I0310/json/1/1000/EVL_YR=$year"))
            .andRespond(respond(year, total, rows))
    }

    private fun respond(year: Int, total: Int, rows: List<Pair<String, String>>): ResponseCreator {
        val json = rows.joinToString(",") { (no, qty) -> """{"PRDLST_REPORT_NO":"$no","EVL_YR":"$year","PRDCTN_QY":"$qty"}""" }
        val code = if (total == 0) "INFO-200" else "INFO-000"
        return withSuccess(
            """{"I0310":{"total_count":"$total","row":[$json],"RESULT":{"CODE":"$code","MSG":""}}}""",
            MediaType.APPLICATION_JSON,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun applied(): Pair<Int, Map<String, Long>>? = mockingDetails(applier).invocations
        .singleOrNull { it.method.name == "apply" }
        ?.let { it.arguments[0] as Int to it.arguments[1] as Map<String, Long> }

    // Kotlin 비null 파라미터에 Mockito 매처(null 반환)를 넘기면 NPE가 나서 기본값을 돌려준다.
    private fun anyInt(): Int = org.mockito.ArgumentMatchers.anyInt()
    private fun anyMap(): Map<String, Long> = org.mockito.ArgumentMatchers.anyMap<String, Long>() ?: emptyMap()
}
