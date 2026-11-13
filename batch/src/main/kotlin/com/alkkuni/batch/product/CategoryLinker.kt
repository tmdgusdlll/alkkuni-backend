package com.alkkuni.batch.product

import com.alkkuni.core.product.FunctionalCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 제품의 기능성 카테고리(product_category)를 기능성 원문과 지금 판정 규칙(FunctionalCategory)으로 맞춘다.
 * 제품 엔티티와 따로 둔 테이블이라 JDBC로 쓴다.
 */
@Service
class CategoryLinker(private val jdbc: JdbcTemplate) {
    data class Result(val changed: Int, val target: Long, val categorized: Long)

    /** 적재 중 제품 하나를 다시 쓴다. ProductUpserter가 제품을 저장한 뒤 같은 트랜잭션에서 부른다. */
    fun relink(productId: Long, functionality: String?) {
        jdbc.update("DELETE FROM product_category WHERE product_id = ?", productId)
        jdbc.batchUpdate(
            "INSERT INTO product_category (product_id, category) VALUES (?, ?)",
            FunctionalCategory.of(functionality).map { arrayOf<Any>(productId, it.name) },
        )
    }

    /**
     * DB에 있는 모든 제품을 다시 판정한다. 식약처 API를 부르지 않고, 몇 번 돌려도 같다.
     * 원문(약 4.6만)을 한 번 읽어 메모리에서 판정한 뒤, 달라진 행만 지우고 넣는다.
     */
    @Transactional
    fun link(): Result {
        val rows = jdbc.query("SELECT id, functionality FROM product") { rs, _ -> rs.getLong(1) to rs.getString(2) }
            .flatMap { (id, text) -> FunctionalCategory.of(text).map { arrayOf<Any>(id, it.name) } }
        // 임시 테이블은 이 트랜잭션의 연결에만 보이고 커밋하면 사라진다(BrandLinker와 같은 방식).
        jdbc.execute("CREATE TEMP TABLE category_map (product_id BIGINT, category VARCHAR(30), PRIMARY KEY (product_id, category)) ON COMMIT DROP")
        jdbc.batchUpdate("INSERT INTO category_map VALUES (?, ?)", rows)
        val deleted = jdbc.update(
            """
            DELETE FROM product_category c
            WHERE NOT EXISTS (SELECT 1 FROM category_map m WHERE m.product_id = c.product_id AND m.category = c.category)
            """.trimIndent(),
        )
        val inserted = jdbc.update("INSERT INTO product_category (product_id, category) SELECT product_id, category FROM category_map ON CONFLICT DO NOTHING")
        // 비율은 비교 대상인 원료 제외 제품 기준이다.
        val (target, categorized) = jdbc.queryForObject(
            """
            SELECT count(*), count(*) FILTER (WHERE EXISTS (SELECT 1 FROM product_category c WHERE c.product_id = p.id))
            FROM product p WHERE NOT raw_material AND status = 'ACTIVE'
            """.trimIndent(),
        ) { rs, _ -> rs.getLong(1) to rs.getLong(2) }!!
        return Result(deleted + inserted, target, categorized)
    }
}
