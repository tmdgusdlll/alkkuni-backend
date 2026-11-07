package com.alkkuni.api.product

import com.alkkuni.api.common.ApiResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/products")
class ProductController(private val searchService: ProductSearchService) {

    // 공개 조회라 성공 응답만 60초 캐시한다(PLANNING.md "캐시"). 에러 응답은 GlobalExceptionHandler가 만들어 헤더가 붙지 않는다.
    @GetMapping
    fun search(
        @RequestParam @NotBlank(message = "검색어를 입력해 주세요.") @Size(max = 100, message = "100자 이하로 입력해 주세요.") q: String,
        @RequestParam(defaultValue = "relevance") sort: String,
        @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int,
    ): ResponseEntity<ApiResponse<ProductSearchResponse>> =
        ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .body(ApiResponse.ok(searchService.search(q, sort, page, size)))

    companion object {
        // ponytail: OFFSET 페이지네이션. 깊은 페이지는 앞 행을 모두 읽으므로 상한을 둔다. 무한 스크롤이 필요해지면 키셋으로 바꾼다.
        private const val MAX_PAGE = 500L
    }
}
