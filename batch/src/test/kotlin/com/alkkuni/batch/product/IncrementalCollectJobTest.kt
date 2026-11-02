package com.alkkuni.batch.product

import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.product.IncrementalCollectJob.Companion.changedSince
import com.alkkuni.batch.raw.RawStore
import com.alkkuni.core.ingestion.entity.IngestionCursor
import com.alkkuni.core.ingestion.repository.IngestionCursorRepository
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IncrementalCollectJobTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun `커서 하루 전부터 받는다`() {
        assertEquals(LocalDate.of(2026, 10, 30), changedSince(LocalDate.of(2026, 10, 31), today))
    }

    @Test
    fun `커서가 오늘이어도 어제까지만 조회한다`() {
        // 오늘 날짜 CHNG_DT는 19시 전 INFO-700
        assertEquals(LocalDate.of(2026, 11, 1), changedSince(today, today))
    }

    @Test
    fun `커서는 뒤로 갈 수 없다`() {
        val cursor = IngestionCursor("I0030", today, LocalDateTime.now())
        assertFailsWith<IllegalArgumentException> { cursor.advance(today.minusDays(1), LocalDateTime.now()) }
    }

    @Test
    fun `CHNG_DT로 받아 upsert하고 커서를 오늘로 옮긴다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val received = mutableListOf<String>()
        val upserter = object : ProductUpserter(
            mock(ProductRepository::class.java),
            mock(ProductIngredientRepository::class.java),
        ) {
            override fun upsert(rows: List<I0030Row>): Int = rows.size.also { rows.mapTo(received) { it.reportNo } }
        }
        val now = LocalDate.now()
        val cursor = IngestionCursor("I0030", now.minusDays(3), LocalDateTime.now())
        val cursorRepository = mock(IngestionCursorRepository::class.java)
        `when`(cursorRepository.findById("I0030")).thenReturn(Optional.of(cursor))
        val since = now.minusDays(4).format(DateTimeFormatter.BASIC_ISO_DATE)
        server.expect(requestTo("${FoodSafetyClient.BASE_URL}/k/I0030/json/1/1000/CHNG_DT=$since")).andRespond(
            withSuccess(
                """{"I0030":{"total_count":"1","row":[{"PRDLST_REPORT_NO":"P1","PRDLST_NM":"제품"}],"RESULT":{"CODE":"INFO-000","MSG":"정상"}}}""",
                MediaType.APPLICATION_JSON,
            ),
        )
        val client = FoodSafetyClient(builder, JsonMapper.builder().build(), "k")

        IncrementalCollectJob(client, RawStore(Files.createTempDirectory("raw")), upserter, cursorRepository).run()

        server.verify()
        assertEquals(listOf("P1"), received)
        assertEquals(now, cursor.lastCollectedDate)
    }
}
