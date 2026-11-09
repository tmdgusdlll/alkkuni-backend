package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.api.common.ErrorDetail
import com.alkkuni.core.ingredient.IngredientName
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.enums.ProductStatus
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import org.springframework.stereotype.Service

@Service
class ProductCompareService(
    private val productRepository: ProductRepository,
    private val productIngredientRepository: ProductIngredientRepository,
    private val ingredientRepository: IngredientRepository,
) {

    // "ids=5,"의 빈 원소는 null로 바인딩된다. Kotlin은 List<Long>의 원소 null을 검사하지 않아 Long?로 받는다.
    fun compare(rawIds: List<Long?>): ProductCompareResponse {
        val ids = rawIds.filterNotNull()
        // 개수를 먼저 봐서 긴 목록은 distinct 없이 거절한다.
        if (ids.size != rawIds.size || ids.size !in MIN_PRODUCTS..MAX_PRODUCTS || ids.distinct().size != ids.size) {
            throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail("ids", "서로 다른 제품 ${MIN_PRODUCTS}~${MAX_PRODUCTS}개를 골라 주세요.")))
        }
        // ponytail: 응답엔 6칼럼만 쓰지만 엔티티 전체(긴 원문 TEXT 포함)를 읽는다. 2~3행이라 그대로 둔다. 호출이 많아지면 프로젝션으로 바꾼다.
        val found = productRepository.findAllById(ids).filter { it.status == ProductStatus.ACTIVE }.associateBy { it.id }
        val products = ids.map { found[it] ?: throw ApiException(ErrorCode.PRODUCT_NOT_FOUND, listOf(ErrorDetail("ids", "제품 ${it}을(를) 찾을 수 없습니다."))) }

        val rows = productIngredientRepository.findAllByProductIdInOrderById(ids)
        val dict = ingredientRepository.findAllById(rows.mapNotNull { it.ingredientId }.toSet()).associateBy { it.id }
        // 사전에 연결된 성분은 id로, 없으면 사전 조회와 같은 키(IngredientName.normalize)로 묶는다. groupBy는 처음 나온 순서를 지킨다.
        val ingredients = rows.groupBy { it.ingredientId to (if (it.ingredientId == null) IngredientName.normalize(it.rawName) else null) }
            .map { (key, group) ->
                val byProduct = group.groupBy { it.productId }
                val ingredient = key.first?.let(dict::get)
                CompareIngredient(
                    ingredientId = key.first,
                    name = ingredient?.displayName ?: group.first().rawName,
                    referenceAmount = ingredient?.referenceAmount,
                    referenceUnit = ingredient?.referenceUnit,
                    values = ids.map { id -> byProduct[id].orEmpty().map { it.toCompareAmount(ingredient) } },
                )
            }
            // 여러 제품에 든 성분 먼저, 같으면 앞 열 제품의 성분 먼저. 정렬이 안정적이라 그 안은 성분표 순서다.
            .sortedWith(
                compareByDescending<CompareIngredient> { row -> row.values.count { it.isNotEmpty() } }
                    .thenBy { row -> row.values.indexOfFirst { it.isNotEmpty() } },
            )

        return ProductCompareResponse(products.map { it.toCompareProduct() }, ingredients)
    }

    companion object {
        private const val MIN_PRODUCTS = 2
        private const val MAX_PRODUCTS = 3
    }
}
