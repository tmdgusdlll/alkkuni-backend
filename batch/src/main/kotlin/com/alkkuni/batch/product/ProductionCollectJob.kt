package com.alkkuni.batch.product

import com.alkkuni.batch.JobLock
import com.alkkuni.batch.JobRun
import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.raw.RawStore
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

/**
 * I0310 생산실적에서 공개된 최신 연도 하나를 통째로 받아 제품에 붙인다(판매 추정 1차, PLANNING.md "검색 결과 표시·노출 순서" 4).
 *
 * 연도 고르기: 작년 실적이 있으면 작년, 없으면(INFO-200) 재작년. 작년 실적은 다음 해 초에 올라오는데 날짜를 몰라서
 * 매달 cron으로 돌려 공개되면 넘어가게 한다. 공개 직후 덜 찬 상태를 피하려고 작년 행 수가 재작년의 절반 미만이면 넘어가지 않는다.
 *
 * 연도 전체 약 4.6만 행, 1000건씩 47번 호출(2026-11-21 실측 약 1분 30초). 연도를 고르는 데 2번 더 부른다.
 * CHNG_DT 없는 호출이라 시간당 100회 한도(INFO-320)에 걸리면 클라이언트가 멈춘다.
 *
 * 받은 신고번호 수가 total_count보다 적으면 반영하지 않고 실패한다. 덜 받은 목록으로 반영하면 빠진 제품의 생산량이 지워진다.
 *
 * `alkkuni.job=production`으로 띄우면 한 번 돌고 끝난다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "production")
class ProductionCollectJob(
    private val client: FoodSafetyClient,
    private val rawStore: RawStore,
    private val applier: ProductionApplier,
    private val jobLock: JobLock,
    private val clock: Clock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) = jobLock.withLock(SERVICE) { collect(it) }

    private fun collect(run: JobRun) {
        val collectedAt = LocalDateTime.now(clock)
        val year = pickYear(collectedAt.year - 1)
        val quantities = HashMap<String, Long>()

        val totalCount = client.forEachPage(SERVICE, params = yearParam(year)) { pageNo, page ->
            rawStore.save(SERVICE, collectedAt, "${year}_%03d.json".format(pageNo), page.rawBody)
            for (node in page.rows) {
                val reportNo = requireNotNull(node.path("PRDLST_REPORT_NO").asString("").trim().ifEmpty { null }) {
                    "$SERVICE PRDLST_REPORT_NO가 비어 있다"
                }
                val qty = requireNotNull(node.path("PRDCTN_QY").asString("").trim().toLongOrNull()) {
                    "$SERVICE $reportNo PRDCTN_QY를 숫자로 읽을 수 없다"
                }
                // 한 해에 같은 번호가 두 번 온 적은 없다(2026-11-21, 2025년 전체). 오면 큰 값을 쓴다.
                quantities.merge(reportNo, qty, ::maxOf)
            }
            run.processed = quantities.size
        }
        check(quantities.size >= totalCount) {
            "$SERVICE ${year}년 받은 신고번호 ${quantities.size}건이 total_count ${totalCount}건보다 적다. 반영하지 않는다."
        }

        val result = applier.apply(year, quantities)
        run.changed = result.matched + result.cleared
        log.info(
            "{} 생산실적 반영 완료: {}년 {}건 받음, 제품에 붙음 {}건, 비움 {}건, 판매 추정 {}건",
            SERVICE, year, quantities.size, result.matched, result.cleared, result.estimated,
        )
    }

    private fun pickYear(lastYear: Int): Int {
        val last = count(lastYear)
        val before = count(lastYear - 1)
        return when {
            last == 0 -> lastYear - 1
            last * 2 < before -> {
                log.warn("{} {}년 실적이 {}건으로 {}년({}건)의 절반 미만이다. 덜 찬 것으로 보고 {}년을 쓴다", SERVICE, lastYear, last, lastYear - 1, before, lastYear - 1)
                lastYear - 1
            }
            else -> lastYear
        }.also { check(it == lastYear || before > 0) { "$SERVICE ${lastYear - 1}년 생산실적도 없다" } }
    }

    private fun count(year: Int) = client.fetchPage(SERVICE, 1, 1, params = yearParam(year)).totalCount

    private fun yearParam(year: Int) = mapOf("EVL_YR" to year.toString())

    companion object {
        const val SERVICE = "I0310"
        private val log = LoggerFactory.getLogger(ProductionCollectJob::class.java)
    }
}
