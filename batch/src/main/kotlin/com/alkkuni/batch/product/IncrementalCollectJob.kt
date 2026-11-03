package com.alkkuni.batch.product

import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.raw.RawStore
import com.alkkuni.core.ingestion.repository.IngestionCursorRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매일 I0030에서 커서 이후 바뀐 제품만 받아 upsert 한다.
 *
 * - 조회 기준일은 `커서 - 1일`. 하루를 겹쳐 받아 지난 실행이 페이징 경계에서 놓친 행과
 *   수집 도중 바뀐 행을 다시 받는다. 겹친 행은 upsert가 해시·판정 버전으로 건너뛴다.
 * - 그래서 초기 적재와 달리 total_count 일관성 검사는 하지 않는다. 놓친 행은 다음 실행이 받는다.
 * - 삭제는 CHNG_DT로 잡지 못한다. 전체 대사(파일 방식)가 맡는다.
 *
 * `alkkuni.job=incremental`로 띄우면 한 번 수집하고 앱이 끝난다(ADR-009, 필요할 때만 뜨는 컨테이너).
 * 매일 실행은 앱 밖(EC2 cron)이 맡는다. 옵션 없이 띄운 앱에서 실수로 수집이 돌지 않게 matchIfMissing은 두지 않는다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "incremental")
class IncrementalCollectJob(
    private val client: FoodSafetyClient,
    private val rawStore: RawStore,
    private val upserter: ProductUpserter,
    private val cursorRepository: IngestionCursorRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        // 예외로 끝내야 프로세스가 0이 아닌 코드로 종료돼 cron 쪽에서 실패를 알 수 있다.
        val cursor = checkNotNull(cursorRepository.findByIdOrNull(SERVICE)) {
            "$SERVICE 커서가 없다. 초기 적재(alkkuni.job=initial-load)를 먼저 돌린다."
        }
        val collectedAt = LocalDateTime.now()
        val today = collectedAt.toLocalDate()
        val since = changedSince(cursor.lastCollectedDate, today)
        var pageNo = 0
        var received = 0
        var changed = 0

        client.forEachPage(SERVICE, since) { page ->
            pageNo++
            // 가공 전에 원본부터 남긴다(초기 적재와 같은 이유).
            rawStore.save(SERVICE, collectedAt, "changed_%03d.json".format(pageNo), page.rawBody)
            val rows = page.rows.map(I0030Row::from)
            changed += upserter.upsert(rows)
            received += rows.size
        }

        // 끝까지 성공했을 때만 옮긴다. 도중에 실패하면 커서가 그대로라 다음 실행이 같은 구간부터 다시 받는다.
        cursor.advance(today, LocalDateTime.now())
        cursorRepository.save(cursor)
        log.info("{} 증분 수집 완료: CHNG_DT={}, {}건 받음, 새로 쓰거나 바뀐 제품 {}건", SERVICE, since, received, changed)
    }

    companion object {
        const val SERVICE = InitialLoadJob.SERVICE
        private val log = LoggerFactory.getLogger(IncrementalCollectJob::class.java)

        /**
         * 하루 겹쳐 `커서 - 1일`부터 받되 어제를 넘지 않는다.
         * 오늘 날짜 CHNG_DT는 19시 전에 INFO-700(예외)이 온다.
         */
        fun changedSince(lastCollectedDate: LocalDate, today: LocalDate): LocalDate =
            minOf(lastCollectedDate.minusDays(1), today.minusDays(1))
    }
}
