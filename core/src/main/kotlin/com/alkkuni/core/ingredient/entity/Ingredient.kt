package com.alkkuni.core.ingredient.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode

@Entity
@Table(name = "ingredient")
class Ingredient(
    name: String,
    displayName: String = name,
    englishName: String? = null,
    referenceAmount: BigDecimal? = null,
    referenceUnit: String? = null,
    upperLimit: BigDecimal? = null,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    // 화면에 보여줄 이름. 대부분 name과 같고, 약칭을 앞세울 때만 다르다(예: "총 HCA (하이드록시시트르산)").
    @Column(nullable = false)
    var displayName: String = displayName
        protected set

    // 영문 정식명. 원문·고시에 있는 성분만 채운다.
    var englishName: String? = englishName
        protected set

    var defaultUnit: String? = null
        protected set

    // IU → mg 환산 배율(1mg = iuPerMg IU). 성분마다 달라 사전에 둔다. 모르면 null(IU 함량의 1일 함량도 null).
    var iuPerMg: BigDecimal? = null
        protected set

    // 1일 기준값(영양성분 기준치 또는 고시형 원료 1일 섭취량 하한). 없으면 null. 출처는 docs/daily-reference.csv.
    var referenceAmount: BigDecimal? = referenceAmount
        protected set

    // referenceAmount·upperLimit의 단위. 1일 함량 단위(dailyUnit)가 이와 같거나 환산 표기만 빠진 질량 단위일 때 비교한다.
    // 단위가 섞인 성분(엽산·비타민 A·나이아신·비타민 E)은 defaultUnit과 값이 같다. 한쪽을 바꾸면 다른 쪽도 함께 바꾼다
    // (따로 바꾸면 목록의 주 단위와 기준 대비 %·상한 판정의 단위가 갈라진다).
    var referenceUnit: String? = referenceUnit
        protected set

    // 상한. 비타민·무기질은 상한섭취량(성인), 기능성 원료는 고시 1일 섭취량 최고값(V12). 없으면 null.
    var upperLimit: BigDecimal? = upperLimit
        protected set

    /**
     * 1일 함량이 상한의 2배를 넘으면 true. 원문 오기·파싱 오류로 의심해 검수 대기로 보여준다.
     * 결과는 저장하지 않고 응답 때 계산한다. 저장하면 수집·성분 연결 두 경로가 모두 다시 써야 하고 상한을 고칠 때마다 배치를 돌려야 한다.
     * 단위는 기준 단위와 같거나, 환산 표기(DFE·RAE·NE·α-TE)만 빠진 질량 단위("mg")일 때 비교한다.
     * 다른 환산 표기(비타민 A의 "mg RE")나 다른 단위는 비교하지 않는다.
     */
    fun exceedsUpperLimit(dailyAmount: BigDecimal?, dailyUnit: String?): Boolean {
        val threshold = suspectAbove() ?: return false
        if (dailyAmount == null || dailyUnit == null || dailyUnit !in comparableUnits()) return false
        return dailyAmount > threshold
    }

    /**
     * 1일 함량이 기준값 이상이면 true, 미만이면 false.
     * 판정하지 않으면 null: 기준값이 없거나 단위를 비교할 수 없을 때, 상한 2배를 넘는 오류 의심 값일 때(false면 "기준 미달"로 보인다).
     * 성분별 목록의 "기준 충족" 필터는 이 값이 true인 행만 근거로 쓴다(같은 규칙).
     */
    fun meetsReference(dailyAmount: BigDecimal?, dailyUnit: String?): Boolean? {
        val reference = referenceAmount
        if (dailyAmount == null || reference == null || dailyUnit !in comparableUnits()) return null
        if (exceedsUpperLimit(dailyAmount, dailyUnit)) return null
        return dailyAmount >= reference
    }

    // 기준값 대비 %(소수점 버림). 올리면 미충족 값이 100%로 보인다. 판정할 수 없으면 null.
    fun referencePercent(dailyAmount: BigDecimal?, dailyUnit: String?): Int? {
        val reference = referenceAmount
        if (dailyAmount == null || reference == null || dailyUnit !in comparableUnits()) return null
        // 파싱 오류 값은 Int 범위를 넘을 수 있다. toInt()는 넘치면 음수가 되므로 자른다.
        return dailyAmount.multiply(HUNDRED).divide(reference, 0, RoundingMode.DOWN).min(MAX_PERCENT).toInt()
    }

    // 이 값을 넘는 1일 함량(상한 2배)은 표기·파싱 오류로 의심한다. 상한이 없으면 null.
    fun suspectAbove(): BigDecimal? = upperLimit?.times(UPPER_LIMIT_MARGIN)

    /**
     * referenceAmount·upperLimit과 비교할 수 있는 1일 함량 단위. 기준 단위와, 환산 표기만 뺀 질량 단위("mg DFE" → "mg").
     * 상한 판정과 성분별 목록의 "기준 충족" 필터가 같은 규칙을 쓴다. 기준 단위가 없으면 빈 목록이다.
     */
    // ponytail: 표기 없는 "mg"를 환산 단위와 같게 본다. 나이아신(1mg = 1mg NE)·비타민 E·A(레티놀)는 같고,
    // 엽산 mg(보충제 엽산)은 실제 DFE가 1.7배라 상한 판정은 느슨하고 기준 충족은 보수적이다(로컬에서 엽산 120mg 1건 잡힘).
    fun comparableUnits(): List<String> {
        val unit = referenceUnit ?: return emptyList()
        return listOf(unit, unit.substringBefore(' ')).distinct()
    }

    companion object {
        // ponytail: 상한만 넘어도 잡으면 고함량 정상 제품(비타민 D 5,000IU, 비타민 C 3,000mg 등) 790개가 걸려 2배로 둔다(로컬 25개).
        // 기능성 원료 상한(V12)으로 13개가 늘었다. 기준을 좁힐 때 다시 센다.
        private val UPPER_LIMIT_MARGIN = BigDecimal(2)
        private val HUNDRED = BigDecimal(100)
        private val MAX_PERCENT = BigDecimal(Int.MAX_VALUE)
    }
}