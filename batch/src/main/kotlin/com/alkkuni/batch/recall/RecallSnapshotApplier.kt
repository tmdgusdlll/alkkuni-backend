package com.alkkuni.batch.recall

import com.alkkuni.core.recall.entity.Recall
import com.alkkuni.core.recall.enums.RecallListStatus
import com.alkkuni.core.recall.repository.RecallRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 받은 I0490 전체 목록을 DB와 비교해 반영한다.
 * - 처음 보는 건: 저장한다(알림 대상).
 * - 있던 건: 값을 덮어쓰고, 빠졌던 건이면 다시 목록에 올린다.
 * - 목록에서 사라진 건: 지우지 않고 DELISTED로만 바꾼다.
 *
 * 전체가 한 트랜잭션이라 도중에 실패하면 아무것도 바뀌지 않는다.
 */
@Service
class RecallSnapshotApplier(private val recallRepository: RecallRepository) {

    @Transactional
    fun apply(rows: List<I0490Row>, at: LocalDateTime): Result {
        // 약 400건이라 전부 읽는다.
        val existing = recallRepository.findAll().associateBy { it.sourceSeq }
        val received = rows.associateBy { it.sourceSeq }
        val toDelist = existing.values.filter { it.listStatus == RecallListStatus.LISTED && it.sourceSeq !in received }
        val listedCount = existing.values.count { it.listStatus == RecallListStatus.LISTED }

        // 점검·장애로 목록이 비거나 크게 줄어 온 걸 "회수 종료"로 반영하면 되돌리기 어렵다. 바꾸기 전에 멈춘다.
        check(toDelist.size * 2 <= listedCount) {
            "I0490 목록에 있던 ${listedCount}건 중 ${toDelist.size}건이 한 번에 빠졌다(받은 ${rows.size}건). 응답 이상으로 보고 반영하지 않는다."
        }

        var relisted = 0
        val created = mutableListOf<Recall>()
        for (row in received.values) {
            val found = existing[row.sourceSeq]
            if (found == null) {
                created += Recall(
                    row.sourceSeq, row.reportNo, row.productName, row.manufacturer,
                    row.productType, row.reason, row.expiryText, at,
                )
            } else {
                if (found.listStatus == RecallListStatus.DELISTED) relisted++
                found.seen(row.reportNo, row.productName, row.manufacturer, row.productType, row.reason, row.expiryText, at)
            }
        }
        toDelist.forEach(Recall::delist)
        recallRepository.saveAll(created)
        return Result(created = created.size, delisted = toDelist.size, relisted = relisted)
    }

    data class Result(val created: Int, val delisted: Int, val relisted: Int)
}
