package com.alkkuni.batch.product

import com.alkkuni.batch.JobLock
import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.raw.RawStore
import com.alkkuni.core.ingestion.repository.IngestionCursorRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

/**
 * 매일 I0030에서 커서 이후 바뀐 제품만 받아 upsert 한다.
 *
 * - 조회 기준일은 `커서 - 1일`. 커서는 오늘을 넘지 않으므로 조회일은 항상 어제 이전이다
 *   (오늘 날짜 CHNG_DT는 19시 전에 INFO-700). 겹친 하루는 수집 도중이나 직후에 바뀐 행을 다시 받는다.
 *   겹친 행은 upsert가 해시·판정 버전으로 건너뛴다.
 * - 하루 겹침만으로는 페이징 경계에서 빠진 행이 다 돌아오지 않는다(조회 시작일 날짜의 행은 다음 실행 범위 밖).
 *   그래서 초기 적재와 같이 total_count가 페이지마다 같은지(forEachPage), 받은 신고번호 수가 total_count보다 적지 않은지 확인하고,
 *   다르면 커서를 옮기지 않고 실패로 끝낸다. 다음 실행이 같은 구간부터 다시 받는다.
 * - 삭제는 CHNG_DT로 잡지 못한다. 전체 대사(파일 방식)가 맡는다.
 *
 * `alkkuni.job=incremental`로 띄우면 한 번 수집하고 앱이 끝난다(ADR-009, 필요할 때만 뜨는 컨테이너).
 * 매일 실행은 앱 밖(EC2 cron)이 맡는다. 옵션 없이 띄운 앱에서 실수로 수집이 돌지 않게 matchIfMissing은 두지 않는다.
 * 초기 적재와 같은 잠금 키를 써서 둘이 겹쳐 돌지 않는다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "incremental")
class IncrementalCollectJob(
    private val client: FoodSafetyClient,
    private val rawStore: RawStore,
    private val upserter: ProductUpserter,
    private val cursorRepository: IngestionCursorRepository,
    private val jobLock: JobLock,
    private val clock: Clock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) = jobLock.withLock(SERVICE) { collect() }

    private fun collect() {
        // 예외로 끝내야 프로세스가 0이 아닌 코드로 종료돼 cron 쪽에서 실패를 알 수 있다.
        val cursor = checkNotNull(cursorRepository.findByIdOrNull(SERVICE)) {
            "$SERVICE 커서가 없다. 초기 적재(alkkuni.job=initial-load)를 먼저 돌린다."
        }
        val collectedAt = LocalDateTime.now(clock)
        val today = collectedAt.toLocalDate()
        // 커서가 오늘보다 뒤면 시계나 시간대가 어긋난 것이다. API를 부르기 전에 멈춘다.
        check(!cursor.lastCollectedDate.isAfter(today)) {
            "$SERVICE 커서(${cursor.lastCollectedDate})가 오늘($today)보다 뒤다. 시간대 설정을 확인한다."
        }
        val since = cursor.lastCollectedDate.minusDays(1)
        val seen = HashSet<String>()
        var changed = 0

        // 도중에 total_count가 바뀌면 forEachPage가 예외로 멈춘다. 커서는 옮기지 않으므로 다음 실행이 다시 받는다.
        val totalCount = client.forEachPage(SERVICE, since) { pageNo, page ->
            // 가공 전에 원본부터 남긴다(초기 적재와 같은 이유).
            rawStore.save(SERVICE, collectedAt, "changed_%03d.json".format(pageNo), page.rawBody)
            val rows = page.rows.map(I0030Row::from)
            changed += upserter.upsert(rows)
            rows.mapTo(seen) { it.reportNo }
        }

        // 덜 받은 것만 실패로 본다. 더 받은 것은 total_count 갱신 지연이다(InitialLoadJob과 같은 이유).
        check(seen.size >= totalCount) {
            "$SERVICE CHNG_DT=$since 받은 신고번호 ${seen.size}건이 total_count ${totalCount}건보다 적다. 커서를 옮기지 않고 다음 실행이 다시 받는다."
        }
        if (seen.size > totalCount) {
            log.warn("{} CHNG_DT={} 받은 신고번호 {}건이 total_count {}건보다 많다(total_count 갱신 지연)", SERVICE, since, seen.size, totalCount)
        }
        // 끝까지 성공했을 때만 옮긴다. 도중에 실패하면 커서가 그대로라 다음 실행이 같은 구간부터 다시 받는다.
        cursor.advance(today, LocalDateTime.now(clock))
        cursorRepository.save(cursor)
        log.info("{} 증분 수집 완료: CHNG_DT={}, {}건 받음, 새로 쓰거나 바뀐 제품 {}건", SERVICE, since, seen.size, changed)
    }

    companion object {
        const val SERVICE = InitialLoadJob.SERVICE
        private val log = LoggerFactory.getLogger(IncrementalCollectJob::class.java)
    }
}
