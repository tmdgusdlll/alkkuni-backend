package com.alkkuni.batch.foodsafety

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
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
        val root = jsonMapper.readTree(body)
        val serviceNode = root.path(service)
        // INFO-000·200·700은 서비스 객체 안에 RESULT가 온다. INFO-320 본문은 확인하지 못해 최상위도 본다.
        val result = serviceNode.path("RESULT").takeUnless { it.isMissingNode } ?: root.path("RESULT")
        val code = result.path("CODE").asString("")
        val message = result.path("MSG").asString("")

        return when (code) {
            "INFO-000" -> FoodSafetyPage(
                totalCount = serviceNode.path("total_count").asString().toInt(),
                rows = serviceNode.path("row").toList(),
                rawBody = body,
            )
            "INFO-200" -> FoodSafetyPage(totalCount = 0, rows = emptyList(), rawBody = body)
            // 재시도하면 한도만 더 쓴다. 호출한 쪽이 멈추도록 별도 예외로 던진다.
            "INFO-320" -> throw FoodSafetyRateLimitException("$service $start-$end: $code $message")
            else -> throw FoodSafetyApiException("$service $start-$end: $code $message")
        }
    }

    /**
     * 처음부터 끝까지 [PAGE_SIZE]씩 받아 페이지마다 [action]을 부른다.
     * 도중에 건수가 바뀌어 빠지는 행은 증분 수집의 하루 겹침(`CHNG_DT = 마지막 수집일 - 1일`)이 다시 받는다.
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
            // 원인 예외는 붙이지 않는다. 메시지에 키가 든 URL이 있어 스택 트레이스로 새어 나간다.
            throw FoodSafetyApiException(
                "$service $start-$end: ${e.javaClass.simpleName} ${e.message?.replace(apiKey, "***")}",
            )
        }
    }

    companion object {
        const val BASE_URL = "https://openapi.foodsafetykorea.go.kr/api"

        // 공식 문서상 한 번에 최대 1000건. 시간당 100회 한도에서 호출 수를 줄이려고 최대로 둔다.
        const val PAGE_SIZE = 1000
    }
}

/** 한 페이지 응답. [rawBody]는 원본 보존용 응답 본문 그대로다. */
data class FoodSafetyPage(
    val totalCount: Int,
    val rows: List<JsonNode>,
    val rawBody: String,
)

open class FoodSafetyApiException(message: String) : RuntimeException(message)

/** `INFO-320`: CHNG_DT 없이 시간당 100회를 넘었다. 재시도하지 않고 멈춘다. */
class FoodSafetyRateLimitException(message: String) : FoodSafetyApiException(message)
