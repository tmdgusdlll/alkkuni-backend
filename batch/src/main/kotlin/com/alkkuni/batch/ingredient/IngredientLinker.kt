package com.alkkuni.batch.ingredient

import com.alkkuni.core.ingredient.IngredientName
import com.alkkuni.core.parser.AmountUnit
import com.alkkuni.core.product.repository.ProductIngredientRepository
import com.alkkuni.core.product.repository.ProductRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * DB에 있는 성분 행을 지금 사전으로 다시 연결한다. 식약처 API를 부르지 않고, 몇 번 돌려도 결과가 같다.
 * 성분명 종류(약 4,000)가 행(약 20만)보다 훨씬 적어 이름별로 대표 성분을 정한 뒤 UPDATE 한 번으로 반영한다.
 */
@Service
class IngredientLinker(
    private val dictionary: IngredientDictionary,
    private val jdbc: JdbcTemplate,
    private val productRepository: ProductRepository,
    private val productIngredientRepository: ProductIngredientRepository,
) {
    data class Result(
        val relinkedRows: Int,
        val recalculatedIuRows: Int,
        val targetRows: Long,
        val linkedRows: Long,
        val topUnlinked: List<Pair<String, Long>>,
    )

    @Transactional
    fun link(): Result {
        val names = jdbc.queryForList("SELECT DISTINCT raw_name FROM product_ingredient", String::class.java).filterNotNull()
        // 임시 테이블은 이 트랜잭션의 연결에만 보이고 커밋하면 사라진다.
        jdbc.execute("CREATE TEMP TABLE link_map (raw_name TEXT PRIMARY KEY, ingredient_id BIGINT) ON COMMIT DROP")
        jdbc.batchUpdate(
            "INSERT INTO link_map VALUES (?, ?)",
            names.map { arrayOf<Any?>(it, dictionary.find(it)?.id) },
        )
        // 바뀐 행만 쓴다. 사전에서 빠진 키는 NULL로 돌아간다.
        val relinked = jdbc.update(
            """
            UPDATE product_ingredient pi SET ingredient_id = m.ingredient_id
            FROM link_map m
            WHERE pi.raw_name = m.raw_name AND pi.ingredient_id IS DISTINCT FROM m.ingredient_id
            """.trimIndent(),
        )
        return Result(relinked, recalculateIu(), countTarget(), countLinked(), topUnlinked())
    }

    // IU 함량의 1일 함량은 연결된 성분의 환산 배율에 달려 있어 연결이 바뀌면 다시 계산한다(로컬 21행).
    private fun recalculateIu(): Int {
        val iuUnits = jdbc.queryForList("SELECT DISTINCT unit FROM product_ingredient", String::class.java)
            .filterNotNull().filter { AmountUnit.isIu(it) }
        if (iuUnits.isEmpty()) return 0
        val rows = productIngredientRepository.findAllByUnitIn(iuUnits)
        val products = productRepository.findAllById(rows.map { it.productId }.toSet()).associateBy { it.id!! }
        for (row in rows) {
            val iuPerMg = dictionary.find(row.rawName)?.iuPerMg
            row.updateDailyAmount(products.getValue(row.productId).dailyAmount(row.toParsedAmount(), iuPerMg))
        }
        return rows.size
    }

    // 연결률은 비교 대상인 원료 제외 제품의 성분 행 기준이다.
    private fun countTarget(): Long = jdbc.queryForObject(
        "SELECT count(*) FROM product_ingredient pi JOIN product p ON p.id = pi.product_id WHERE NOT p.raw_material",
        Long::class.java,
    )!!

    private fun countLinked(): Long = jdbc.queryForObject(
        """
        SELECT count(*) FROM product_ingredient pi JOIN product p ON p.id = pi.product_id
        WHERE NOT p.raw_material AND pi.ingredient_id IS NOT NULL
        """.trimIndent(),
        Long::class.java,
    )!!

    // 사전에 넣을 후보를 보려고 연결 안 된 행을 키별로 모은다.
    private fun topUnlinked(): List<Pair<String, Long>> =
        jdbc.query(
            """
            SELECT pi.raw_name, count(*) FROM product_ingredient pi JOIN product p ON p.id = pi.product_id
            WHERE NOT p.raw_material AND pi.ingredient_id IS NULL GROUP BY pi.raw_name
            """.trimIndent(),
        ) { rs, _ -> IngredientName.normalize(rs.getString(1)) to rs.getLong(2) }
            .groupBy({ it.first }, { it.second })
            .map { (key, counts) -> key to counts.sum() }
            .sortedByDescending { it.second }
            .take(TOP_UNLINKED)

    companion object {
        private const val TOP_UNLINKED = 20
    }
}
