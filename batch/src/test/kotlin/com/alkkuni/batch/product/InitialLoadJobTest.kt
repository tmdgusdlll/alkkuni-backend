package com.alkkuni.batch.product

import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.noLock
import com.alkkuni.batch.raw.RawStore
import com.alkkuni.core.ingestion.repository.IngestionCursorRepository
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InitialLoadJobTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = FoodSafetyClient(builder, JsonMapper.builder().build(), "k")
    private val received = mutableListOf<String>()

    // DB 없이 받은 신고번호만 기록한다
    private val upserter = object : ProductUpserter(
        mock(ProductRepository::class.java),
        mock(ProductIngredientRepository::class.java),
    ) {
        override fun upsert(rows: List<I0030Row>): Int = rows.size.also { rows.mapTo(received) { it.reportNo } }
    }
    private val cursorRepository = mock(IngestionCursorRepository::class.java)
    private val job = InitialLoadJob(
        client, RawStore(Files.createTempDirectory("raw")), upserter, cursorRepository, noLock(), Clock.systemDefaultZone(),
    )

    // 커서 저장(save) 호출 수. 실패한 적재가 커서를 남기면 증분이 빠진 구간을 건너뛴다.
    private fun cursorSaves() = mockingDetails(cursorRepository).invocations.count { it.method.name == "save" }

    @Test
    fun `도중에 total_count가 바뀌면 받은 개수가 맞아도 실패한다`() {
        // 1페이지를 받은 뒤 앞쪽 행이 삭제돼 total이 1001 → 1000으로 줄었다.
        // 원래 1001번째 행이 1페이지 범위로 당겨져 아무도 받지 않는데, 받은 수(1000)와 마지막 total(1000)은 같다.
        expectPage(1, 1000, total = 1001, reportNos = (1..1000).map { "P$it" })
        expectPage(1001, 2000, total = 1000, reportNos = emptyList())

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }
        assertTrue(e.message!!.contains("total_count가 1001건에서 1000건으로"), e.message)
        assertEquals(0, cursorSaves())
    }

    @Test
    fun `total_count가 그대로면 끝까지 받는다`() {
        expectPage(1, 1000, total = 1001, reportNos = (1..1000).map { "P$it" })
        expectPage(1001, 2000, total = 1001, reportNos = listOf("P1001"))

        job.run(DefaultApplicationArguments())

        assertEquals(1001, received.size)
        assertEquals(1, cursorSaves())
    }

    private fun expectPage(start: Int, end: Int, total: Int, reportNos: List<String>) {
        val rows = reportNos.joinToString(",") { """{"PRDLST_REPORT_NO":"$it","PRDLST_NM":"제품"}""" }
        server.expect(requestTo("${FoodSafetyClient.BASE_URL}/k/I0030/json/$start/$end")).andRespond(
            withSuccess(
                """{"I0030":{"total_count":"$total","row":[$rows],"RESULT":{"CODE":"INFO-000","MSG":"정상"}}}""",
                MediaType.APPLICATION_JSON,
            ),
        )
    }
}
