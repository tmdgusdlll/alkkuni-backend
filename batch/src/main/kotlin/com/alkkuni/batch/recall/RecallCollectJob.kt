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
 * "목록에 없으면 빠짐"으로 판단하므로, 덜 받은 목록으로 빠짐 처리를 하면 멀쩡한 회수가 빠짐으로 바뀐다.
 * 반대로 받은 건은 덜 받았어도 목록에 있다는 게 확실하다. 그래서 다 받지 못한 날(일련번호 수 ≠ total_count)에도
 * 새 건 저장·갱신은 하고 빠짐 처리만 건너뛴 뒤, 실패로 끝내 알람이 울리게 한다.
 * 덜 받은 날 전부 멈추면 그 안에 있던 새 회수도 저장되지 않아 알림이 늦어진다.
 *
 * [RecallSnapshotApplier.apply]가 자기 트랜잭션으로 먼저 커밋하고, 그 뒤 마지막 check가 실패한다.
 * 이 클래스(run·collect)에 `@Transactional`을 걸면 그 실패가 저장까지 롤백하므로 걸지 않는다.
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
        var skipped = 0

        val totalCount = client.forEachPage(SERVICE) { pageNo, page ->
            rawStore.save(SERVICE, collectedAt, "list_%03d.json".format(pageNo), page.rawBody)
            for (node in page.rows) {
                // 깨진 행 하나 때문에 나머지 회수까지 버리지 않는다. 건너뛴 행만큼 아래 건수가 모자라 빠짐 처리는 하지 않는다.
                try {
                    rows += I0490Row.from(node)
                } catch (e: IllegalArgumentException) {
                    skipped++
                    log.warn("{} 행을 건너뜀: {}", SERVICE, e.message)
                }
            }
        }

        val seqCount = rows.distinctBy { it.sourceSeq }.size
        // 식약처가 덜 보낸 건지, 다 왔는데 깨진 행을 우리가 버린 건지 실패 메시지만으로 구분되게 한다.
        val skippedNote = if (skipped > 0) "(깨진 행 ${skipped}건 건너뜀)" else ""
        val incomplete = if (seqCount != totalCount) {
            "받은 일련번호 ${seqCount}건이 total_count ${totalCount}건과 다르다$skippedNote"
        } else {
            null
        }
        val result = applier.apply(rows, collectedAt, incomplete)
        log.info(
            "{} 회수 수집 완료: {}건 받음, 새 회수 {}건, 목록에서 빠짐 {}건, 다시 올라옴 {}건",
            SERVICE, seqCount, result.created, result.delisted, result.relisted,
        )
        // 새 건·갱신은 이미 반영했다. 빠짐 처리를 건너뛴 날은 실패로 끝내 다음 실행 전에 사람이 보게 한다.
        check(result.delistSkipped == null) { "$SERVICE 빠짐 처리를 건너뛰었다: ${result.delistSkipped}" }
    }

    companion object {
        const val SERVICE = "I0490"
        private val log = LoggerFactory.getLogger(RecallCollectJob::class.java)
    }
}
