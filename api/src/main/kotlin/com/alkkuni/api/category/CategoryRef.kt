package com.alkkuni.api.category

import com.alkkuni.core.product.FunctionalCategory
import com.alkkuni.core.product.repository.ProductCategoryRepository

// 제품 응답에 붙는 기능성 카테고리. code는 카테고리 목록 API의 code, 성분별 목록의 category= 값과 같다.
data class CategoryRef(
    val code: String,
    val label: String,
)

fun FunctionalCategory.toRef() = CategoryRef(code = name, label = label)

// 제품 여러 개의 카테고리. 한 번에 조회하고 FunctionalCategory 순서로 둔다. 카테고리가 없는 제품은 키가 없다.
fun ProductCategoryRepository.refsOf(productIds: Collection<Long>): Map<Long, List<CategoryRef>> =
    if (productIds.isEmpty()) {
        emptyMap()
    } else {
        findAllByProductIdIn(productIds).groupBy({ it.productId }, { it.category })
            .mapValues { (_, categories) -> categories.sorted().map { it.toRef() } }
    }
