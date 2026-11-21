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
 * 매달 cron으로 돌려 공개되면 넘어가게 한다. 반영하면 그해 목록에 없는 제품 값을 비우므로, 덜 찬 연도로 넘어가면
 * 아직 안 올라온 제품이 판매 추정에서 빠진다. 그래서 작년 행 수가 재작년의 90% 미만이면 넘어가지 않는다
 * (연도별 행 수가 2023 3.7만 → 2024 4.2만 → 2025 4.6만으로 늘고 있다. 공개가 한 번에 되는지 조금씩 채워지는지는 모른다).
 *
 * 연도 전체 약 4.6만 행, 1000건씩 47번 호출(2026-11-21 실측 약 1분 30초). 건수 확인에 3번 더 부른다(두 해, 다 받은 뒤 다시 세기).
 * CHNG_DT 없는 호출이라 시간당 100회 한도(INFO-320)에 걸리면 클라이언트가 멈춘다.
 *
 * 덜 받은 목록으로 반영하면 빠진 제품의 생산량이 지워진다. 그래서 아래 중 하나라도 맞으면 반영하지 않고 실패한다.
 * 실패해도 DB는 지난 실행 값 그대로다.
 * - 받은 행이 0건이거나, total_count나 연도를 고를 때 본 건수보다 적다(신고번호가 아니라 행으로 센다. 중복 번호는 큰 값으로 합친다)
 * - 다 받은 뒤 다시 센 건수가 처음과 다르다(수집 도중 데이터가 바뀌면 페이지 경계에서 행이 빠질 수 있다, FoodSafetyClient.forEachPage)
 * 생산량이 숫자가 아닌 행도 그해 전체를 실패로 둔다. 0으로 넣으면 틀린 판정이 조용히 나가므로 멈추는 편이 낫다.
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
        val (year, expected) = pickYear(collectedAt.year - 1)
        val quantities = HashMap<String, Long>()
        var received = 0

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
                received++
            }
            run.processed = received
        }
        val least = maxOf(totalCount, expected)
        check(received > 0 && received >= least) {
            "$SERVICE ${year}년 받은 행 ${received}건이 total_count ${least}건보다 적다. 반영하지 않는다."
        }
        val recount = count(year)
        check(recount == totalCount) {
            "$SERVICE ${year}년 다 받은 뒤 다시 세니 ${recount}건으로 처음(${totalCount}건)과 다르다. 도중에 바뀌어 행이 빠졌을 수 있어 반영하지 않는다."
        }

        val result = applier.apply(year, quantities)
        run.changed = result.updated + result.cleared
        log.info(
            "{} 생산실적 반영 완료: {}년 {}행 받음(신고번호 {}개), 값이 바뀐 제품 {}건, 비움 {}건, 판매 추정 {}건",
            SERVICE, year, received, quantities.size, result.updated, result.cleared, result.estimated,
        )
    }

    /** 받을 연도와 그때 본 건수. */
    private fun pickYear(lastYear: Int): Pair<Int, Int> {
        val last = count(lastYear)
        val before = count(lastYear - 1)
        if (last > 0 && last * 10L >= before * 9L) return lastYear to last
        if (last > 0) {
            log.warn("{} {}년 실적이 {}건으로 {}년({}건)의 90% 미만이다. 덜 찬 것으로 보고 {}년을 쓴다", SERVICE, lastYear, last, lastYear - 1, before, lastYear - 1)
        }
        check(before > 0) { "$SERVICE ${lastYear}년·${lastYear - 1}년 생산실적이 모두 없다" }
        return lastYear - 1 to before
    }

    private fun count(year: Int) = client.fetchPage(SERVICE, 1, 1, params = yearParam(year)).totalCount

    private fun yearParam(year: Int) = mapOf("EVL_YR" to year.toString())

    companion object {
        const val SERVICE = "I0310"
        private val log = LoggerFactory.getLogger(ProductionCollectJob::class.java)
    }
}
