package com.alkkuni.api.stats

import com.alkkuni.api.common.ApiResponse
import com.alkkuni.api.common.PUBLIC_CACHE
import com.alkkuni.core.ingestion.entity.IngestionCursor
import com.alkkuni.core.ingestion.repository.IngestionCursorRepository
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.enums.ProductStatus
import com.alkkuni.core.product.repository.ProductRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 메인 "공식 데이터로 만들어요" 칸과 푸터 기준일이 쓰는 숫자. 이미 적재된 데이터를 세기만 한다.
 * 하루 한 번(새벽 수집) 바뀌는 값이지만 다른 공개 API와 같은 60초 캐시로 둔다. COUNT가 부담되면 그때 늘린다.
 */
@RestController
@RequestMapping("/api/stats")
class StatsController(
    private val productRepository: ProductRepository,
    private val ingredientRepository: IngredientRepository,
    private val cursorRepository: IngestionCursorRepository,
) {

    @GetMapping
    fun stats(): ResponseEntity<ApiResponse<StatsResponse>> =
        ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(
                ApiResponse.ok(
                    StatsResponse(
                        productCount = productRepository.countByStatusAndRawMaterialFalse(ProductStatus.ACTIVE),
                        // 성분 목록 API(/api/ingredients)가 내려주는 개수와 같다
                        ingredientCount = ingredientRepository.count(),
                        // 커서는 수집을 실행한 날(KST 새벽)이고, 그날 변경분은 아직 확정 전이라 다음 날 받는다.
                        // 화면에 "이 날짜까지 반영"으로 보이도록 하루를 뺀다.
                        dataDate = cursorRepository.findByIdOrNull(IngestionCursor.PRODUCT_SOURCE)?.lastCollectedDate?.minusDays(1),
                    ),
                ),
            )
}

data class StatsResponse(
    val productCount: Long,
    val ingredientCount: Long,
    // 이 날짜까지의 변경이 반영됐다(커서 - 1일). 첫 적재 전이면 null이다. 적재가 멈추면 이 날짜도 멈춰 데이터가 오래됐다는 신호가 된다.
    val dataDate: LocalDate?,
)
