package com.alkkuni.batch.product

import com.alkkuni.batch.brand.BrandDictionary
import com.alkkuni.batch.ingredient.IngredientDictionary
import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.noLock
import com.alkkuni.batch.raw.RawStore
import com.alkkuni.core.ingestion.entity.IngestionCursor
import com.alkkuni.core.ingestion.repository.IngestionCursorRepository
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IncrementalCollectJobTest {
    // 한국 2026-11-03 01:00 = UTC 2026-11-02 16:00. UTC 기준으로 날짜를 잡으면 하루 전이 된다.
    private val clock = Clock.fixed(Instant.parse("2026-11-02T16:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val today = LocalDate.of(2026, 11, 3)

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = FoodSafetyClient(builder, JsonMapper.builder().build(), "k")
    private val received = mutableListOf<String>()

    // DB 없이 받은 신고번호만 기록한다
    private val upserter = object : ProductUpserter(
        mock(ProductRepository::class.java),
        mock(ProductIngredientRepository::class.java),
        mock(IngredientDictionary::class.java),
        mock(BrandDictionary::class.java),
    ) {
        override fun upsert(rows: List<I0030Row>): Int = rows.size.also { rows.mapTo(received) { it.reportNo } }
    }
    private val cursorRepository = mock(IngestionCursorRepository::class.java)
    private val job = IncrementalCollectJob(
        client, RawStore(Files.createTempDirectory("raw")), upserter, cursorRepository, noLock(), clock,
    )

    private fun givenCursor(date: LocalDate?): IngestionCursor? {
        val cursor = date?.let { IngestionCursor("I0030", it, LocalDateTime.now()) }
        `when`(cursorRepository.findById("I0030")).thenReturn(Optional.ofNullable(cursor))
        return cursor
    }

    private fun cursorSaves() = mockingDetails(cursorRepository).invocations.count { it.method.name == "save" }

    @Test
    fun `커서 하루 전부터 한국 날짜 기준으로 받고 커서를 오늘로 옮긴다`() {
        // 커서가 오늘이어도 조회일은 어제다(오늘 날짜는 19시 전 INFO-700)
        val cursor = givenCursor(today)!!
        expectPage(since = "20261102", start = 1, end = 1000, total = 1, reportNos = listOf("P1"))

        job.run(DefaultApplicationArguments())

        server.verify()
        assertEquals(listOf("P1"), received)
        assertEquals(today, cursor.lastCollectedDate)
        assertEquals(1, cursorSaves())
    }

    @Test
    fun `받은 신고번호 수가 total_count보다 적으면 커서를 옮기지 않는다`() {
        // 1페이지를 받은 뒤 앞쪽 행이 수정돼 밀리면서 1001번째 행이 1페이지 범위로 당겨졌다. 2페이지에서 다시 받지 못한다.
        val cursor = givenCursor(today.minusDays(1))!!
        expectPage(since = "20261101", start = 1, end = 1000, total = 1001, reportNos = (1..1000).map { "P$it" })
        expectPage(since = "20261101", start = 1001, end = 2000, total = 1001, reportNos = listOf("P1000"))

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertTrue(e.message!!.contains("받은 신고번호 1000건이 total_count 1001건보다 적다"), e.message)
        assertEquals(today.minusDays(1), cursor.lastCollectedDate)
        assertEquals(0, cursorSaves())
    }

    @Test
    fun `받은 신고번호 수가 total_count보다 많으면 경고만 하고 커서를 옮긴다`() {
        val cursor = givenCursor(today.minusDays(1))!!
        expectPage(since = "20261101", start = 1, end = 1000, total = 1, reportNos = listOf("P1", "P2"))

        job.run(DefaultApplicationArguments())

        assertEquals(listOf("P1", "P2"), received)
        assertEquals(today, cursor.lastCollectedDate)
        assertEquals(1, cursorSaves())
    }

    @Test
    fun `도중에 total_count가 바뀌면 실패한다`() {
        givenCursor(today)
        expectPage(since = "20261102", start = 1, end = 1000, total = 1001, reportNos = (1..1000).map { "P$it" })
        expectPage(since = "20261102", start = 1001, end = 2000, total = 1000, reportNos = emptyList())

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertTrue(e.message!!.contains("total_count가 1001건에서 1000건으로"), e.message)
        assertEquals(0, cursorSaves())
    }

    @Test
    fun `커서가 오늘보다 뒤면 API를 부르지 않고 실패한다`() {
        givenCursor(today.plusDays(1))

        assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        server.verify() // 기대한 호출이 없으므로 아무것도 부르지 않았어야 통과한다
        assertEquals(0, cursorSaves())
    }

    @Test
    fun `커서가 없으면 예외로 끝난다`() {
        // 조용히 끝나면 cron이 성공으로 본다. 0이 아닌 종료 코드로 실패를 알려야 한다.
        givenCursor(null)

        assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }
    }

    @Test
    fun `커서는 뒤로 갈 수 없다`() {
        val cursor = IngestionCursor("I0030", today, LocalDateTime.now())
        assertFailsWith<IllegalArgumentException> { cursor.advance(today.minusDays(1), LocalDateTime.now()) }
    }

    private fun expectPage(since: String, start: Int, end: Int, total: Int, reportNos: List<String>) {
        val rows = reportNos.joinToString(",") { """{"PRDLST_REPORT_NO":"$it","PRDLST_NM":"제품"}""" }
        server.expect(requestTo("${FoodSafetyClient.BASE_URL}/k/I0030/json/$start/$end/CHNG_DT=$since")).andRespond(
            withSuccess(
                """{"I0030":{"total_count":"$total","row":[$rows],"RESULT":{"CODE":"INFO-000","MSG":"정상"}}}""",
                MediaType.APPLICATION_JSON,
            ),
        )
    }
}
