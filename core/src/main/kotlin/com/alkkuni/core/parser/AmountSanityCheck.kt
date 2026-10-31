package com.alkkuni.core.parser

import java.math.BigDecimal

// 파서가 뽑은 함량끼리 모순되는지 본다. 값은 고치지 않고 검수 이유만 돌려준다.
// "1.600 mg"이 1,600의 오기인지 진짜 1.6인지는 숫자 하나로는 알 수 없어서, 다른 값과 맞지 않을 때만 잡는다.
object AmountSanityCheck {

    // 질량 단위만 비교한다. "mg α-TE", "IU", "CFU"처럼 질량으로 바꿀 수 없는 단위는 건너뛴다.
    private val mgPerUnit = mapOf(
        "kg" to BigDecimal("1000000"),
        "g" to BigDecimal("1000"),
        "mg" to BigDecimal.ONE,
        "㎎" to BigDecimal.ONE,
        "μg" to BigDecimal("0.001"),
        "µg" to BigDecimal("0.001"),
        "㎍" to BigDecimal("0.001"),
        "ug" to BigDecimal("0.001"),
    )
    private val thousand = BigDecimal(1000)

    fun issues(amounts: List<ParsedAmount>): List<String> {
        val issues = mutableListOf<String>()

        // 1) 함량이 분모보다 크면 불가능하다. 분모 오기("210mg/1.600mg")와 단위 오기("2.4g/200mg", ㎍을 g으로)를 잡는다.
        for (a in amounts) {
            val amount = toMg(a.amount, a.unit) ?: continue
            val basis = toMg(a.basisAmount, a.basisUnit) ?: continue
            if (amount > basis) issues += "함량이 분모보다 큼: ${a.rawName} ${a.amount.plain()}${a.unit}/${a.basisAmount.plain()}${a.basisUnit}"
        }

        // 2) 같은 제품 안에서 분모가 정확히 1000배 차이 나면 쉼표·마침표나 g·mg를 잘못 적은 것이다("1.6mg"과 "1,600mg").
        // 분모가 단위만 있는 농도 표기("4mg/g")는 제품 1회 분량이 아니라서 비교하지 않는다.
        val bases = amounts
            .filter { it.basisAmount.compareTo(BigDecimal.ONE) != 0 }
            .mapNotNull { a -> toMg(a.basisAmount, a.basisUnit)?.let { it to "${a.basisAmount.plain()}${a.basisUnit}" } }
            .distinctBy { it.first.stripTrailingZeros() }
        for ((small, smallText) in bases) {
            bases.firstOrNull { (large, _) -> large.compareTo(small * thousand) == 0 }
                ?.let { (_, largeText) -> issues += "분모 불일치: $smallText / $largeText" }
        }
        return issues
    }

    private fun toMg(value: BigDecimal, unit: String): BigDecimal? = mgPerUnit[unit.trim().lowercase()]?.let { value * it }

    private fun BigDecimal.plain() = stripTrailingZeros().toPlainString()
}
