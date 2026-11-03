package com.alkkuni.batch.recall

import com.alkkuni.batch.JobLock
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
 * I0490 회수·판매중지 목록 전체(약 400건)를 받아 이전 스냅샷과 비교한다(PLANNING "회수 수집").
 *
 * "목록에 없으면 빠짐"으로 판단하므로, 덜 받은 목록을 반영하면 멀쩡한 회수가 빠짐으로 바뀐다.
 * 그래서 다 받았는지(일련번호 수 = total_count) 확인한 뒤에만 반영하고, 반영 단계에서 급감도 한 번 더 막는다.
 *
 * `alkkuni.job=recall`로 띄우면 한 번 돌고 끝난다. 매일 실행은 EC2 cron이 맡는다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "recall")
class RecallCollectJob(
    private val client: FoodSafetyClient,
    private val rawStore: RawStore,
    private val applier: RecallSnapshotApplier,
    private val jobLock: JobLock,
    private val clock: Clock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) = jobLock.withLock(SERVICE) { collect() }

    private fun collect() {
        val collectedAt = LocalDateTime.now(clock)
        val rows = mutableListOf<I0490Row>()

        val totalCount = client.forEachPage(SERVICE) { pageNo, page ->
            rawStore.save(SERVICE, collectedAt, "list_%03d.json".format(pageNo), page.rawBody)
            page.rows.mapTo(rows, I0490Row::from)
        }

        val seqCount = rows.distinctBy { it.sourceSeq }.size
        check(seqCount == totalCount) {
            "$SERVICE 받은 일련번호 ${seqCount}건이 total_count ${totalCount}건과 다르다. 반영하지 않는다."
        }
        val result = applier.apply(rows, collectedAt)
        log.info(
            "{} 회수 수집 완료: {}건 받음, 새 회수 {}건, 목록에서 빠짐 {}건, 다시 올라옴 {}건",
            SERVICE, seqCount, result.created, result.delisted, result.relisted,
        )
    }

    companion object {
        const val SERVICE = "I0490"
        private val log = LoggerFactory.getLogger(RecallCollectJob::class.java)
    }
}
