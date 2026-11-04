package com.alkkuni.core.product.entity

import com.alkkuni.core.parser.AmountSanityCheck
import com.alkkuni.core.parser.AmountUnit
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
import java.math.MathContext
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
        rawMaterial = standardText?.let(IngredientAmountParser::isRawMaterialOnly) == true ||
            IntakeMethodParser.isRawMaterialUse(intakeMethod)
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
        return when (servingRatio(basis.first, perTime, intake.timesPerDay)) {
            null -> BasisCheck.MISMATCH
            BigDecimal.ONE -> BasisCheck.DAILY
            else -> BasisCheck.PER_SERVING
        }
    }

    // 분모 → 1일 섭취량 배율. 분모가 하루치면 1, 1회분이면 1일 횟수, 둘 다 아니면 null.
    // 분모 확인(checkBasis)과 1일 함량(dailyAmount)이 같은 판단을 쓰게 한 곳에 둔다.
    private fun servingRatio(basis: BigDecimal, perTime: BigDecimal, times: Int): BigDecimal? = when {
        near(perTime.multiply(times.toBigDecimal()), basis) -> BigDecimal.ONE
        times > 1 && near(perTime, basis) -> times.toBigDecimal()
        else -> null
    }

    /**
     * 성분 함량 한 줄의 1일 함량을 통일한 단위(AmountUnit)로 계산한다. 계산할 수 없으면 null.
     * 1일 함량 = 함량 × (1일 섭취 중량 ÷ 분모). 1일 섭취 중량 = 1회 중량 × 1일 횟수. applyParse 뒤에 부른다.
     * - 비교할 수 있으면: 분모가 하루치면 그대로, 1회분이면 × 1일 횟수, "/g" 같은 단위당 농도면 × 하루 섭취 중량. 어느 것도 아니면 원문 오류일 수 있어 null.
     * - 비교할 수 없으면(1회 중량 없음, 무게·부피 섞임, 섭취방법 못 읽음): 전체 중량 분모는 하루치로 가정한다
     *   (확인된 제품의 94%가 하루치, 추정 여부는 basisCheck로 구분). 단위당 분모는 하루에 몇 g 먹는지 몰라 null.
     */
    fun dailyAmount(parsed: ParsedAmount): Pair<BigDecimal, String>? {
        // 원료는 하루 섭취량이 없다. 비교 대상도 아니다.
        if (rawMaterial) return null
        val (amount, unit) = AmountUnit.normalize(parsed.amount, parsed.unit) ?: return null
        // "4mg/g"처럼 분모에 숫자가 없으면 파서가 1을 넣는다. g·ml당 농도다.
        // "/1.000mg"(1,000mg의 오기)이 1mg으로 읽혀 1mg당 농도로 보면 1000배가 되므로 mg 분모는 농도로 보지 않는다.
        val perUnit = parsed.basisAmount.compareTo(BigDecimal.ONE) == 0 && parsed.basisUnit.lowercase() in PER_UNIT_BASIS
        val basis = IntakeMethodParser.normalize(parsed.basisAmount, parsed.basisUnit)
        val perTime = intakeAmountPerTime
        val times = intakeTimesPerDay
        // 분모 0은 원문 오기다. 하루치로 가정하지 않는다.
        if (basis?.first?.signum() == 0) return null
        if (basis == null || perTime == null || times == null || basis.second != intakeAmountUnit) {
            return if (perUnit) null else amount.clean() to unit
        }
        val ratio = if (perUnit) {
            perTime.multiply(times.toBigDecimal()).divide(basis.first, MathContext.DECIMAL64)
        } else {
            servingRatio(basis.first, perTime, times) ?: return null
        }
        return amount.multiply(ratio).clean() to unit
    }

    // 0.700 → 0.7. 1E+8처럼 지수 표기가 되지 않게 음수 scale은 0으로 둔다.
    private fun BigDecimal.clean() = stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }

    // 표기 반올림(0.45g × 2 = 900mg vs 분모 905mg)을 같다고 본다. 실제 데이터에서 2% 안에 93%가 맞았다.
    private fun near(a: BigDecimal, b: BigDecimal) = (a - b).abs() <= b.multiply(TOLERANCE)

    companion object {
        private val TOLERANCE = BigDecimal("0.02")
        private val PER_UNIT_BASIS = setOf("g", "ml", "㎖")
    }
}
