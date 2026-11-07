package com.alkkuni.core.product.repository

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
 * 검색 조건. textSearch면 제품명·제조사(부분 일치, 유사도), ingredientId가 있으면 그 성분이 든 제품도 찾는다.
 * 둘 중 하나는 있어야 한다.
 */
data class ProductSearchCondition(
    val q: String,
    val textSearch: Boolean,
    val ingredientId: Long?,
) {
    init {
        require(textSearch || ingredientId != null) { "검색 대상이 없음" }
    }
}

// 검색 결과 한 줄. dailyAmount·dailyUnit은 검색어가 성분일 때 그 성분의 1일 함량(모르면 null).
data class ProductSearchRow(
    val id: Long,
    val name: String,
    val manufacturer: String?,
    val form: String?,
    val reportedOn: LocalDate?,
    val dailyAmount: BigDecimal?,
    val dailyUnit: String?,
)

/**
 * 제품 검색. pg_trgm 유사도(`<%`, word_similarity)는 JPQL로 쓸 수 없어 네이티브 SQL로 둔다.
 * 원료 품목과 삭제된 제품은 뺀다.
 */
@Repository
class ProductSearchRepository(private val jdbc: JdbcClient) {

    fun search(cond: ProductSearchCondition, sort: ProductSearchSort, offset: Long, limit: Int): List<ProductSearchRow> {
        require(sort != ProductSearchSort.AMOUNT || cond.ingredientId != null) { "함량순은 성분 검색에서만" }
        // ponytail: 같은 제품에 같은 성분 행이 여러 개면(멀티팩 등 505쌍) 가장 큰 값 하나만 쓴다. 비교 API에서 합산 규칙을 정하면 맞춘다.
        // ponytail: 한 성분 안에서도 단위가 섞여 있다(엽산 mg DFE·mg, 비타민 A mg RAE·mg RE). 함량순은 단위를 보지 않고 숫자로만 정렬한다.
        val amount = if (cond.ingredientId != null) {
            """
            LEFT JOIN LATERAL (
                SELECT pi.daily_amount, pi.daily_unit FROM product_ingredient pi
                WHERE pi.product_id = p.id AND pi.ingredient_id = :ingredientId
                ORDER BY pi.daily_amount DESC NULLS LAST LIMIT 1
            ) a ON true
            """
        } else {
            "LEFT JOIN (SELECT NULL::numeric AS daily_amount, NULL::varchar AS daily_unit) a ON true"
        }
        val order = when (sort) {
            ProductSearchSort.RELEVANCE -> "hit.text_match DESC, word_similarity(:q, p.name) DESC, p.reported_on DESC NULLS LAST"
            ProductSearchSort.LATEST -> "p.reported_on DESC NULLS LAST"
            ProductSearchSort.AMOUNT -> "a.daily_amount DESC NULLS LAST"
        }
        val sql = """
            ${hits(cond)}
            SELECT p.id, p.name, p.manufacturer, p.form, p.reported_on, a.daily_amount, a.daily_unit
            FROM hit JOIN product p ON p.id = hit.id
            $amount
            ORDER BY $order, p.id DESC
            LIMIT :limit OFFSET :offset
        """
        return params(jdbc.sql(sql), cond)
            .param("limit", limit)
            .param("offset", offset)
            .query { rs, _ -> rs.toRow() }
            .list()
    }

    fun count(cond: ProductSearchCondition): Long =
        params(jdbc.sql("${hits(cond)} SELECT count(*) FROM hit"), cond).query(Long::class.java).single()

    // 검색에 걸린 제품 id와, 제품명·제조사로 걸렸는지(text_match). 성분으로도 걸리면 한 줄로 합친다.
    private fun hits(cond: ProductSearchCondition): String {
        val sources = listOfNotNull(
            if (cond.textSearch) {
                // 부분 일치(ILIKE)는 "고함량아연"처럼 단어 중간도 찾고, <%는 "비타민씨" → "비타민C" 같은 오타를 허용한다.
                // 3자 미만은 trigram이 모자라 오타 허용이 의미 없고 <%가 전체 스캔 비용만 4배로 늘린다(로컬 실측 32ms → 122ms).
                val fuzzy = if (cond.q.count { !it.isWhitespace() } >= FUZZY_MIN_LENGTH) " OR :q <% name" else ""
                "SELECT id, true AS text_match FROM product WHERE name ILIKE :pattern OR manufacturer ILIKE :pattern$fuzzy"
            } else {
                null
            },
            cond.ingredientId?.let { "SELECT product_id AS id, false AS text_match FROM product_ingredient WHERE ingredient_id = :ingredientId" },
        )
        return """
            WITH hit AS (
                SELECT p.id, bool_or(h.text_match) AS text_match
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
    )

    companion object {
        private const val FUZZY_MIN_LENGTH = 3
    }
}
