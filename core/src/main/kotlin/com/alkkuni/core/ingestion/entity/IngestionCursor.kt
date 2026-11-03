package com.alkkuni.core.ingestion.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "ingestion_cursor")
class IngestionCursor(
    @Id
    val source: String,
    lastCollectedDate: LocalDate,
    updatedAt: LocalDateTime
) {
    var lastCollectedDate: LocalDate = lastCollectedDate
        protected set

    var updatedAt: LocalDateTime = updatedAt
        protected set

    /** 수집이 끝난 뒤 커서를 앞으로 옮긴다. 뒤로 가면 이미 받은 구간을 다시 받거나 빈 구간이 생길 수 있어 막는다. */
    fun advance(collectedDate: LocalDate, now: LocalDateTime) {
        require(!collectedDate.isBefore(lastCollectedDate)) {
            "$source 커서를 ${lastCollectedDate}에서 ${collectedDate}로 되돌릴 수 없다."
        }
        lastCollectedDate = collectedDate
        updatedAt = now
    }
}
