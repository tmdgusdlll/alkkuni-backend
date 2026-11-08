package com.alkkuni.core.product.repository

import com.alkkuni.core.product.entity.Product
import com.alkkuni.core.product.entity.ProductIngredient
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.product.enums.ProductStatus
import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.expression.Subquery
import com.linecorp.kotlinjdsl.querymodel.jpql.path.Path
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

enum class ProductExploreSort {
    // 기준 성분의 1일 함량 높은순(주 단위 행 먼저, 다른 단위 행, 모르는 값 순)
    AMOUNT,
    // 신고일 최신순
    LATEST,
}

/**
 * 기준 성분. 함량순 정렬과 함량 범위(min·max)가 이 성분에 걸린다.
 * defaultUnit(Ingredient.defaultUnit)이 있으면 그 단위 행만 범위·1차 정렬 대상이다. 없으면 단위를 가리지 않는다.
 */
data class ExploreBase(
    val ingredientId: Long,
    val defaultUnit: String?,
)

/**
 * 성분별 제품 목록 조건. ingredientIds는 모두 든(AND) 제품, excludeIds는 하나라도 든 제품을 뺀다.
 * forms가 비어 있으면 제형을 가리지 않는다.
 */
data class ProductExploreCondition(
    val ingredientIds: List<Long>,
    val excludeIds: List<Long>,
    val forms: List<String>,
    val base: ExploreBase,
    val min: BigDecimal?,
    val max: BigDecimal?,
)

data class ProductExploreRow(
    val id: Long,
    val name: String,
    val manufacturer: String?,
    val form: String?,
    val reportedOn: LocalDate?,
    val basisCheck: BasisCheck?,
    val parseStatus: ParseStatus,
)

// 제품 하나의 성분 하나 함량. 같은 성분 행이 여러 개면 pickAmount 규칙으로 하나를 고른다.
data class ExploreAmount(
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
)

/**
 * 성분별 제품 목록. 선택 조건이 많고 EXISTS·NOT EXISTS가 반복돼 Kotlin JDSL로 만든다(PLANNING.md "검색·목록·비교 화면").
 * 원료 품목과 삭제된 제품은 뺀다.
 *
 * "같은 성분 행이 여러 개면 가장 큰 값 하나"(검색 API와 같은 규칙)를 주 단위 → 다른 단위 순으로 적용한다.
 * 정렬(JPQL 서브쿼리)과 응답 함량(pickAmount)이 같은 규칙을 쓰므로 둘을 이 파일에 둔다.
 */
@Repository
class ProductExploreRepository(
    private val productRepository: ProductRepository,
    private val productIngredientRepository: ProductIngredientRepository,
) {

    fun find(cond: ProductExploreCondition, sort: ProductExploreSort, page: Int, size: Int): Page<ProductExploreRow> =
        productRepository.findPage(
            PageRequest.of(page, size),
            {
                selectNew<ProductExploreRow>(
                    path(Product::id),
                    path(Product::name),
                    path(Product::manufacturer),
                    path(Product::form),
                    path(Product::reportedOn),
                    path(Product::basisCheck),
                    path(Product::parseStatus),
                ).from(entity(Product::class))
                    .whereAnd(*predicates(cond))
                    .orderBy(
                        *when (sort) {
                            ProductExploreSort.AMOUNT -> arrayOf(
                                baseAmount(cond.base, mainUnit = true).desc().nullsLast(),
                                baseAmount(cond.base, mainUnit = false).desc().nullsLast(),
                            )
                            ProductExploreSort.LATEST -> arrayOf(path(Product::reportedOn).desc().nullsLast())
                        },
                        path(Product::id).desc(),
                    )
            },
            {
                // 개수 쿼리에는 정렬 서브쿼리가 필요 없다.
                select(count(path(Product::id))).from(entity(Product::class)).whereAnd(*predicates(cond))
            },
        )

    // 한 페이지 제품의 고른 성분 함량. (제품 id, 성분 id) → 함량. 행이 없으면 키가 없다.
    fun amounts(productIds: Collection<Long>, bases: List<ExploreBase>): Map<Pair<Long, Long>, ExploreAmount> {
        if (productIds.isEmpty()) return emptyMap()
        val units = bases.associate { it.ingredientId to it.defaultUnit }
        return productIngredientRepository.findAllByProductIdInAndIngredientIdIn(productIds, units.keys)
            .groupBy { it.productId to requireNotNull(it.ingredientId) }
            .mapValues { (key, rows) -> pickAmount(rows, units[key.second]) }
    }

    private fun Jpql.predicates(cond: ProductExploreCondition): Array<Predicate?> = arrayOf(
        path(Product::status).eq(ProductStatus.ACTIVE),
        path(Product::rawMaterial).eq(false),
        // 서브쿼리 별칭을 성분마다 다르게 둔다. 같은 별칭이 겹치면 상관 관계가 엉킬 수 있다.
        *cond.ingredientIds.mapIndexed { i, id -> exists(hasIngredient("inc$i") { it.eq(id) }) }.toTypedArray(),
        cond.excludeIds.takeIf { it.isNotEmpty() }?.let { ids -> notExists(hasIngredient("exc") { it.`in`(ids) }) },
        cond.forms.takeIf { it.isNotEmpty() }?.let { path(Product::form).`in`(it) },
        cond.min?.let { baseAmount(cond.base, mainUnit = true, alias = "rmin").ge(it) },
        cond.max?.let { baseAmount(cond.base, mainUnit = true, alias = "rmax").le(it) },
    )

    private fun Jpql.hasIngredient(alias: String, ingredientMatch: (Path<Long>) -> Predicate): Subquery<Long> {
        val pi = entity(ProductIngredient::class, alias)
        return select(pi(ProductIngredient::id))
            .from(pi)
            .where(and(pi(ProductIngredient::productId).eq(path(Product::id)), ingredientMatch(pi(ProductIngredient::ingredientId))))
            .asSubquery()
    }

    /**
     * 기준 성분의 1일 함량(같은 성분 행이 여러 개면 최댓값).
     * mainUnit이면 주 단위 행(주 단위가 없으면 모든 행), 아니면 주 단위가 아닌 행만 본다(주 단위가 없으면 항상 null).
     * 함량순은 주 단위 값 → 다른 단위 값 순으로 정렬해, 단위가 다른 값끼리 섞여 비교되지 않게 한다.
     */
    private fun Jpql.baseAmount(base: ExploreBase, mainUnit: Boolean, alias: String = if (mainUnit) "amain" else "aother"): Subquery<BigDecimal> {
        val pi = entity(ProductIngredient::class, alias)
        val unit = base.defaultUnit
        val unitMatch = when {
            unit == null && mainUnit -> null
            // 주 단위가 없으면 "다른 단위" 묶음도 없다. 항상 거짓인 조건을 둔다.
            unit == null -> pi(ProductIngredient::id).isNull()
            mainUnit -> pi(ProductIngredient::dailyUnit).eq(unit)
            else -> pi(ProductIngredient::dailyUnit).ne(unit)
        }
        return select(max(pi(ProductIngredient::dailyAmount)))
            .from(pi)
            .whereAnd(
                pi(ProductIngredient::productId).eq(path(Product::id)),
                pi(ProductIngredient::ingredientId).eq(base.ingredientId),
                unitMatch,
            )
            .asSubquery()
    }

    companion object {
        // 주 단위 행의 최댓값, 없으면 다른 단위 행의 최댓값, 둘 다 없으면 단위만 있는 null 함량. baseAmount와 같은 규칙이다.
        fun pickAmount(rows: List<ProductIngredient>, defaultUnit: String?): ExploreAmount {
            val known = rows.filter { it.dailyAmount != null }
            val main = known.filter { defaultUnit == null || it.dailyUnit == defaultUnit }
            val best = (main.ifEmpty { known }).maxByOrNull { it.dailyAmount!! }
            return ExploreAmount(best?.dailyAmount, best?.dailyUnit)
        }
    }
}
