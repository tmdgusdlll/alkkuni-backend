package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.api.common.ErrorDetail
import com.alkkuni.api.brand.refsOf
import com.alkkuni.api.category.refsOf
import com.alkkuni.core.brand.repository.BrandRepository
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.FunctionalCategory
import com.alkkuni.core.product.repository.ExploreBase
import com.alkkuni.core.product.repository.ExploreReference
import com.alkkuni.core.product.repository.ProductCategoryRepository
import com.alkkuni.core.product.repository.ProductExploreCondition
import com.alkkuni.core.product.repository.ProductExploreRepository
import com.alkkuni.core.product.repository.ProductExploreSort
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ProductExploreService(
    private val exploreRepository: ProductExploreRepository,
    private val ingredientRepository: IngredientRepository,
    private val brandRepository: BrandRepository,
    private val productCategoryRepository: ProductCategoryRepository,
) {

    /**
     * 고른 성분이 모두 든 제품 목록. 성분이나 기능성 카테고리 중 하나는 있어야 한다.
     * - category: 그 기능성 카테고리 제품만(카테고리 목록 API의 code). 성분 없이 카테고리만 고르면 "기능성으로 찾기"다.
     *   이때는 기준 성분이 없어 함량순·함량 범위·기준 충족을 거절하고, 기본 정렬이 최신순이다.
     * - sort: amount(첫 성분 기준), amount:{성분 id}, latest. 없으면 성분이 있을 때 amount, 없을 때 latest.
     *   함량 범위(min·max)는 기준 성분(amount:{id}의 성분, 없으면 첫 성분)에 건다.
     * - 목록 값("ingredient=5,")의 빈 원소는 null로 바인딩된다. 비교 API처럼 Long?로 받아 거절한다.
     * - meetsReference: 고른 성분 모두 1일 함량이 기준값 이상인 제품만. 기준값이 없는 성분이 있으면 거절한다
     *   (조용히 무시하면 확인하지 않은 성분까지 "충족"으로 보인다).
     * - brand: 고른 브랜드 중 하나인 제품만. 없는 브랜드 id는 거절한다(오타로 빈 결과가 "제품 없음"처럼 보이지 않게).
     */
    fun explore(
        rawIngredientIds: List<Long?>,
        rawCategory: String?,
        rawExcludeIds: List<Long?>,
        forms: List<String>,
        rawBrandIds: List<Long?>,
        min: BigDecimal?,
        max: BigDecimal?,
        meetsReference: Boolean,
        market: String,
        sort: String?,
        page: Int,
        size: Int,
    ): ProductExploreResponse {
        val ingredientIds = validIds(rawIngredientIds, "ingredient", 0)
        // 빈 값(category=·sort=)은 고르지 않은 것으로 본다. 프론트가 칩을 해제하면 빈 값을 보낼 수 있고,
        // sort는 예전 defaultValue가 빈 값을 기본값으로 바꿔 주던 동작을 지킨다.
        val category = rawCategory?.trim()?.ifEmpty { null }?.let { c -> FunctionalCategory.entries.find { it.name == c } ?: invalid("category", "없는 카테고리입니다: $c") }
        if (ingredientIds.isEmpty()) {
            if (category == null) invalid("ingredient", "성분이나 카테고리를 골라 주세요.")
            if (min != null || max != null) invalid(if (min != null) "min" else "max", "함량 범위는 성분을 골라야 쓸 수 있습니다.")
            if (meetsReference) invalid("meetsReference", "기준 충족은 성분을 골라야 쓸 수 있습니다.")
        }
        val excludeIds = validIds(rawExcludeIds, "exclude", 0)
        if (ingredientIds.any { it in excludeIds }) invalid("exclude", "고른 성분은 제외할 수 없습니다.")
        if (min != null && max != null && min > max) invalid("min", "최솟값이 최댓값보다 클 수 없습니다.")
        val cleanForms = forms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanForms.size > MAX_FORMS) invalid("form", "제형은 ${MAX_FORMS}개까지 고를 수 있습니다.")
        val brandIds = rawBrandIds.filterNotNull()
        if (brandIds.size != rawBrandIds.size || brandIds.size > MAX_BRANDS || brandIds.distinct().size != brandIds.size) {
            invalid("brand", "서로 다른 브랜드를 ${MAX_BRANDS}개까지 골라 주세요.")
        }
        if (brandIds.isNotEmpty() && brandRepository.countByIdIn(brandIds) != brandIds.size.toLong()) invalid("brand", "없는 브랜드가 있습니다.")

        val found = ingredientRepository.findAllById(ingredientIds + excludeIds).associateBy { requireNotNull(it.id) }
        (ingredientIds + excludeIds).firstOrNull { it !in found }?.let { invalid(if (it in ingredientIds) "ingredient" else "exclude", "없는 성분입니다: $it") }
        val ingredients = ingredientIds.map { found.getValue(it) }

        val (searchSort, baseId) = parseSort(sort?.trim()?.ifEmpty { null } ?: if (ingredientIds.isEmpty()) "latest" else "amount", ingredientIds)
        val base = ingredients.find { it.id == baseId }?.let { ExploreBase(requireNotNull(it.id), it.defaultUnit, it.suspectAbove(), it.comparableUnits()) }
        val references = if (meetsReference) {
            ingredients.map { ing ->
                val amount = ing.referenceAmount ?: invalid("meetsReference", "기준값이 없는 성분입니다: ${ing.displayName}")
                ExploreReference(requireNotNull(ing.id), amount, ing.comparableUnits(), ing.suspectAbove())
            }
        } else {
            emptyList()
        }
        val cond = ProductExploreCondition(ingredientIds, excludeIds, cleanForms, base, min, max, references, brandIds, parseMarket(market), category)

        val result = exploreRepository.find(cond, searchSort, page, size)
        val amounts = exploreRepository.amounts(result.content.map { it.id }, ingredientIds)
        val brands = brandRepository.refsOf(result.content.map { it.brandId })
        val categories = productCategoryRepository.refsOf(result.content.map { it.id })
        return ProductExploreResponse(
            totalCount = result.totalElements,
            ingredients = ingredients.map { it.toExploreIngredient() },
            baseIngredientId = baseId,
            items = result.content.map { row ->
                row.toExploreItem(row.brandId?.let(brands::get), categories[row.id].orEmpty(), ingredientIds.map { id -> ExploreItemAmount(id, amounts[row.id to id].orEmpty().map { it.toCompareAmount(found.getValue(id)) }) })
            },
        )
    }

    private fun validIds(raw: List<Long?>, field: String, minCount: Int): List<Long> {
        val ids = raw.filterNotNull()
        if (ids.size != raw.size || ids.size !in minCount..MAX_IDS || ids.distinct().size != ids.size) {
            invalid(field, "서로 다른 성분을 ${if (minCount > 0) "${minCount}~" else ""}${MAX_IDS}개${if (minCount > 0) "" else "까지"} 골라 주세요.")
        }
        return ids
    }

    // API 값은 소문자다. amount:{id}의 id는 고른 성분 중 하나여야 한다. 기준 성분은 고른 성분이 없으면 null이다.
    private fun parseSort(sort: String, ingredientIds: List<Long>): Pair<ProductExploreSort, Long?> {
        val parts = sort.split(":", limit = 2)
        val type = when (parts[0]) {
            "amount" -> ProductExploreSort.AMOUNT
            "latest" -> if (parts.size == 1) ProductExploreSort.LATEST else null
            else -> null
        } ?: invalid("sort", "amount, amount:{성분 id}, latest 중 하나여야 합니다.")
        val baseId = parts.getOrNull(1)?.let { s -> s.toLongOrNull()?.takeIf { it in ingredientIds } ?: invalid("sort", "함량순 기준은 고른 성분 중 하나여야 합니다.") }
        if (type == ProductExploreSort.AMOUNT && ingredientIds.isEmpty()) invalid("sort", "함량순은 성분을 골라야 쓸 수 있습니다.")
        return type to (baseId ?: ingredientIds.firstOrNull())
    }

    private fun invalid(field: String, reason: String): Nothing =
        throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail(field, reason)))

    companion object {
        // 고른 성분·제외 성분 각각의 최대 개수. 성분마다 EXISTS가 하나씩 붙는다.
        private const val MAX_IDS = 5

        // 제형은 IN 하나라 비용과 상관없다. 제형 코드(로컬 14종)를 모두 골라도 되게 두고, 터무니없이 긴 목록만 막는다.
        private const val MAX_FORMS = 20

        // 브랜드도 IN 하나다. 제형처럼 긴 목록만 막는다.
        private const val MAX_BRANDS = 20
    }
}
