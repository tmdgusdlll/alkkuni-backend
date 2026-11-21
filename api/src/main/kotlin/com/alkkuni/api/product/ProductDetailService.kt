package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.api.brand.toRef
import com.alkkuni.api.category.refsOf
import com.alkkuni.core.brand.repository.BrandRepository
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.entity.ShoppingLink
import com.alkkuni.core.product.enums.ProductStatus
import com.alkkuni.core.product.repository.ProductCategoryRepository
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import com.alkkuni.core.product.repository.ShoppingLinkRepository
import com.alkkuni.core.recall.entity.Recall
import com.alkkuni.core.recall.repository.RecallRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ProductDetailService(
    private val productRepository: ProductRepository,
    private val productIngredientRepository: ProductIngredientRepository,
    private val ingredientRepository: IngredientRepository,
    private val recallRepository: RecallRepository,
    private val brandRepository: BrandRepository,
    private val productCategoryRepository: ProductCategoryRepository,
    private val shoppingLinkRepository: ShoppingLinkRepository,
) {

    fun detail(id: Long): ProductDetailResponse {
        val product = productRepository.findByIdOrNull(id)?.takeIf { it.status == ProductStatus.ACTIVE }
            ?: throw ApiException(ErrorCode.PRODUCT_NOT_FOUND)

        val rows = productIngredientRepository.findAllByProductIdOrderById(id)
        val ingredients = ingredientRepository.findAllById(rows.mapNotNull { it.ingredientId }.toSet()).associateBy { it.id }
        val recalls = recallRepository.findAllByReportNoAndProductTypeOrderById(product.reportNo, Recall.HEALTH_FUNCTIONAL_FOOD)

        return product.toDetailResponse(
            brand = product.brandId?.let { brandRepository.findByIdOrNull(it) }?.toRef(),
            categories = productCategoryRepository.refsOf(id),
            ingredients = rows.map { it.toDetailIngredient(it.ingredientId?.let(ingredients::get)) },
            recalls = recalls.map { it.toDetailRecall() },
            // 대표 링크: 상세 링크를 검색 링크보다 먼저, 같으면 사람이 최근에 확인한 것, 그것도 같으면 나중에 넣은 것(요청마다 같은 링크).
            purchaseLink = shoppingLinkRepository.findAllByProductId(id)
                .minWithOrNull(compareBy<ShoppingLink> { it.type }.thenByDescending { it.checkedAt }.thenByDescending { it.id })
                ?.toPurchaseLink(),
        )
    }
}
