package com.alkkuni.batch.product

import com.alkkuni.batch.JobLock
import com.alkkuni.core.product.FunctionalCategory
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
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
        val inserted = jdbc.update("INSERT INTO product_category SELECT * FROM category_map ON CONFLICT DO NOTHING")
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

/**
 * 카테고리 판정 규칙을 바꾸거나 V15를 처음 적용한 뒤 기존 제품을 다시 판정한다. 새로 적재·갱신되는 제품은 ProductUpserter가 쓴다.
 * 수집 작업도 제품을 쓰므로 같은 잠금(I0030)으로 겹치지 않게 한다.
 * `alkkuni.job=link-categories`로 띄우면 한 번 돌고 끝난다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "link-categories")
class LinkCategoriesJob(
    private val linker: CategoryLinker,
    private val jobLock: JobLock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val result = jobLock.withLock(InitialLoadJob.SERVICE) { linker.link() }
        log.info(
            "기능성 카테고리 판정 완료: 바뀐 행 {}건, 원료 제외 제품 {}건 중 {}건에 카테고리",
            result.changed, result.target, result.categorized,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(LinkCategoriesJob::class.java)
    }
}
