package com.alkkuni.batch.recall

import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.noLock
import com.alkkuni.batch.raw.RawStore
import com.alkkuni.core.recall.entity.Recall
import com.alkkuni.core.recall.enums.RecallListStatus
import com.alkkuni.core.recall.repository.RecallRepository
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
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecallCollectJobTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val earlier = LocalDateTime.of(2026, 11, 1, 9, 0)

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = FoodSafetyClient(builder, JsonMapper.builder().build(), "k")
    private val repository = mock(RecallRepository::class.java)
    private val job = RecallCollectJob(
        client, RawStore(Files.createTempDirectory("raw")), RecallSnapshotApplier(repository), noLock(), clock,
    )

    private fun recall(seq: String, status: RecallListStatus = RecallListStatus.LISTED) =
        Recall(seq, null, "제품$seq", null, null, null, null, earlier).also { if (status == RecallListStatus.DELISTED) it.delist() }

    private fun givenStored(vararg recalls: Recall) = `when`(repository.findAll()).thenReturn(recalls.toList())

    @Suppress("UNCHECKED_CAST")
    private fun created(): List<String> = mockingDetails(repository).invocations
        .filter { it.method.name == "saveAll" }
        .flatMap { it.arguments[0] as Iterable<Recall> }
        .map { it.sourceSeq }

    @Test
    fun `새 건은 저장하고 사라진 건은 빠짐으로, 돌아온 건은 다시 목록으로 바꾼다`() {
        val kept = recall("1")
        val gone = recall("2")
        val back = recall("3", RecallListStatus.DELISTED)
        givenStored(kept, gone, back, recall("4"), recall("5"))
        expectPage(total = 5, seqs = listOf("1", "3", "4", "5", "6"))

        job.run(DefaultApplicationArguments())

        server.verify()
        assertEquals(listOf("6"), created())
        assertEquals(RecallListStatus.DELISTED, gone.listStatus)
        assertEquals(earlier, gone.lastSeenAt) // 마지막으로 본 시각은 그대로 남는다
        assertEquals(RecallListStatus.LISTED, back.listStatus)
        assertEquals(LocalDateTime.now(clock), kept.lastSeenAt)
    }

    @Test
    fun `빈 목록이 오면 아무것도 빠짐으로 바꾸지 않고 실패한다`() {
        val stored = recall("1")
        givenStored(stored, recall("2"))
        expectPage(total = 0, seqs = emptyList())

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertTrue(e.message!!.contains("2건 중 2건이 한 번에 빠졌다"), e.message)
        assertEquals(RecallListStatus.LISTED, stored.listStatus)
        assertEquals(emptyList(), created())
    }

    @Test
    fun `목록에 있던 건의 절반 넘게 빠지면 빠짐 처리만 건너뛰고 새 건은 저장한다`() {
        val stored = recall("2")
        givenStored(recall("1"), stored, recall("3"))
        expectPage(total = 2, seqs = listOf("1", "4"))

        assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertEquals(listOf("4"), created())
        assertEquals(RecallListStatus.LISTED, stored.listStatus)
    }

    @Test
    fun `받은 일련번호 수가 total_count와 다르면 새 건은 저장하고 빠짐 처리는 하지 않은 채 실패한다`() {
        val stored = recall("9")
        givenStored(stored)
        expectPage(total = 3, seqs = listOf("1", "2"))

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertTrue(e.message!!.contains("받은 일련번호 2건이 total_count 3건과 다르다"), e.message)
        assertEquals(listOf("1", "2"), created().sorted())
        assertEquals(RecallListStatus.LISTED, stored.listStatus)
    }

    @Test
    fun `제품명이 빈 행은 건너뛰고 나머지는 저장하되 빠짐 처리는 하지 않는다`() {
        val stored = recall("9")
        givenStored(stored)
        expectPage(total = 2, seqs = listOf("1"), extraRow = """{"RTRVLDSUSE_SEQ":"2","PRDTNM":""}""")

        val e = assertFailsWith<IllegalStateException> { job.run(DefaultApplicationArguments()) }

        assertTrue(e.message!!.contains("받은 일련번호 1건이 total_count 2건과 다르다"), e.message)
        assertEquals(listOf("1"), created())
        assertEquals(RecallListStatus.LISTED, stored.listStatus)
    }

    private fun expectPage(total: Int, seqs: List<String>, extraRow: String? = null) {
        val rows = (seqs.map { """{"RTRVLDSUSE_SEQ":"$it","PRDTNM":"제품$it","PRDLST_REPORT_NO":""}""" } + listOfNotNull(extraRow))
            .joinToString(",")
        server.expect(requestTo("${FoodSafetyClient.BASE_URL}/k/I0490/json/1/1000")).andRespond(
            withSuccess(
                """{"I0490":{"total_count":"$total","row":[$rows],"RESULT":{"CODE":"INFO-000","MSG":"정상"}}}""",
                MediaType.APPLICATION_JSON,
            ),
        )
    }
}
