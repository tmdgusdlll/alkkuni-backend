package com.alkkuni.api.news

import com.alkkuni.api.common.ApiResponse
import com.alkkuni.api.common.PUBLIC_CACHE
import com.alkkuni.core.recall.entity.Recall
import com.alkkuni.core.recall.repository.RecallRepository
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 메인 소식 칸(4건)과 전체 보기(/news)가 쓰는 목록. 지금은 건강기능식품 회수만 낸다.
 * 공지·기사가 생기면 type을 늘리고 그때 저장 방식(UNION ALL 또는 news 테이블)을 정한다(PLANNING.md "메인 통계·소식 API").
 */
@RestController
@RequestMapping("/api/news")
class NewsController(private val recallRepository: RecallRepository) {

    @GetMapping
    fun news(
        @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int,
    ): ResponseEntity<ApiResponse<NewsResponse>> {
        val result = recallRepository.findNews(Recall.HEALTH_FUNCTIONAL_FOOD, PageRequest.of(page, size))
        return ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(NewsResponse(totalCount = result.totalElements, items = result.content.map { it.toNewsItem() })))
    }

    companion object {
        private const val MAX_PAGE = 500L
    }
}

data class NewsResponse(
    val totalCount: Long,
    val items: List<NewsItem>,
)

data class NewsItem(
    val type: NewsType,
    val id: Long,
    // 회수는 제품명
    val title: String,
    val manufacturer: String?,
    val reason: String?,
    // 회수 등급 원문(1·2·3등급, 자율회수 등). null이면 화면이 등급을 숨긴다
    val grade: String?,
    // 식약처 등록일. 등록일을 받기 전에 목록에서 빠진 회수는 null이다
    val date: LocalDate?,
)

enum class NewsType { RECALL }

fun Recall.toNewsItem() = NewsItem(
    type = NewsType.RECALL,
    id = requireNotNull(id),
    title = productName,
    manufacturer = manufacturer,
    reason = reason,
    grade = grade,
    date = registeredAt?.toLocalDate(),
)
