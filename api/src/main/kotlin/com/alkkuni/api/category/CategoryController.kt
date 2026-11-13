package com.alkkuni.api.category

import com.alkkuni.api.common.ApiResponse
import com.alkkuni.api.common.PUBLIC_CACHE
import com.alkkuni.core.product.FunctionalCategory
import com.alkkuni.core.product.repository.ProductCategoryRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 기능성 카테고리 전체(FunctionalCategory 순서). "기능성으로 찾기" 화면이 칩을 그리고, 고른 code를 성분별 제품 목록의 category=로 보낸다.
 * 24개 고정이라 페이지 없이 내려준다. 제품이 없는 카테고리도 0으로 낸다(칩 목록이 데이터에 따라 바뀌지 않게).
 */
@RestController
@RequestMapping("/api/categories")
class CategoryController(private val productCategoryRepository: ProductCategoryRepository) {

    @GetMapping
    fun list(): ResponseEntity<ApiResponse<List<CategoryItem>>> {
        val counts = productCategoryRepository.countProducts().associate { it.category to it.count }
        return ResponseEntity.ok()
            .cacheControl(PUBLIC_CACHE)
            .body(ApiResponse.ok(FunctionalCategory.entries.map { CategoryItem(it.name, it.label, counts[it] ?: 0) }))
    }
}

data class CategoryItem(
    // 필터 값(category=EYE). enum 이름 그대로다.
    val code: String,
    val label: String,
    // 공개 제품 수(삭제·원료 제외, 수출용 포함)
    val productCount: Long,
)

