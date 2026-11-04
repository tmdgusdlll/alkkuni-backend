package com.alkkuni.core.product.entity

import com.alkkuni.core.parser.AmountSanityCheck
import com.alkkuni.core.parser.IngredientAmountParser
import com.alkkuni.core.parser.IntakeMethod
import com.alkkuni.core.parser.IntakeMethodParser
import com.alkkuni.core.parser.ParseResult
import com.alkkuni.core.parser.ParsedAmount
import com.alkkuni.core.product.enums.BasisCheck
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
import java.math.BigDecimal
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

    // 섭취방법 파싱 결과. 1회 중량은 무게면 mg, 부피면 ml(intakeAmountUnit).
    var intakeTimesPerDay: Int? = null
        protected set

    var intakeUnitsPerTime: BigDecimal? = null
        protected set

    var intakeUnit: String? = null
        protected set

    var intakeAmountPerTime: BigDecimal? = null
        protected set

    var intakeAmountUnit: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    var basisCheck: BasisCheck? = null
        protected set

    // 기준규격(standardText)과 그로부터 정하는 값(판정, 원료 여부, 판정 버전, 섭취방법 파싱, 분모 확인)은 applyParse로만 바꾼다.
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
     * 원문이 그대로인데 사람이 검수(REVIEWED)했으면 기준규격 판정은 바꾸지 않고 false다.
     * 원문이 바뀌었으면 검수 결과도 옛 원문 기준이라 다시 판정한다.
     * 섭취방법 파싱과 분모 확인은 검수 여부와 상관없이 매번 한다. 섭취방법만 바뀌어도 맞춰야 해서다.
     */
    fun applyParse(standardText: String?, result: ParseResult): Boolean {
        applyIntake(IntakeMethodParser.parse(intakeMethod), result.amounts)
        if (parseStatus == ParseStatus.REVIEWED && standardText == this.standardText) {
            // 기준규격 판정은 사람 것을 지키지만 섭취방법은 이 버전 규칙으로 다시 판정했다.
            // 버전을 그대로 두면 upsert의 건너뛰기 조건(해시·버전 같음)을 영영 못 맞춰 적재 때마다 다시 처리된다.
            parserVersion = IngredientAmountParser.VERSION
            return false
        }
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

    private fun applyIntake(intake: IntakeMethod?, amounts: List<ParsedAmount>) {
        intakeTimesPerDay = intake?.timesPerDay
        intakeUnitsPerTime = intake?.unitsPerTime
        intakeUnit = intake?.unit
        intakeAmountPerTime = intake?.amountPerTime
        intakeAmountUnit = intake?.amountUnit
        basisCheck = checkBasis(intake, amounts)
    }

    private fun checkBasis(intake: IntakeMethod?, amounts: List<ParsedAmount>): BasisCheck? {
        if (amounts.isEmpty()) return null
        if (intake == null) return BasisCheck.UNREADABLE
        // ponytail: 분모가 여러 개인 제품(약 150건)은 확인하지 않는다. 성분별로 확인하려면 product_ingredient에 둔다.
        // "/2,400mg"(2400)과 "/2.4g"(2400.0)이 같은 분모로 모이게 scale을 지운다. BigDecimal.equals는 scale까지 본다.
        val basis = amounts
            .map { IntakeMethodParser.normalize(it.basisAmount, it.basisUnit)?.let { (v, u) -> v.stripTrailingZeros() to u } }
            .distinct()
            .singleOrNull()
        val perTime = intake.amountPerTime
        if (basis == null || perTime == null || basis.second != intake.amountUnit) return BasisCheck.UNVERIFIED
        val (basisAmount, _) = basis
        val perDay = perTime.multiply(intake.timesPerDay.toBigDecimal())
        return when {
            near(perDay, basisAmount) -> BasisCheck.DAILY
            intake.timesPerDay > 1 && near(perTime, basisAmount) -> BasisCheck.PER_SERVING
            else -> BasisCheck.MISMATCH
        }
    }

    // 표기 반올림(0.45g × 2 = 900mg vs 분모 905mg)을 같다고 본다. 실제 데이터에서 2% 안에 93%가 맞았다.
    private fun near(a: BigDecimal, b: BigDecimal) = (a - b).abs() <= b.multiply(TOLERANCE)

    companion object {
        private val TOLERANCE = BigDecimal("0.02")
    }
}
