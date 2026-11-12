package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.api.common.ErrorDetail
import com.alkkuni.api.brand.refsOf
import com.alkkuni.api.brand.toRef
import com.alkkuni.core.brand.BrandName
import com.alkkuni.core.brand.repository.BrandAliasRepository
import com.alkkuni.core.brand.repository.BrandRepository
import com.alkkuni.core.ingredient.IngredientName
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.repository.ProductSearchCondition
import com.alkkuni.core.product.repository.ProductSearchRepository
import com.alkkuni.core.product.repository.ProductSearchSort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ProductSearchService(
    private val searchRepository: ProductSearchRepository,
    private val ingredientRepository: IngredientRepository,
    private val brandRepository: BrandRepository,
    private val brandAliasRepository: BrandAliasRepository,
) {

    /**
     * 제품명·제조사·성분으로 검색한다.
     * - 성분은 사전(ingredient_alias)으로 정확히 일치할 때만 찾는다. 유사도는 제품명·제조사에만 쓴다.
     * - 브랜드도 사전(brand_alias, 보류 표기 제외)으로 정확히 일치할 때만 그 브랜드 제품을 더한다("애터미" → "ATOMY …").
     * - 공백을 뺀 2자 미만은 성분명과 정확히 일치할 때만 받는다("철"). 1자 부분 일치는 결과가 너무 넓다.
     */
    fun search(q: String, sort: String, page: Int, size: Int): ProductSearchResponse {
        val query = q.trim()
        val ingredient = ingredientRepository.findByAliasKey(IngredientName.normalize(query))
        val brand = brandAliasRepository.findByIdOrNull(BrandName.key(query))?.brandId?.let { brandRepository.findByIdOrNull(it) }
        val cond = ProductSearchCondition(
            query,
            ingredient?.id,
            ingredient?.defaultUnit,
            ingredient?.suspectAbove(),
            ingredient?.comparableUnits().orEmpty(),
            brand?.id,
        )
        if (!cond.searchable) {
            throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail("q", "${ProductSearchCondition.TEXT_MIN_LENGTH}자 이상 입력해 주세요.")))
        }
        val searchSort = parseSort(sort)
        if (searchSort == ProductSearchSort.AMOUNT && ingredient == null) {
            throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail("sort", "함량순은 성분 이름으로 검색할 때만 쓸 수 있습니다.")))
        }

        val rows = searchRepository.search(cond, searchSort, page.toLong() * size, size)
        val brands = brandRepository.refsOf(rows.map { it.brandId })
        return ProductSearchResponse(
            totalCount = searchRepository.count(cond),
            matchedIngredient = ingredient?.toMatchedIngredient(),
            matchedBrand = brand?.toRef(),
            items = rows.map { it.toItem(ingredient, it.brandId?.let(brands::get)) },
        )
    }

    // API 값은 소문자(relevance, latest, amount)다.
    private fun parseSort(sort: String) = ProductSearchSort.entries.find { it.name.lowercase() == sort }
        ?: throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail("sort", "relevance, latest, amount 중 하나여야 합니다.")))
}
