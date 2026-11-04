package com.alkkuni.batch.product

import com.alkkuni.batch.JobLock
import com.alkkuni.batch.foodsafety.FoodSafetyClient
import com.alkkuni.batch.raw.RawStore
import com.alkkuni.core.ingestion.entity.IngestionCursor
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
 * I0030 전체를 받아 원본 저장 → 파싱 → upsert 한다. 최초 1회와 장애 복구 때 수동으로 돌린다.
 * 실행: ./gradlew :batch:bootRun --args='--alkkuni.job=initial-load'
 *
 * 페이징 도중 행이 추가·삭제되면 위치가 밀려 경계의 행이 빠질 수 있다. 그래서 둘 다 확인하고 실패로 끝낸다.
 * - 페이지마다 total_count가 첫 페이지와 같은지. 받은 범위에서 하나 삭제되고 개수가 함께 줄면 끝의 비교만으로는 못 잡는다.
 * - 끝에 받은 신고번호 수가 total_count보다 적지 않은지. 많은 건 경고만 남긴다(total_count 갱신 지연).
 * upsert가 멱등이라 다시 돌리면 된다(47회 호출, 시간당 한도 100회 안).
 * 증분 수집과 같은 잠금 키를 써서 둘이 겹쳐 돌지 않는다.
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "initial-load")
class InitialLoadJob(
    private val client: FoodSafetyClient,
    private val rawStore: RawStore,
    private val upserter: ProductUpserter,
    private val cursorRepository: IngestionCursorRepository,
    private val jobLock: JobLock,
    private val clock: Clock,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) = jobLock.withLock(SERVICE) { load() }

    private fun load() {
        val collectedAt = LocalDateTime.now(clock)
        val seen = HashSet<String>()
        var changed = 0

        val totalCount = client.forEachPage(SERVICE) { pageNo, page ->
            // 가공 전에 원본부터 남긴다. 파싱·저장이 실패해도 원본으로 다시 돌릴 수 있다.
            rawStore.save(SERVICE, collectedAt, "page_%03d.json".format(pageNo), page.rawBody)
            val rows = page.rows.map(I0030Row::from)
            changed += upserter.upsert(rows)
            rows.mapTo(seen) { it.reportNo }
            log.info("{} {}페이지: 누적 {}/{}건", SERVICE, pageNo, seen.size, page.totalCount)
        }

        // 덜 받은 것만 실패로 본다. 더 받은 것은 당일 신고 제품이 행에는 바로 나오고 total_count는 늦게 따라오는 경우다
        // (2026-11-04 total_count 46,179에 46,182건). 받은 행은 전부 저장했으니 잃은 데이터가 없다.
        // ponytail: 새 행 n건과 빠진 행 m건(n ≥ m)이 겹치면 빠짐이 가려진다. 빠짐은 페이징 도중 수정으로 행이 밀릴 때라 드물다.
        // 초기 적재에서 가려진 제품은 수정일이 바뀌지 않으면 증분으로도 안 들어오고, 전체 대사(파일 방식)가 생겨야 잡힌다.
        check(seen.size >= totalCount) {
            "$SERVICE 받은 신고번호 ${seen.size}건이 total_count ${totalCount}건보다 적다. 도중에 데이터가 바뀌었을 수 있으니 다시 실행한다."
        }
        if (seen.size > totalCount) log.warn("{} 받은 신고번호 {}건이 total_count {}건보다 많다(total_count 갱신 지연)", SERVICE, seen.size, totalCount)
        // 증분 수집이 이어 받을 기준. 시작 시각 기준이라 적재 도중 바뀐 건도 다음 증분(커서 - 1일)이 다시 받는다.
        val now = LocalDateTime.now(clock)
        val cursor = cursorRepository.findByIdOrNull(SERVICE)?.apply { advance(collectedAt.toLocalDate(), now) }
            ?: IngestionCursor(SERVICE, collectedAt.toLocalDate(), now)
        cursorRepository.save(cursor)
        log.info("{} 초기 적재 완료: {}건, 새로 쓰거나 바뀐 제품 {}건", SERVICE, seen.size, changed)
    }

    companion object {
        const val SERVICE = "I0030"
        private val log = LoggerFactory.getLogger(InitialLoadJob::class.java)
    }
}
