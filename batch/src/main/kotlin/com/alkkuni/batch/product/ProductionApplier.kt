package com.alkkuni.batch.product

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한 연도의 생산실적(신고번호 → 생산량)을 제품에 반영한다. 전체가 한 트랜잭션이다.
 * 받은 목록에 없는 제품은 연도·생산량을 비운다. 연도가 바뀌었을 때 옛 연도 값이 남지 않게 하려는 것이라,
 * 호출한 쪽이 그해 목록을 끝까지 받았을 때만 부른다.
 */
@Service
class ProductionApplier(private val jdbc: JdbcTemplate) {
    data class Result(val matched: Int, val cleared: Int, val estimated: Long)

    @Transactional
    fun apply(year: Int, quantities: Map<String, Long>): Result {
        jdbc.execute("CREATE TEMP TABLE production_in (report_no TEXT PRIMARY KEY, qty BIGINT NOT NULL) ON COMMIT DROP")
        jdbc.batchUpdate("INSERT INTO production_in VALUES (?, ?)", quantities.entries, 1000) { ps, e ->
            ps.setString(1, e.key)
            ps.setLong(2, e.value)
        }
        val matched = jdbc.update(
            """
            UPDATE product p SET production_year = ?, production_qty = i.qty
            FROM production_in i WHERE p.report_no = i.report_no
            """,
            year,
        )
        val cleared = jdbc.update(
            """
            UPDATE product SET production_year = NULL, production_qty = NULL
            WHERE production_year IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM production_in i WHERE i.report_no = product.report_no)
            """,
        )
        // 화면이 쓰는 기준(원료 제외, 수출용 제외)의 판매 추정 수. 로그로 전후를 비교한다.
        val estimated = jdbc.queryForObject(
            """
            SELECT count(*) FROM product
            WHERE production_qty > 0 AND NOT export_only AND NOT raw_material AND status = 'ACTIVE'
            """,
            Long::class.java,
        )!!
        return Result(matched, cleared, estimated)
    }
}
