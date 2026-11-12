package com.alkkuni.core.product.repository

import com.alkkuni.core.product.enums.BasisCheck
import com.alkkuni.core.product.enums.ParseStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate

enum class ProductSearchSort {
    // 제품명·제조사 일치 먼저, 그다음 제품명 유사도
    RELEVANCE,
    // 신고일 최신순
    LATEST,
    // 검색어가 성분일 때만. 그 성분의 1일 함량 높은순
    AMOUNT,
}

/**
 * 검색 조건. textSearch면 제품명·제조사(부분 일치, 유사도), ingredientId가 있으면 그 성분이 든 제품,
 * brandId가 있으면 그 브랜드 제품도 찾는다. 셋 중 하나는 있어야 한다(searchable). defaultUnit은 그 성분의 주 단위(Ingredient.defaultUnit)다.
 * suspectAbove(Ingredient.suspectAbove, 상한 2배)를 넘는 comparableUnits(Ingredient.comparableUnits) 값은 오류 의심이라 함량순 정렬에 쓰지 않는다. null이면 상한이 없다.
 */
data class ProductSearchCondition(
    val q: String,
    val ingredientId: Long?,
    val defaultUnit: String? = null,
    val suspectAbove: BigDecimal? = null,
    val comparableUnits: List<String> = emptyList(),
    val brandId: Long? = null,
) {
    // 공백을 뺀 글자 수. 길이 기준은 여기 한 곳에서만 정한다.
    private val length = q.count { !it.isWhitespace() }

    // 2자 이상이면 제품명·제조사도 찾는다. 1자 부분 일치는 결과가 너무 넓다.
    val textSearch = length >= TEXT_MIN_LENGTH

    // 3자 미만은 trigram이 모자라 오타 허용이 의미 없고 <%가 전체 스캔 비용만 4배로 늘린다(로컬 실측 32ms → 122ms).
    val fuzzy = length >= FUZZY_MIN_LENGTH

    val searchable = textSearch || ingredientId != null || brandId != null

    companion object {
        const val TEXT_MIN_LENGTH = 2
        private const val FUZZY_MIN_LENGTH = 3
    }
}

// 검색 결과 한 줄. dailyAmount·dailyUnit은 검색어가 성분일 때 그 성분의 1일 함량(모르면 null).
// basisCheck는 그 함량이 하루치로 확인됐는지, parseStatus는 검수 대기(NEEDS_REVIEW)인지를 화면에 알리려고 함께 낸다.
data class ProductSearchRow(
    val id: Long,
    val name: String,
    val manufacturer: String?,
    val form: String?,
    val reportedOn: LocalDate?,
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
    val basisCheck: BasisCheck?,
    val parseStatus: ParseStatus,
    val brandId: Long?,
)

/**
 * 제품 검색. pg_trgm 유사도(`<%`, word_similarity)는 JPQL로 쓸 수 없어 네이티브 SQL로 둔다.
 * 원료 품목과 삭제된 제품은 뺀다.
 */
@Repository
class ProductSearchRepository(private val jdbc: JdbcClient) {

    fun search(cond: ProductSearchCondition, sort: ProductSearchSort, offset: Long, limit: Int): List<ProductSearchRow> {
        require(sort != ProductSearchSort.AMOUNT || cond.ingredientId != null) { "함량순은 성분 검색에서만" }
        // ponytail: 같은 제품에 같은 성분 행이 여러 개면(멀티팩 등 505쌍) 가장 큰 값 하나로 정렬한다. 멀티팩은 실제보다 낮다. 파서가 구성품 구간을 나누면 합산으로 바꾼다(PLANNING.md "함량 표시 규칙").
        // 주 단위가 있는 성분(엽산·비타민 A 등)은 단위가 다른 값끼리 숫자로 비교하지 않는다.
        // 행 선택과 함량순 모두 주 단위 값 → 다른 단위(단위 내림차순) 값 → 모르는 값 순이다. 성분별 목록(ProductExploreRepository)과 같은 규칙이다.
        val unitOrder = { unit: String ->
            if (cond.defaultUnit != null) "($unit = :defaultUnit) DESC NULLS LAST, $unit DESC NULLS LAST, " else ""
        }
        // 오류 의심 값(상한 2배 초과)은 정렬에서 모르는 값처럼 다룬다. 응답 값은 그대로 낸다.
        // ponytail: 고른 행이 의심 값이면 같은 제품의 다른 정상 행이 있어도 뒤로 간다(성분별 목록은 정상 행으로 정렬). 드물어서 행 선택은 그대로 둔다.
        val suspect = if (cond.suspectAbove != null) "a.daily_unit IN (:comparableUnits) AND a.daily_amount > :suspectAbove" else "false"
        val sortKey = { column: String -> "CASE WHEN $suspect THEN NULL ELSE $column END" }
        val amount = if (cond.ingredientId != null) {
            """
            LEFT JOIN LATERAL (
                SELECT pi.daily_amount, pi.daily_unit FROM product_ingredient pi
                WHERE pi.product_id = p.id AND pi.ingredient_id = :ingredientId
                ORDER BY ${unitOrder("pi.daily_unit")}pi.daily_amount DESC NULLS LAST LIMIT 1
            ) a ON true
            """
        } else {
            "LEFT JOIN (SELECT NULL::numeric AS daily_amount, NULL::varchar AS daily_unit) a ON true"
        }
        val order = when (sort) {
            ProductSearchSort.RELEVANCE -> "${brandFirst(cond)}p.text_match DESC, ${similarity(cond)} DESC, p.reported_on DESC NULLS LAST"
            ProductSearchSort.LATEST -> "p.reported_on DESC NULLS LAST"
            ProductSearchSort.AMOUNT -> "${unitOrder(sortKey("a.daily_unit"))}${sortKey("a.daily_amount")} DESC NULLS LAST"
        }
        val sql = """
            ${hits(cond)}
            SELECT p.id, p.name, p.manufacturer, p.form, p.reported_on, p.basis_check, p.parse_status, p.brand_id, a.daily_amount, a.daily_unit
            FROM hit p
            $amount
            -- 수출용 제품(V14)은 어느 정렬이든 국내 제품 뒤다.
            ORDER BY p.export_only, $order, p.id DESC
            LIMIT :limit OFFSET :offset
        """
        return params(jdbc.sql(sql), cond)
            .param("limit", limit)
            .param("offset", offset)
            .query { rs, _ -> rs.toRow() }
            .list()
    }

    // 검색어가 브랜드면 그 브랜드 제품을 맨 앞에 두고, 그 안에서는 제품명 유사도를 보지 않는다(같은 값 → 최신순).
    // 제품명에 브랜드 이름이 없는 제품("ATOMY …")은 유사도가 0에 가까워, 유사도로 정렬하면 브랜드 제품 중 맨 뒤로 밀린다
    // (로컬 "애터미" 347개 중 107개가 241위 이후).
    private fun brandFirst(cond: ProductSearchCondition) = if (cond.brandId != null) "(p.brand_id = :brandId) DESC NULLS LAST, " else ""

    private fun similarity(cond: ProductSearchCondition) =
        if (cond.brandId != null) "CASE WHEN p.brand_id = :brandId THEN 1 ELSE word_similarity(:q, p.name) END" else "word_similarity(:q, p.name)"

    fun count(cond: ProductSearchCondition): Long =
        params(jdbc.sql("${hits(cond)} SELECT count(*) FROM hit"), cond).query(Long::class.java).single()

    // 검색에 걸린 제품과, 제품명·제조사·브랜드로 걸렸는지(text_match). 여러 경로로 걸리면 한 줄로 합친다.
    // 브랜드는 제품명에 브랜드 이름이 없는 제품("ATOMY …" ↔ 애터미)을 찾으려고 더한다. 관련도순에서는 브랜드 제품이 먼저다(brandFirst).
    // 상태 필터에 product JOIN이 필요하므로, 그때 응답 칼럼도 같이 가져와 본 쿼리에서 product를 다시 JOIN하지 않는다.
    private fun hits(cond: ProductSearchCondition): String {
        require(cond.searchable) { "검색 대상이 없음" }
        val sources = listOfNotNull(
            if (cond.textSearch) {
                // 부분 일치(ILIKE)는 "고함량아연"처럼 단어 중간도 찾고, <%는 "비타민씨" → "비타민C" 같은 오타를 허용한다.
                val fuzzy = if (cond.fuzzy) " OR :q <% name" else ""
                "SELECT id, true AS text_match FROM product WHERE name ILIKE :pattern OR manufacturer ILIKE :pattern$fuzzy"
            } else {
                null
            },
            cond.ingredientId?.let { "SELECT product_id AS id, false AS text_match FROM product_ingredient WHERE ingredient_id = :ingredientId" },
            cond.brandId?.let { "SELECT id, true AS text_match FROM product WHERE brand_id = :brandId" },
        )
        return """
            WITH hit AS (
                SELECT p.id, p.name, p.manufacturer, p.form, p.reported_on, p.basis_check, p.parse_status, p.brand_id, p.export_only,
                       bool_or(h.text_match) AS text_match
                FROM (${sources.joinToString(" UNION ALL ")}) h
                JOIN product p ON p.id = h.id
                WHERE p.status = 'ACTIVE' AND NOT p.raw_material
                GROUP BY p.id
            )
        """
    }

    // SQL에 없는 이름의 파라미터는 무시되므로 조건과 상관없이 모두 넘긴다.
    private fun params(spec: JdbcClient.StatementSpec, cond: ProductSearchCondition) = spec
        .param("q", cond.q)
        .param("pattern", "%${escapeLike(cond.q)}%")
        .param("ingredientId", cond.ingredientId)
        .param("brandId", cond.brandId)
        .param("defaultUnit", cond.defaultUnit)
        .param("suspectAbove", cond.suspectAbove)
        .param("comparableUnits", cond.comparableUnits)

    // LIKE의 와일드카드(%, _)와 이스케이프 문자(\, PostgreSQL 기본값)를 글자 그대로 찾게 한다.
    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun ResultSet.toRow() = ProductSearchRow(
        id = getLong("id"),
        name = getString("name"),
        manufacturer = getString("manufacturer"),
        form = getString("form"),
        reportedOn = getObject("reported_on", LocalDate::class.java),
        dailyAmount = getBigDecimal("daily_amount"),
        dailyUnit = getString("daily_unit"),
        basisCheck = getString("basis_check")?.let(BasisCheck::valueOf),
        parseStatus = ParseStatus.valueOf(getString("parse_status")),
        brandId = getObject("brand_id", Long::class.javaObjectType),
    )
}
