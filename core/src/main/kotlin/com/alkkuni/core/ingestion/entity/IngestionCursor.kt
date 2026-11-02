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
}