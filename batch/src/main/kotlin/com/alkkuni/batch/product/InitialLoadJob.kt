package com.alkkuni.batch.product

import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.raw.RawStore
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * I0030 전체를 받아 원본 저장 → 파싱 → upsert 한다. 최초 1회와 장애 복구 때 수동으로 돌린다.
 * 실행: ./gradlew :batch:bootRun --args='--alkkuni.job=initial-load'
 *
 * 페이징 도중 행이 추가·삭제되면 경계의 행이 빠질 수 있다. 받은 신고번호 수가 total_count와 다르면 실패로 끝낸다.
 * upsert가 멱등이라 다시 돌리면 된다(47회 호출, 시간당 한도 100회 안).
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "initial-load")
class InitialLoadJob(
    private val client: FoodSafetyClient,
    private val rawStore: RawStore,
    private val upserter: ProductUpserter,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val collectedAt = LocalDateTime.now()
        val seen = HashSet<String>()
        var totalCount = 0
        var pageNo = 0
        var changed = 0

        client.forEachPage(SERVICE) { page ->
            pageNo++
            // 가공 전에 원본부터 남긴다. 파싱·저장이 실패해도 원본으로 다시 돌릴 수 있다.
            rawStore.save(SERVICE, collectedAt, "page_%03d.json".format(pageNo), page.rawBody)
            val rows = page.rows.map(I0030Row::from)
            changed += upserter.upsert(rows)
            rows.mapTo(seen) { it.reportNo }
            totalCount = page.totalCount
            log.info("{} {}페이지: 누적 {}/{}건", SERVICE, pageNo, seen.size, totalCount)
        }

        check(seen.size == totalCount) {
            "$SERVICE 받은 신고번호 ${seen.size}건이 total_count ${totalCount}건과 다르다. 도중에 데이터가 바뀌었을 수 있으니 다시 실행한다."
        }
        log.info("{} 초기 적재 완료: {}건, 새로 쓰거나 바뀐 제품 {}건", SERVICE, totalCount, changed)
    }

    companion object {
        const val SERVICE = "I0030"
        private val log = LoggerFactory.getLogger(InitialLoadJob::class.java)
    }
}
