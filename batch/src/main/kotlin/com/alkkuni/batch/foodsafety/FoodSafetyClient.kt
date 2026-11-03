package com.alkkuni.batch.foodsafety

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import org.springframework.web.util.UriUtils
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

/**
 * 식품안전나라 OpenAPI 클라이언트.
 *
 * 에러도 HTTP 200으로 오고 본문의 `RESULT.CODE`로 구분한다.
 * 인증키가 URL 경로에 들어가므로, 예외 메시지에 URL이 섞이지 않게 한다.
 */
@Component
class FoodSafetyClient(
    restClientBuilder: RestClient.Builder,
    private val jsonMapper: JsonMapper,
    @Value("\${foodsafety.api-key}") private val apiKey: String,
) {
    init {
        // 빈 키는 호출해 봐야 인증 오류라 기동 시점에 막는다. 키 마스킹(replace)도 빈 문자열이면 깨진다.
        require(apiKey.isNotBlank()) { "foodsafety.api-key(FOODSAFETY_API_KEY)가 비어 있다" }
    }

    private val restClient = restClientBuilder.baseUrl(BASE_URL).build()

    /** [start]~[end] 행(1부터, 양끝 포함)을 받는다. [changedSince]를 주면 그날 이후 변경분만 온다. */
    fun fetchPage(service: String, start: Int, end: Int, changedSince: LocalDate? = null): FoodSafetyPage {
        val body = request(service, start, end, changedSince)
        val where = "$service $start-$end"
        // 점검 안내 HTML처럼 JSON이 아닌 응답도 API 예외 하나로 받게 한다. 본문은 메시지에 넣지 않는다.
        val root = try {
            jsonMapper.readTree(body)
        } catch (e: JacksonException) {
            throw FoodSafetyApiException("$where: JSON이 아닌 응답")
        }
        val serviceNode = root.path(service)
        // INFO-000·200·700은 서비스 객체 안에 RESULT가 온다. INFO-320 본문은 확인하지 못해 최상위도 본다.
        val result = serviceNode.path("RESULT").takeUnless { it.isMissingNode } ?: root.path("RESULT")
        val code = result.path("CODE").asString("")
        val message = result.path("MSG").asString("")

        return when (code) {
            "INFO-000" -> {
                val totalCount = serviceNode.path("total_count").asString("").toIntOrNull()
                    ?: throw FoodSafetyApiException("$where: total_count를 읽을 수 없음")
                val rows = serviceNode.path("row")
                // 배열이 아니면 객체의 필드값을 행으로 세게 된다. 잘못된 데이터를 넘기지 않고 멈춘다.
                if (!rows.isArray) throw FoodSafetyApiException("$where: row가 배열이 아님")
                FoodSafetyPage(totalCount = totalCount, rows = rows.toList(), rawBody = body)
            }
            "INFO-200" -> FoodSafetyPage(totalCount = 0, rows = emptyList(), rawBody = body)
            // 재시도하면 한도만 더 쓴다. 호출한 쪽이 멈추도록 별도 예외로 던진다.
            "INFO-320" -> throw FoodSafetyRateLimitException("$where: $code $message")
            else -> throw FoodSafetyApiException("$where: $code $message")
        }
    }

    /**
     * 처음부터 끝까지 [PAGE_SIZE]씩 받아 페이지마다 [action]을 부른다.
     * 위치(start/end)로 나눠 받으므로 도중에 행이 추가·삭제되면 경계의 행이 빠지거나 겹칠 수 있다.
     * 겹친 행은 upsert가 흡수한다. 빠진 행은 초기 적재와 증분 모두 받은 신고번호 수를 `total_count`와 비교해 잡고
     * 실패로 끝낸다. "목록에 없으면 삭제"로 판단하는 전체 대사에는 쓰지 않는다(파일 방식).
     */
    fun forEachPage(service: String, changedSince: LocalDate? = null, action: (FoodSafetyPage) -> Unit) {
        var start = 1
        do {
            val page = fetchPage(service, start, start + PAGE_SIZE - 1, changedSince)
            action(page)
            start += PAGE_SIZE
        } while (start <= page.totalCount)
    }

    private fun request(service: String, start: Int, end: Int, changedSince: LocalDate?): String {
        val path = buildString {
            append("/{key}/{service}/json/{start}/{end}")
            if (changedSince != null) append("/CHNG_DT={changedSince}")
        }
        val variables = mapOf(
            "key" to apiKey,
            "service" to service,
            "start" to start,
            "end" to end,
            "changedSince" to changedSince?.format(DateTimeFormatter.BASIC_ISO_DATE),
        )
        return try {
            restClient.get().uri(path, variables).retrieve().body<String>()
                ?: throw FoodSafetyApiException("$service $start-$end: 빈 응답")
        } catch (e: RestClientException) {
            // RestClient 예외 메시지에는 키가 든 URL이 들어간다. 그 메시지와 원인 예외를 쓰지 않고,
            // HTTP 상태나 원인 예외의 종류·메시지만 남긴다.
            val reason = when (e) {
                is RestClientResponseException -> "HTTP ${e.statusCode.value()}"
                else -> e.cause?.let { "${it.javaClass.simpleName} ${it.message}" } ?: e.javaClass.simpleName
            }
            throw FoodSafetyApiException("$service $start-$end: ${maskKey(reason)}")
        }
    }

    // 원인 메시지에 혹시 URL이 섞여도 원래 키와 RestClient가 인코딩한 키(+ → %2B, = → %3D 등)를 모두 가린다.
    private fun maskKey(text: String): String =
        text.replace(apiKey, "***").replace(UriUtils.encode(apiKey, StandardCharsets.UTF_8), "***")

    companion object {
        const val BASE_URL = "https://openapi.foodsafetykorea.go.kr/api"

        // 공식 문서상 한 번에 최대 1000건. 시간당 100회 한도에서 호출 수를 줄이려고 최대로 둔다.
        const val PAGE_SIZE = 1000
    }
}

/**
 * 한 페이지 응답. [rawBody]는 원본 보존용 응답 본문 그대로다.
 * 본문이 수 MB라 로그에 통째로 찍히지 않게 data class 대신 toString을 직접 둔다.
 */
class FoodSafetyPage(
    val totalCount: Int,
    val rows: List<JsonNode>,
    val rawBody: String,
) {
    override fun toString() = "FoodSafetyPage(totalCount=$totalCount, rows=${rows.size})"
}

open class FoodSafetyApiException(message: String) : RuntimeException(message)

/** `INFO-320`: CHNG_DT 없이 시간당 100회를 넘었다. 재시도하지 않고 멈춘다. */
class FoodSafetyRateLimitException(message: String) : FoodSafetyApiException(message)
