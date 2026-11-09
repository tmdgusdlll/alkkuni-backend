package com.alkkuni.core.ingredient.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

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

    // referenceAmount·upperLimit의 단위. 1일 함량 단위(dailyUnit)가 이와 같을 때만 비교한다.
    var referenceUnit: String? = referenceUnit
        protected set

    // 상한섭취량(성인). 없으면 null.
    var upperLimit: BigDecimal? = upperLimit
        protected set

    /**
     * 1일 함량이 상한의 2배를 넘으면 true. 원문 오기·파싱 오류로 의심해 검수 대기로 보여준다.
     * 결과는 저장하지 않고 응답 때 계산한다. 저장하면 수집·성분 연결 두 경로가 모두 다시 써야 하고 상한을 고칠 때마다 배치를 돌려야 한다.
     * 단위가 기준 단위와 다르면(엽산 mg DFE 기준에 mg 행) 비교하지 않는다.
     */
    fun exceedsUpperLimit(dailyAmount: BigDecimal?, dailyUnit: String?): Boolean {
        val limit = upperLimit ?: return false
        if (dailyAmount == null || dailyUnit != referenceUnit) return false
        return dailyAmount > limit * UPPER_LIMIT_MARGIN
    }

    companion object {
        // ponytail: 상한만 넘어도 잡으면 고함량 정상 제품(비타민 D 5,000IU, 비타민 C 3,000mg 등) 790개가 걸려 2배로 둔다(로컬 25개).
        // 기능성 원료 상한(고시 최고량)을 채우거나 기준을 좁힐 때 다시 센다.
        private val UPPER_LIMIT_MARGIN = BigDecimal(2)
    }
}