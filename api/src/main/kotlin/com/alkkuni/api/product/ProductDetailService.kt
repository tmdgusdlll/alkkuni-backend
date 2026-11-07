package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.enums.ProductStatus
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import com.alkkuni.core.recall.repository.RecallRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ProductDetailService(
    private val productRepository: ProductRepository,
    private val productIngredientRepository: ProductIngredientRepository,
    private val ingredientRepository: IngredientRepository,
    private val recallRepository: RecallRepository,
) {

    fun detail(id: Long): ProductDetailResponse {
        val product = productRepository.findByIdOrNull(id)?.takeIf { it.status == ProductStatus.ACTIVE }
            ?: throw ApiException(ErrorCode.PRODUCT_NOT_FOUND)

        val rows = productIngredientRepository.findAllByProductIdOrderById(id)
        val names = ingredientRepository.findAllById(rows.mapNotNull { it.ingredientId }.toSet()).associate { it.id to it.displayName }
        val recalls = recallRepository.findAllByReportNoAndProductTypeOrderById(product.reportNo, HEALTH_FUNCTIONAL_FOOD)

        return product.toDetailResponse(
            ingredients = rows.map { it.toDetailIngredient(it.ingredientId?.let(names::get)) },
            recalls = recalls.map { it.toDetailRecall() },
        )
    }

    companion object {
        // I0490 PRDLST_TYPE 값
        private const val HEALTH_FUNCTIONAL_FOOD = "건강기능식품"
    }
}
