package com.alkkuni.core.product.entity

import com.alkkuni.core.parser.AmountSanityCheck
import com.alkkuni.core.parser.IngredientAmountParser
import com.alkkuni.core.parser.ParseResult
import com.alkkuni.core.product.enums.ParseStatus
import com.alkkuni.core.product.enums.ProductStatus
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
@Table(name = "product")
class Product(
    @Column(nullable = false, unique = true)
    val reportNo: String,
    name: String,
    manufacturer: String? = null,
    intakeMethod: String? = null,
    standardText: String? = null,
    rawMaterial: Boolean,
    sourceHash: String? = null,
    sourceUpdatedAt: LocalDateTime? = null,
    parseStatus: ParseStatus,
    parseIssue: String? = null,
    parserVersion: String,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    var manufacturer: String? = manufacturer
        protected set

    var intakeMethod: String? = intakeMethod
        protected set

    var standardText: String? = standardText
        protected set

    @Column(nullable = false)
    var rawMaterial: Boolean = rawMaterial
        protected set

    var sourceHash: String? = sourceHash
        protected set

    var sourceUpdatedAt: LocalDateTime? = sourceUpdatedAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus = ProductStatus.ACTIVE
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var parseStatus: ParseStatus = parseStatus
        protected set

    var parseIssue: String? = parseIssue
        protected set

    // 판정에 쓴 파서 규칙 버전. 성분 행이 없는 제품도 재판정 대상으로 찾을 수 있게 제품에 둔다.
    @Column(nullable = false)
    var parserVersion: String = parserVersion
        protected set

    // 기준규격(standardText)과 그로부터 정하는 값(판정, 원료 여부, 판정 버전)은 applyParse로만 바꾼다.
    // 규칙이 바뀌어 다시 판정할 때 applyParse 하나만 부르면 모두 새 규칙으로 맞춰진다.
    fun updateSource(
        name: String,
        manufacturer: String?,
        intakeMethod: String?,
        sourceHash: String,
        sourceUpdatedAt: LocalDateTime?,
    ) {
        this.name = name
        this.manufacturer = manufacturer
        this.intakeMethod = intakeMethod
        this.sourceHash = sourceHash
        this.sourceUpdatedAt = sourceUpdatedAt
    }

    /**
     * 파싱 결과로 판정을 정한다. 성분 행을 다시 써야 하면 true.
     * 원문이 그대로인데 사람이 검수(REVIEWED)했으면 아무것도 바꾸지 않고 false다.
     * 원문이 바뀌었으면 검수 결과도 옛 원문 기준이라 다시 판정한다.
     */
    fun applyParse(standardText: String?, result: ParseResult): Boolean {
        if (parseStatus == ParseStatus.REVIEWED && standardText == this.standardText) return false
        val issues = result.unreadLines.map { "못 읽은 줄: $it" } + AmountSanityCheck.issues(result.amounts)
        this.standardText = standardText
        rawMaterial = standardText?.let(IngredientAmountParser::isRawMaterialOnly) ?: false
        parseStatus = when {
            issues.isNotEmpty() -> ParseStatus.NEEDS_REVIEW
            result.amounts.isEmpty() -> ParseStatus.NO_AMOUNT
            else -> ParseStatus.PARSED
        }
        parseIssue = issues.joinToString("\n").ifEmpty { null }
        parserVersion = IngredientAmountParser.VERSION
        return true
    }
}