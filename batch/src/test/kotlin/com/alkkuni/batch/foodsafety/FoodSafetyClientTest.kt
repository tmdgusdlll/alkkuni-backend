package com.alkkuni.batch.foodsafety

import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodSafetyClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = FoodSafetyClient(builder, JsonMapper.builder().build(), KEY)

    @Test
    fun `정상 응답은 전체 건수, 행, 원문 본문을 돌려준다`() {
        val body = javaClass.getResource("/foodsafety/I0030-ok.json")!!.readText()
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000")).andRespond(json(body))

        val page = client.fetchPage("I0030", 1, 1000)

        assertEquals(2, page.totalCount)
        assertEquals(2, page.rows.size)
        assertEquals("20040020028415", page.rows[0].path("PRDLST_REPORT_NO").asString())
        assertEquals(body, page.rawBody)
    }

    @Test
    fun `변경일은 CHNG_DT=yyyyMMdd 경로로 붙는다`() {
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000/CHNG_DT=20261101"))
            .andRespond(json(ok(totalCount = 0)))

        client.fetchPage("I0030", 1, 1000, LocalDate.of(2026, 11, 1))

        server.verify()
    }

    @Test
    fun `INFO-200은 빈 페이지다`() {
        // 실제 응답 모양(2026-11-02 CHNG_DT=20991231 호출)
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000")).andRespond(
            json("""{"I0030":{"total_count":"0","RESULT":{"MSG":"해당하는 데이터가 없습니다.","CODE":"INFO-200"}}}"""),
        )

        val page = client.fetchPage("I0030", 1, 1000)

        assertEquals(0, page.totalCount)
        assertTrue(page.rows.isEmpty())
    }

    @Test
    fun `INFO-320이면 한 번만 호출하고 한도 예외로 멈춘다`() {
        // INFO-320 본문은 확인하지 못해 RESULT가 최상위에 오는 경우로 둔다(서비스 객체 안은 INFO-200 테스트가 본다).
        server.expect(once(), requestTo("$BASE/$KEY/I0030/json/1/1000"))
            .andRespond(json("""{"RESULT":{"MSG":"1시간당 100회까지만 호출할 수 있습니다.","CODE":"INFO-320"}}"""))

        assertFailsWith<FoodSafetyRateLimitException> {
            client.forEachPage("I0030") { }
        }
        server.verify()
    }

    @Test
    fun `그 밖의 코드는 API 예외다`() {
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000"))
            .andRespond(json("""{"RESULT":{"MSG":"인증키가 유효하지 않습니다.","CODE":"INFO-100"}}"""))

        val e = assertFailsWith<FoodSafetyApiException> { client.fetchPage("I0030", 1, 1000) }
        assertFalse(e is FoodSafetyRateLimitException)
    }

    @Test
    fun `전체 건수만큼 1000건씩 나눠 받는다`() {
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000")).andRespond(json(ok(totalCount = 1500)))
        server.expect(requestTo("$BASE/$KEY/I0030/json/1001/2000")).andRespond(json(ok(totalCount = 1500)))

        var pages = 0
        client.forEachPage("I0030") { pages++ }

        assertEquals(2, pages)
        server.verify()
    }

    @Test
    fun `네트워크 오류 메시지와 원인에 인증키가 남지 않는다`() {
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000")).andRespond(withException(IOException("timeout")))

        val e = assertFailsWith<FoodSafetyApiException> { client.fetchPage("I0030", 1, 1000) }

        assertFalse(e.message!!.contains(KEY), e.message)
        assertEquals(null, e.cause)
    }

    @Test
    fun `특수문자가 든 키도 HTTP 오류 메시지에 남지 않는다`() {
        val specialKey = "ab+cd/ef=="
        val b = RestClient.builder()
        val s = MockRestServiceServer.bindTo(b).build()
        val c = FoodSafetyClient(b, JsonMapper.builder().build(), specialKey)
        s.expect(requestTo("$BASE/ab%2Bcd%2Fef%3D%3D/I0030/json/1/1000")).andRespond(withServerError())

        val e = assertFailsWith<FoodSafetyApiException> { c.fetchPage("I0030", 1, 1000) }

        assertEquals("I0030 1-1000: HTTP 500", e.message)
        assertEquals(null, e.cause)
    }

    @Test
    fun `JSON이 아닌 응답은 API 예외다`() {
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000"))
            .andRespond(withSuccess("<html>시스템 점검 중</html>", MediaType.TEXT_HTML))

        val e = assertFailsWith<FoodSafetyApiException> { client.fetchPage("I0030", 1, 1000) }
        assertFalse(e.message!!.contains("점검"), e.message)
    }

    @Test
    fun `정상 코드인데 total_count가 없으면 API 예외다`() {
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000"))
            .andRespond(json("""{"I0030":{"row":[],"RESULT":{"CODE":"INFO-000"}}}"""))

        assertFailsWith<FoodSafetyApiException> { client.fetchPage("I0030", 1, 1000) }
    }

    @Test
    fun `row가 배열이 아니면 API 예외다`() {
        server.expect(requestTo("$BASE/$KEY/I0030/json/1/1000")).andRespond(
            json("""{"I0030":{"total_count":"1","row":{"PRDLST_NM":"x"},"RESULT":{"CODE":"INFO-000"}}}"""),
        )

        assertFailsWith<FoodSafetyApiException> { client.fetchPage("I0030", 1, 1000) }
    }

    @Test
    fun `페이지 toString에 응답 본문이 들어가지 않는다`() {
        val page = FoodSafetyPage(totalCount = 1, rows = emptyList(), rawBody = "큰 본문")

        assertEquals("FoodSafetyPage(totalCount=1, rows=0)", page.toString())
    }

    private fun json(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private fun ok(totalCount: Int) =
        """{"I0030":{"total_count":"$totalCount","row":[],"RESULT":{"MSG":"정상처리되었습니다.","CODE":"INFO-000"}}}"""

    companion object {
        private const val KEY = "test-key-1234"
        private const val BASE = FoodSafetyClient.BASE_URL
    }
}
