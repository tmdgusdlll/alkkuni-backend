package com.alkkuni.batch.recall

import com.alkkuni.core.recall.entity.Recall
import com.alkkuni.core.recall.enums.RecallListStatus
import com.alkkuni.core.recall.repository.RecallRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 받은 I0490 목록을 DB와 비교해 반영한다.
 * - 처음 보는 건: 저장한다(알림 대상).
 * - 있던 건: 값을 덮어쓰고, 빠졌던 건이면 다시 목록에 올린다.
 * - 목록에서 사라진 건: 지우지 않고 DELISTED로만 바꾼다.
 *   목록이 완전하지 않거나([incompleteReason]) 한 번에 너무 많이 빠지면 이 단계만 건너뛰고 이유를 돌려준다.
 *
 * 전체가 한 트랜잭션이라 도중에 실패하면 아무것도 바뀌지 않는다.
 */
@Service
class RecallSnapshotApplier(private val recallRepository: RecallRepository) {

    @Transactional
    fun apply(rows: List<I0490Row>, at: LocalDateTime, incompleteReason: String? = null): Result {
        // 약 400건이라 전부 읽는다.
        val existing = recallRepository.findAll().associateBy { it.sourceSeq }
        val received = rows.associateBy { it.sourceSeq }
        val toDelist = existing.values.filter { it.listStatus == RecallListStatus.LISTED && it.sourceSeq !in received }
        val listedCount = existing.values.count { it.listStatus == RecallListStatus.LISTED }

        // 점검·장애로 목록이 비거나(INFO-200은 0건 정상 응답으로 온다) 크게 줄어 온 걸 "회수 종료"로 반영하지 않는다.
        // ponytail: 비율 기준이라 total_count까지 줄여 절반 미만으로 덜 온 오류 응답(300→200 등)은 못 거른다. DELISTED를 화면·알림에 쓰기 시작하면 N일 연속 미노출 기준으로 바꾼다(PLANNING "회수 수집").
        val delistSkipped = incompleteReason
            ?: if (toDelist.size * 2 > listedCount) {
                "목록에 있던 ${listedCount}건 중 ${toDelist.size}건이 한 번에 빠졌다(받은 ${rows.size}건). 응답 이상으로 본다"
            } else {
                null
            }

        var relisted = 0
        val created = mutableListOf<Recall>()
        for (row in received.values) {
            val found = existing[row.sourceSeq]
            if (found == null) {
                created += Recall(
                    sourceSeq = row.sourceSeq,
                    reportNo = row.reportNo,
                    productName = row.productName,
                    manufacturer = row.manufacturer,
                    productType = row.productType,
                    reason = row.reason,
                    expiryText = row.expiryText,
                    seenAt = at,
                )
            } else {
                if (found.listStatus == RecallListStatus.DELISTED) relisted++
                found.seen(
                    reportNo = row.reportNo,
                    productName = row.productName,
                    manufacturer = row.manufacturer,
                    productType = row.productType,
                    reason = row.reason,
                    expiryText = row.expiryText,
                    at = at,
                )
            }
        }
        if (delistSkipped == null) toDelist.forEach(Recall::delist)
        recallRepository.saveAll(created)
        return Result(
            created = created.size,
            delisted = if (delistSkipped == null) toDelist.size else 0,
            relisted = relisted,
            delistSkipped = delistSkipped,
        )
    }

    /** [delistSkipped]가 있으면 빠짐 처리를 건너뛴 이유다. */
    data class Result(val created: Int, val delisted: Int, val relisted: Int, val delistSkipped: String? = null)
}
