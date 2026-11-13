package com.alkkuni.api.product

import com.alkkuni.api.common.ApiResponse
import com.alkkuni.api.common.PUBLIC_CACHE
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val searchService: ProductSearchService,
    private val compareService: ProductCompareService,
    private val detailService: ProductDetailService,
    private val exploreService: ProductExploreService,
) {

    // 공개 조회라 성공 응답만 60초 캐시한다(PLANNING.md "캐시"). 에러 응답은 GlobalExceptionHandler가 만들어 헤더가 붙지 않는다.
    @GetMapping
    fun search(
        @RequestParam @NotBlank(message = "검색어를 입력해 주세요.") @Size(max = 100, message = "100자 이하로 입력해 주세요.") q: String,
        @RequestParam(defaultValue = "relevance") sort: String,
        // all(기본, 수출용은 뒤로), domestic(국내용만), export(수출용만)
        @RequestParam(defaultValue = "all") market: String,
        @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int,
    ): ResponseEntity<ApiResponse<ProductSearchResponse>> =
        ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(searchService.search(q, sort, market, page, size)))

    // ids=1,2,3. 개수·중복 검사는 서비스에서 한다.
    @GetMapping("/compare")
    fun compare(@RequestParam ids: List<Long?>): ResponseEntity<ApiResponse<ProductCompareResponse>> =
        ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(compareService.compare(ids)))

    // 성분별 제품 목록. ingredient=1,2(모두 포함), exclude=3(하나라도 들면 제외), form=정,캡슐, brand=4,5(그중 하나, 브랜드 목록 API의 id).
    // min·max는 기준 성분의 1일 함량 범위이고 단위는 응답 ingredients[].defaultUnit(없으면 mg 등 저장 단위)이다.
    // meetsReference=true면 고른 성분 모두 1일 기준값 이상인 제품만 낸다. 기준값 유무는 성분 목록 API의 referenceAmount로 안다.
    @GetMapping("/explore")
    fun explore(
        @RequestParam ingredient: List<Long?>,
        @RequestParam(required = false) exclude: List<Long?>?,
        @RequestParam(required = false) form: List<String>?,
        @RequestParam(required = false) brand: List<Long?>?,
        @RequestParam(required = false) @PositiveOrZero @DecimalMax(MAX_AMOUNT) min: BigDecimal?,
        @RequestParam(required = false) @PositiveOrZero @DecimalMax(MAX_AMOUNT) max: BigDecimal?,
        @RequestParam(defaultValue = "false") meetsReference: Boolean,
        @RequestParam(defaultValue = "all") market: String,
        @RequestParam(defaultValue = "amount") sort: String,
        @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) size: Int,
    ): ResponseEntity<ApiResponse<ProductExploreResponse>> =
        ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(exploreService.explore(ingredient, exclude.orEmpty(), form.orEmpty(), brand.orEmpty(), min, max, meetsReference, market, sort, page, size)))

    // 숫자가 아닌 id는 타입 변환 실패로 400이 된다. /compare·/explore는 리터럴 경로라 이 매핑보다 먼저 잡힌다.
    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): ResponseEntity<ApiResponse<ProductDetailResponse>> =
        ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(detailService.detail(id)))

    companion object {
        // ponytail: OFFSET 페이지네이션. 깊은 페이지는 앞 행을 모두 읽으므로 상한을 둔다. 무한 스크롤이 필요해지면 키셋으로 바꾼다.
        private const val MAX_PAGE = 500L

        // 함량 범위 상한. 균수(CFU)가 1일 수천억(1e11)대라 넉넉히 두되, PostgreSQL numeric을 넘는 값("1e999999999")이 500이 되지 않게 막는다.
        private const val MAX_AMOUNT = "1E15"

    }
}
