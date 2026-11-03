package com.alkkuni.core.recall.entity

import com.alkkuni.core.recall.enums.RecallListStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "recall")
class Recall(
    @Column(nullable = false, unique = true)
    val sourceSeq: String,
    reportNo: String?,
    productName: String,
    manufacturer: String?,
    productType: String?,
    reason: String?,
    expiryText: String?,
    seenAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    var reportNo: String? = reportNo
        protected set

    @Column(nullable = false)
    var productName: String = productName
        protected set

    var manufacturer: String? = manufacturer
        protected set

    var productType: String? = productType
        protected set

    var reason: String? = reason
        protected set

    var expiryText: String? = expiryText
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var listStatus: RecallListStatus = RecallListStatus.LISTED
        protected set

    @Column(nullable = false)
    val firstSeenAt: LocalDateTime = seenAt

    @Column(nullable = false)
    var lastSeenAt: LocalDateTime = seenAt
        protected set

    /** 이번 목록에 있었다. 원본 값이 고쳐졌을 수 있어 덮어쓰고, 빠졌던 건이면 다시 목록에 올린다. */
    fun seen(
        reportNo: String?,
        productName: String,
        manufacturer: String?,
        productType: String?,
        reason: String?,
        expiryText: String?,
        at: LocalDateTime,
    ) {
        this.reportNo = reportNo
        this.productName = productName
        this.manufacturer = manufacturer
        this.productType = productType
        this.reason = reason
        this.expiryText = expiryText
        listStatus = RecallListStatus.LISTED
        lastSeenAt = at
    }

    /** 이번 목록에 없었다. 지우지 않고 상태만 바꾼다. */
    fun delist() {
        listStatus = RecallListStatus.DELISTED
    }
}
