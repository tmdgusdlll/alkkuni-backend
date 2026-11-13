package com.alkkuni.core.product.repository

import com.alkkuni.core.product.entity.Product
import com.alkkuni.core.product.entity.ProductIngredient
import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.product.enums.ProductStatus
import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.entity.Entity
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
 * units(Ingredient.comparableUnits) 중 한 단위로 suspectAbove(상한 2배)를 넘는 행은 오류 의심이라 함량순 정렬에 쓰지 않는다. null이면 상한이 없다.
 */
data class ExploreBase(
    val ingredientId: Long,
    val defaultUnit: String?,
    val suspectAbove: BigDecimal? = null,
    val units: List<String> = emptyList(),
)

/**
 * "기준 충족" 필터의 성분 하나. units(Ingredient.comparableUnits) 중 한 단위의 1일 함량이 amount 이상인 행이 있어야 한다.
 * suspectAbove(Ingredient.suspectAbove, 상한 2배)를 넘는 행은 표기·파싱 오류로 보고 충족 근거로 쓰지 않는다. null이면 상한이 없다.
 */
data class ExploreReference(
    val ingredientId: Long,
    val amount: BigDecimal,
    val units: List<String>,
    val suspectAbove: BigDecimal?,
)

/**
 * 성분별 제품 목록 조건. ingredientIds는 모두 든(AND) 제품, excludeIds는 하나라도 든 제품을 뺀다.
 * forms·brandIds가 비어 있으면 제형·브랜드를 가리지 않는다(고르면 그중 하나). references가 비어 있으면 기준 충족으로 거르지 않는다.
 */
data class ProductExploreCondition(
    val ingredientIds: List<Long>,
    val excludeIds: List<Long>,
    val forms: List<String>,
    val base: ExploreBase,
    val min: BigDecimal?,
    val max: BigDecimal?,
    val references: List<ExploreReference> = emptyList(),
    val brandIds: List<Long> = emptyList(),
    val market: ProductMarket = ProductMarket.ALL,
)

data class ProductExploreRow(
    val id: Long,
    val name: String,
    val manufacturer: String?,
    val form: String?,
    val reportedOn: LocalDate?,
    val basisCheck: BasisCheck?,
    val parseStatus: ParseStatus,
    val brandId: Long?,
    val exportOnly: Boolean,
)

/**
 * 성분별 제품 목록. 선택 조건이 많고 EXISTS·NOT EXISTS가 반복돼 Kotlin JDSL로 만든다(PLANNING.md "검색·목록·비교 화면").
 * 원료 품목과 삭제된 제품은 뺀다.
 *
 * 함량순 정렬 키는 검색 API(ProductSearchRepository)와 같다: 주 단위 값 → 다른 단위(단위 내림차순) 값 → 모르는 값.
 * 오류 의심 값(상한 2배 초과)은 정렬 키에서 빼서 모르는 값처럼 다룬다. 범위·기준 충족 필터와 응답 값은 그대로다.
 * 같은 성분 행이 여러 개면 정렬에는 가장 큰 값을 쓰고, 응답에는 합치지 않고 모두 내보낸다(PLANNING.md "함량 표시 규칙").
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
                    path(Product::brandId),
                    path(Product::exportOnly),
                ).from(entity(Product::class))
                    .whereAnd(*predicates(cond))
                    .orderBy(
                        // 수출용 제품(V14)은 어느 정렬이든 국내 제품 뒤다.
                        path(Product::exportOnly).asc(),
                        *when (sort) {
                            ProductExploreSort.AMOUNT -> amountOrder(cond.base)
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

    // 한 페이지 제품의 고른 성분 행. (제품 id, 성분 id) → 원문 성분표 순서의 행 목록. 행이 없으면 키가 없다.
    fun amounts(productIds: Collection<Long>, ingredientIds: Collection<Long>): Map<Pair<Long, Long>, List<ProductIngredient>> {
        if (productIds.isEmpty()) return emptyMap()
        return productIngredientRepository.findAllByProductIdInAndIngredientIdInOrderById(productIds, ingredientIds)
            .groupBy { it.productId to requireNotNull(it.ingredientId) }
    }

    private fun Jpql.predicates(cond: ProductExploreCondition): Array<Predicate?> = arrayOf(
        path(Product::status).eq(ProductStatus.ACTIVE),
        path(Product::rawMaterial).eq(false),
        // 서브쿼리 별칭을 성분마다 다르게 둔다. 같은 별칭이 겹치면 상관 관계가 엉킬 수 있다.
        // 기준 충족이 켜지면 같은 서브쿼리에 기준 조건을 더한다(포함 EXISTS를 따로 두지 않는다).
        // basisCheck는 보지 않는다. "표시 기준량" 제품을 빼면 원료 제외 제품의 77%가 사라진다(PROGRESS.md).
        *cond.ingredientIds.mapIndexed { i, id ->
            val ref = cond.references.find { it.ingredientId == id }
            exists(hasIngredient("inc$i", ref) { it.eq(id) })
        }.toTypedArray(),
        cond.excludeIds.takeIf { it.isNotEmpty() }?.let { ids -> notExists(hasIngredient("exc") { it.`in`(ids) }) },
        cond.forms.takeIf { it.isNotEmpty() }?.let { path(Product::form).`in`(it) },
        cond.brandIds.takeIf { it.isNotEmpty() }?.let { path(Product::brandId).`in`(it) },
        when (cond.market) {
            ProductMarket.ALL -> null
            ProductMarket.DOMESTIC -> path(Product::exportOnly).eq(false)
            ProductMarket.EXPORT -> path(Product::exportOnly).eq(true)
        },
        // 범위는 주 단위 값에만 건다. 둘 다 있으면 서브쿼리 하나(between)로 계산한다.
        when {
            cond.min != null && cond.max != null -> mainAmount(cond.base, "rng").between(cond.min, cond.max)
            cond.min != null -> mainAmount(cond.base, "rng").ge(cond.min)
            cond.max != null -> mainAmount(cond.base, "rng").le(cond.max)
            else -> null
        },
    )

    // ref가 있으면 비교 가능한 단위이고 1일 함량이 기준값 이상(상한 2배 이하)인 행만 센다. 같은 성분 행이 여러 개면 하나만 맞아도 된다.
    private fun Jpql.hasIngredient(
        alias: String,
        ref: ExploreReference? = null,
        ingredientMatch: (Path<Long>) -> Predicate,
    ): Subquery<Long> {
        val pi = entity(ProductIngredient::class, alias)
        return select(pi(ProductIngredient::id))
            .from(pi)
            .whereAnd(
                pi(ProductIngredient::productId).eq(path(Product::id)),
                ingredientMatch(pi(ProductIngredient::ingredientId)),
                ref?.let { pi(ProductIngredient::dailyUnit).`in`(it.units) },
                ref?.let { pi(ProductIngredient::dailyAmount).ge(it.amount) },
                ref?.suspectAbove?.let { pi(ProductIngredient::dailyAmount).le(it) },
            )
            .asSubquery()
    }

    /**
     * 함량순 정렬 키. 주 단위가 없으면(대부분의 성분) 단위를 가리지 않고 최댓값 하나로 정렬한다.
     * 주 단위가 있으면 주 단위 최댓값 → 다른 단위의 단위(내림차순, 비타민 A면 mg RE 묶음 다음 mg 묶음) → 다른 단위 최댓값 순이다.
     * 단위가 다른 값끼리 숫자로 비교되지 않게 묶음을 나눈다.
     */
    private fun Jpql.amountOrder(base: ExploreBase) = if (base.defaultUnit == null) {
        arrayOf(mainAmount(base, "amain", forSort = true).desc().nullsLast())
    } else {
        arrayOf(
            mainAmount(base, "amain", forSort = true).desc().nullsLast(),
            otherUnit(base, "ounit").desc().nullsLast(),
            // ponytail: 한 제품에 다른 단위가 두 종류 이상이면(예: mg RE와 mg 행이 같이 있음) 이 최댓값은 단위를 섞는다. 로컬에 드물어 그대로 둔다.
            otherAmount(base, "oamt").desc().nullsLast(),
        )
    }

    // 기준 성분의 주 단위 행(주 단위가 없으면 모든 행) 중 1일 함량 최댓값.
    // forSort면 오류 의심 행을 뺀다. 범위 필터는 지금처럼 모든 행을 본다.
    private fun Jpql.mainAmount(base: ExploreBase, alias: String, forSort: Boolean = false): Subquery<BigDecimal> {
        val pi = entity(ProductIngredient::class, alias)
        return select(max(pi(ProductIngredient::dailyAmount)))
            .from(pi)
            .whereAnd(
                pi(ProductIngredient::productId).eq(path(Product::id)),
                pi(ProductIngredient::ingredientId).eq(base.ingredientId),
                base.defaultUnit?.let { pi(ProductIngredient::dailyUnit).eq(it) },
                if (forSort) notSuspect(pi, base) else null,
            )
            .asSubquery()
    }

    // 오류 의심(비교 가능한 단위이고 상한 2배 초과)이 아닌 행. 상한이 없으면 조건을 걸지 않는다.
    private fun Jpql.notSuspect(pi: Entity<ProductIngredient>, base: ExploreBase): Predicate? =
        base.suspectAbove?.let {
            or(pi(ProductIngredient::dailyUnit).notIn(base.units), pi(ProductIngredient::dailyAmount).le(it))
        }

    // 기준 성분의 주 단위가 아닌, 함량을 아는 행의 단위(최댓값)와 함량(최댓값). 주 단위가 있을 때만 부른다.
    private fun Jpql.otherUnit(base: ExploreBase, alias: String): Subquery<String> {
        val pi = entity(ProductIngredient::class, alias)
        return select(max(pi(ProductIngredient::dailyUnit))).from(pi).whereAnd(*otherRows(pi, base)).asSubquery()
    }

    private fun Jpql.otherAmount(base: ExploreBase, alias: String): Subquery<BigDecimal> {
        val pi = entity(ProductIngredient::class, alias)
        return select(max(pi(ProductIngredient::dailyAmount))).from(pi).whereAnd(*otherRows(pi, base)).asSubquery()
    }

    private fun Jpql.otherRows(pi: Entity<ProductIngredient>, base: ExploreBase) = arrayOf(
        pi(ProductIngredient::productId).eq(path(Product::id)),
        pi(ProductIngredient::ingredientId).eq(base.ingredientId),
        pi(ProductIngredient::dailyUnit).ne(requireNotNull(base.defaultUnit)),
        pi(ProductIngredient::dailyAmount).isNotNull(),
        notSuspect(pi, base),
    )
}
