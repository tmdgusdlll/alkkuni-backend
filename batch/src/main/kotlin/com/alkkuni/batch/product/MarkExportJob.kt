package com.alkkuni.batch.product

import com.alkkuni.batch.JobLock
import com.alkkuni.core.product.ExportName
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * DB에 있는 제품의 수출용 여부(export_only)를 지금 판정 규칙(ExportName)으로 다시 맞춘다. 식약처 API를 부르지 않고, 몇 번 돌려도 같다.
 * 제품명(약 4.6만)을 한 번 읽어 메모리에서 판정한 뒤, 바뀐 제품만 UPDATE 한 번으로 반영한다.
 */
@Service
class ExportMarker(private val jdbc: JdbcTemplate) {
    data class Result(val changed: Int, val target: Long, val exportOnly: Long)

    @Transactional
    fun mark(): Result {
        val exportIds = jdbc.query("SELECT id, name FROM product") { rs, _ -> rs.getLong(1) to rs.getString(2) }
            .filter { (_, name) -> ExportName.isExportOnly(name) }
            .map { it.first }
            .toTypedArray()
        val changed = jdbc.update(
            "UPDATE product SET export_only = (id = ANY(?)) WHERE export_only IS DISTINCT FROM (id = ANY(?))",
            exportIds, exportIds,
        )
        // 비율은 비교 대상인 원료 제외 제품 기준이다.
        val (target, exportOnly) = jdbc.queryForObject(
            "SELECT count(*), count(*) FILTER (WHERE export_only) FROM product WHERE NOT raw_material AND status = 'ACTIVE'",
        ) { rs, _ -> rs.getLong(1) to rs.getLong(2) }!!
        return Result(changed, target, exportOnly)
    }
}

/**
 * 수출용 판정 규칙을 바꾸거나 V14를 처음 적용한 뒤 기존 제품을 다시 판정한다. 새로 적재·갱신되는 제품은 Product가 이름을 정하면서 판정한다.
 * 수집 작업도 제품을 쓰므로 같은 잠금(I0030)으로 겹치지 않게 한다.
 * `alkkuni.job=mark-export`로 띄우면 한 번 돌고 끝난다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "mark-export")
class MarkExportJob(
    private val marker: ExportMarker,
    private val jobLock: JobLock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val result = jobLock.withLock(InitialLoadJob.SERVICE) { marker.mark() }
        log.info("수출용 판정 완료: 바뀐 제품 {}건, 원료 제외 제품 {}건 중 수출용 {}건", result.changed, result.target, result.exportOnly)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MarkExportJob::class.java)
    }
}
