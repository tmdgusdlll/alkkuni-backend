package com.alkkuni.batch.product

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
import java.time.LocalDateTime

/**
 * I0030 전체를 받아 원본 저장 → 파싱 → upsert 한다. 최초 1회와 장애 복구 때 수동으로 돌린다.
 * 실행: ./gradlew :batch:bootRun --args='--alkkuni.job=initial-load'
 *
 * 페이징 도중 행이 추가·삭제되면 위치가 밀려 경계의 행이 빠질 수 있다. 그래서 둘 다 확인하고 실패로 끝낸다.
 * - 페이지마다 total_count가 첫 페이지와 같은지. 받은 범위에서 하나 삭제되고 개수가 함께 줄면 끝의 비교만으로는 못 잡는다.
 * - 끝에 받은 신고번호 수가 total_count와 같은지.
 * upsert가 멱등이라 다시 돌리면 된다(47회 호출, 시간당 한도 100회 안).
 */
@Component
@ConditionalOnProperty(name = ["alkkuni.job"], havingValue = "initial-load")
class InitialLoadJob(
    private val client: FoodSafetyClient,
    private val rawStore: RawStore,
    private val upserter: ProductUpserter,
    private val cursorRepository: IngestionCursorRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val collectedAt = LocalDateTime.now()
        val seen = HashSet<String>()
        var totalCount: Int? = null
        var pageNo = 0
        var changed = 0

        client.forEachPage(SERVICE) { page ->
            pageNo++
            val expected = totalCount ?: page.totalCount.also { totalCount = it }
            check(page.totalCount == expected) {
                "$SERVICE ${pageNo}페이지 total_count가 ${expected}건에서 ${page.totalCount}건으로 바뀌었다. 도중에 데이터가 바뀌어 행이 빠졌을 수 있으니 다시 실행한다."
            }
            // 가공 전에 원본부터 남긴다. 파싱·저장이 실패해도 원본으로 다시 돌릴 수 있다.
            rawStore.save(SERVICE, collectedAt, "page_%03d.json".format(pageNo), page.rawBody)
            val rows = page.rows.map(I0030Row::from)
            changed += upserter.upsert(rows)
            rows.mapTo(seen) { it.reportNo }
            log.info("{} {}페이지: 누적 {}/{}건", SERVICE, pageNo, seen.size, expected)
        }

        check(seen.size == totalCount) {
            "$SERVICE 받은 신고번호 ${seen.size}건이 total_count ${totalCount}건과 다르다. 도중에 데이터가 바뀌었을 수 있으니 다시 실행한다."
        }
        // 증분 수집이 이어 받을 기준. 시작 시각 기준이라 적재 도중 바뀐 건도 다음 증분(커서 - 1일)이 다시 받는다.
        val now = LocalDateTime.now()
        val cursor = cursorRepository.findByIdOrNull(SERVICE)?.apply { advance(collectedAt.toLocalDate(), now) }
            ?: IngestionCursor(SERVICE, collectedAt.toLocalDate(), now)
        cursorRepository.save(cursor)
        log.info("{} 초기 적재 완료: {}건, 새로 쓰거나 바뀐 제품 {}건", SERVICE, totalCount, changed)
    }

    companion object {
        const val SERVICE = "I0030"
        private val log = LoggerFactory.getLogger(InitialLoadJob::class.java)
    }
}
