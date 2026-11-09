package com.alkkuni.api.product

import com.alkkuni.api.common.ApiException
import com.alkkuni.api.common.ErrorCode
import com.alkkuni.api.common.ErrorDetail
import com.alkkuni.core.ingredient.repository.IngredientRepository
import com.alkkuni.core.product.repository.ExploreBase
import com.alkkuni.core.product.repository.ExploreReference
import com.alkkuni.core.product.repository.ProductExploreCondition
import com.alkkuni.core.product.repository.ProductExploreRepository
import com.alkkuni.core.product.repository.ProductExploreSort
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ProductExploreService(
    private val exploreRepository: ProductExploreRepository,
    private val ingredientRepository: IngredientRepository,
) {

    /**
     * 고른 성분이 모두 든 제품 목록.
     * - sort: amount(첫 성분 기준), amount:{성분 id}, latest. 함량 범위(min·max)는 기준 성분(amount:{id}의 성분, 없으면 첫 성분)에 건다.
     * - 목록 값("ingredient=5,")의 빈 원소는 null로 바인딩된다. 비교 API처럼 Long?로 받아 거절한다.
     * - meetsReference: 고른 성분 모두 1일 함량이 기준값 이상인 제품만. 기준값이 없는 성분이 있으면 거절한다
     *   (조용히 무시하면 확인하지 않은 성분까지 "충족"으로 보인다).
     */
    fun explore(
        rawIngredientIds: List<Long?>,
        rawExcludeIds: List<Long?>,
        forms: List<String>,
        min: BigDecimal?,
        max: BigDecimal?,
        meetsReference: Boolean,
        sort: String,
        page: Int,
        size: Int,
    ): ProductExploreResponse {
        val ingredientIds = validIds(rawIngredientIds, "ingredient", 1)
        val excludeIds = validIds(rawExcludeIds, "exclude", 0)
        if (ingredientIds.any { it in excludeIds }) invalid("exclude", "고른 성분은 제외할 수 없습니다.")
        if (min != null && max != null && min > max) invalid("min", "최솟값이 최댓값보다 클 수 없습니다.")
        val cleanForms = forms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanForms.size > MAX_FORMS) invalid("form", "제형은 ${MAX_FORMS}개까지 고를 수 있습니다.")

        val found = ingredientRepository.findAllById(ingredientIds + excludeIds).associateBy { requireNotNull(it.id) }
        (ingredientIds + excludeIds).firstOrNull { it !in found }?.let { invalid(if (it in ingredientIds) "ingredient" else "exclude", "없는 성분입니다: $it") }
        val ingredients = ingredientIds.map { found.getValue(it) }

        val (searchSort, baseId) = parseSort(sort, ingredientIds)
        val bases = ingredients.map { ExploreBase(requireNotNull(it.id), it.defaultUnit) }
        val base = bases.first { it.ingredientId == baseId }
        val references = if (meetsReference) {
            ingredients.map { ing ->
                val amount = ing.referenceAmount ?: invalid("meetsReference", "기준값이 없는 성분입니다: ${ing.displayName}")
                ExploreReference(requireNotNull(ing.id), amount, ing.comparableUnits())
            }
        } else {
            emptyList()
        }
        val cond = ProductExploreCondition(ingredientIds, excludeIds, cleanForms, base, min, max, references)

        val result = exploreRepository.find(cond, searchSort, page, size)
        val amounts = exploreRepository.amounts(result.content.map { it.id }, ingredientIds)
        return ProductExploreResponse(
            totalCount = result.totalElements,
            ingredients = ingredients.map { it.toExploreIngredient() },
            baseIngredientId = baseId,
            items = result.content.map { row ->
                row.toExploreItem(ingredientIds.map { id -> ExploreItemAmount(id, amounts[row.id to id].orEmpty().map { it.toCompareAmount() }) })
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

    // API 값은 소문자다. amount:{id}의 id는 고른 성분 중 하나여야 한다.
    private fun parseSort(sort: String, ingredientIds: List<Long>): Pair<ProductExploreSort, Long> {
        val parts = sort.split(":", limit = 2)
        val type = when (parts[0]) {
            "amount" -> ProductExploreSort.AMOUNT
            "latest" -> if (parts.size == 1) ProductExploreSort.LATEST else null
            else -> null
        } ?: invalid("sort", "amount, amount:{성분 id}, latest 중 하나여야 합니다.")
        val baseId = parts.getOrNull(1)?.let { s -> s.toLongOrNull()?.takeIf { it in ingredientIds } ?: invalid("sort", "함량순 기준은 고른 성분 중 하나여야 합니다.") }
        return type to (baseId ?: ingredientIds.first())
    }

    private fun invalid(field: String, reason: String): Nothing =
        throw ApiException(ErrorCode.INVALID_INPUT, listOf(ErrorDetail(field, reason)))

    companion object {
        // 고른 성분·제외 성분 각각의 최대 개수. 성분마다 EXISTS가 하나씩 붙는다.
        private const val MAX_IDS = 5

        // 제형은 IN 하나라 비용과 상관없다. 제형 코드(로컬 14종)를 모두 골라도 되게 두고, 터무니없이 긴 목록만 막는다.
        private const val MAX_FORMS = 20
    }
}
