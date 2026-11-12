package com.alkkuni.batch.brand

import com.alkkuni.core.brand.BrandMatcher
import com.alkkuni.core.brand.repository.BrandAliasRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 브랜드 사전(brand_alias, 수백 행)을 처음 쓸 때 한 번 읽어 둔다.
 * 배치는 한 번 돌고 끝나므로 실행 중 사전이 바뀌는 경우는 없다(IngredientDictionary와 같은 방식).
 */
@Component
class BrandDictionary(private val aliasRepository: BrandAliasRepository) {
    private val matcher: BrandMatcher by lazy {
        BrandMatcher(aliasRepository.findAll().map { BrandMatcher.Alias(it.aliasKey, it.brandId, it.wholeWord) })
    }

    fun find(productName: String): Long? = matcher.find(productName)
}

/**
 * DB에 있는 제품을 지금 브랜드 사전으로 다시 연결한다. 식약처 API를 부르지 않고, 몇 번 돌려도 결과가 같다.
 * 제품명(약 4.6만)을 한 번 읽어 메모리에서 맞춘 뒤, 바뀐 제품만 UPDATE 한 번으로 반영한다.
 */
@Service
class BrandLinker(
    private val dictionary: BrandDictionary,
    private val jdbc: JdbcTemplate,
) {
    data class Result(val relinked: Int, val target: Long, val linked: Long)

    @Transactional
    fun link(): Result {
        val products = jdbc.query("SELECT id, name FROM product") { rs, _ -> rs.getLong(1) to rs.getString(2) }
        // 임시 테이블은 이 트랜잭션의 연결에만 보이고 커밋하면 사라진다.
        jdbc.execute("CREATE TEMP TABLE brand_map (product_id BIGINT PRIMARY KEY, brand_id BIGINT) ON COMMIT DROP")
        jdbc.batchUpdate("INSERT INTO brand_map VALUES (?, ?)", products.map { (id, name) -> arrayOf<Any?>(id, dictionary.find(name)) })
        // 바뀐 제품만 쓴다. 사전에서 빠진 표기는 NULL로 돌아간다.
        val relinked = jdbc.update(
            """
            UPDATE product p SET brand_id = m.brand_id FROM brand_map m
            WHERE p.id = m.product_id AND p.brand_id IS DISTINCT FROM m.brand_id
            """.trimIndent(),
        )
        // 연결률은 비교 대상인 원료 제외 제품 기준이다.
        val (target, linked) = jdbc.queryForObject(
            "SELECT count(*), count(brand_id) FROM product WHERE NOT raw_material AND status = 'ACTIVE'",
        ) { rs, _ -> rs.getLong(1) to rs.getLong(2) }!!
        return Result(relinked, target, linked)
    }
}
