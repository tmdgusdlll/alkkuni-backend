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
    registeredAt: LocalDateTime? = null,
    grade: String? = null,
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

    // 식약처 등록 시각(CRET_DTM). 소식 목록 정렬·날짜에 쓴다
    var registeredAt: LocalDateTime? = registeredAt
        protected set

    // 회수 등급 원문(1·2·3등급, 자율회수 등). 식약처가 새 값을 내도 수집이 깨지지 않게 enum으로 두지 않는다
    var grade: String? = grade
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

    /**
     * 이번 목록에 있었다. 원본 값이 고쳐졌을 수 있어 덮어쓰고, 빠졌던 건이면 다시 목록에 올린다.
     * 등록일은 null이면 덮어쓰지 않는다. 식약처가 형식을 바꿔 읽지 못한 날 이미 저장된 날짜가 지워지지 않게 한다.
     */
    fun seen(
        reportNo: String?,
        productName: String,
        manufacturer: String?,
        productType: String?,
        reason: String?,
        expiryText: String?,
        registeredAt: LocalDateTime?,
        grade: String?,
        at: LocalDateTime,
    ) {
        this.reportNo = reportNo
        this.productName = productName
        this.manufacturer = manufacturer
        this.productType = productType
        this.reason = reason
        this.expiryText = expiryText
        if (registeredAt != null) this.registeredAt = registeredAt
        this.grade = grade
        listStatus = RecallListStatus.LISTED
        lastSeenAt = at
    }

    companion object {
        // I0490 PRDLST_TYPE 값. 다른 식품 회수도 함께 오므로 서비스는 이 유형만 보여 준다
        const val HEALTH_FUNCTIONAL_FOOD = "건강기능식품"
    }

    /** 이번 목록에 없었다. 지우지 않고 상태만 바꾼다. */
    fun delist() {
        listStatus = RecallListStatus.DELISTED
    }
}
