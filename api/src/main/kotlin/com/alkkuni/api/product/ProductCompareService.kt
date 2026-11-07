package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.api.common.ErrorDetail
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

    fun compare(ids: List<Long>): ProductCompareResponse {
        if (ids.distinct().size != ids.size || ids.size !in MIN_PRODUCTS..MAX_PRODUCTS) {
            throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail("ids", "서로 다른 제품 ${MIN_PRODUCTS}~${MAX_PRODUCTS}개를 골라 주세요.")))
        }
        val found = productRepository.findAllById(ids).filter { it.status == ProductStatus.ACTIVE }.associateBy { it.id }
        val products = ids.map { found[it] ?: throw ApiException(ErrorCode.PRODUCT_NOT_FOUND, listOf(ErrorDetail("ids", "$it"))) }

        val rows = productIngredientRepository.findAllByProductIdInOrderById(ids)
        val names = ingredientRepository.findAllById(rows.mapNotNull { it.ingredientId }.toSet()).associate { it.id to it.displayName }
        // 사전에 연결된 성분은 id로, 없으면 원문 이름으로 묶는다. groupBy는 처음 나온 순서를 지킨다.
        val ingredients = rows.groupBy { it.ingredientId to (if (it.ingredientId == null) it.rawName else null) }
            .map { (key, group) ->
                val byProduct = group.groupBy { it.productId }
                CompareIngredient(
                    ingredientId = key.first,
                    name = key.first?.let(names::get) ?: group.first().rawName,
                    values = ids.map { id -> byProduct[id].orEmpty().map { it.toCompareAmount() } },
                )
            }
            .sortedByDescending { row -> row.values.count { it.isNotEmpty() } }

        return ProductCompareResponse(products.map { it.toCompareProduct() }, ingredients)
    }

    companion object {
        private const val MIN_PRODUCTS = 2
        private const val MAX_PRODUCTS = 3
    }
}
