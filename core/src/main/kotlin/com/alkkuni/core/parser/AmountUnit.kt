package com.alkkuni.core.parser

import java.math.BigDecimal

// 함량 단위 표기(261종)를 비교할 수 있는 단위로 모은다.
// 질량은 mg, 비타민 환산 단위는 "mg α-TE"처럼 mg + 환산 기준, 균수는 억·10^n을 곱한 CFU(사균은 cells).
// IU·U·FU·개는 성분마다 환산이 달라 null이다(성분 사전 단계). 규칙을 바꾸면 IngredientAmountParser.VERSION을 올린다.
object AmountUnit {

    private val mass = Regex("""^(kg|mg|㎎|μg|µg|㎍|ug|mcg|g)(.*)$""")
    private val mgPer = mapOf(
        "kg" to BigDecimal("1000000"),
        "g" to BigDecimal("1000"),
        "mg" to BigDecimal.ONE,
        "㎎" to BigDecimal.ONE,
        "μg" to BigDecimal("0.001"),
        "µg" to BigDecimal("0.001"),
        "㎍" to BigDecimal("0.001"),
        "ug" to BigDecimal("0.001"),
        "mcg" to BigDecimal("0.001"),
    )

    // "1억 CFU", "2 X 10^9 CFU", "5*10^8cfu", "1×10^8c.f.u", "2억 cells"
    private val count = Regex("""^(?:(억)|[x×*]\s*10\s*\^\s*(\d+))?\s*(cfu|c\.f\.u\.?|cells)$""", RegexOption.IGNORE_CASE)
    private val hundredMillion = BigDecimal("100000000")

    // 비교할 단위로 바꾼 값과 단위. 모르는 단위면 null.
    fun normalize(value: BigDecimal, unit: String): Pair<BigDecimal, String>? {
        val u = unit.trim()
        count.find(u)?.let { m ->
            val multiplier = when {
                m.groupValues[1].isNotEmpty() -> hundredMillion
                m.groupValues[2].isNotEmpty() -> BigDecimal.TEN.pow(m.groupValues[2].toInt())
                else -> BigDecimal.ONE
            }
            return value.multiply(multiplier) to if (m.groupValues[3].equals("cells", ignoreCase = true)) "cells" else "CFU"
        }
        val m = mass.find(u.lowercase()) ?: return null
        val basis = equivalent(m.groupValues[2]) ?: return null
        return value.multiply(mgPer.getValue(m.groupValues[1])) to listOf("mg", basis).filter { it.isNotEmpty() }.joinToString(" ")
    }

    // 환산 기준 표시는 무시하고 질량(mg)만 본다. 함량 이상치 검사용.
    fun toMg(value: BigDecimal, unit: String): BigDecimal? =
        mass.find(unit.trim().lowercase())?.takeIf { equivalent(it.groupValues[2]) != null }?.let { value.multiply(mgPer.getValue(it.groupValues[1])) }

    // 질량 단위 뒤에 붙은 환산 기준. "" → 없음, "a-te"·"ɑ-te"·"te" → α-TE, "rea" → RAE, "def" → DFE(오타). 모르면 null.
    private fun equivalent(suffix: String): String? {
        val s = suffix.replace(Regex("""[\s\-‑]"""), "").uppercase()
        return when {
            s.isEmpty() -> ""
            s.endsWith("TE") && s.length <= 3 -> "α-TE"
            s == "NE" -> "NE"
            s == "RAE" || s == "REA" -> "RAE"
            s == "RE" -> "RE"
            s == "DFE" || s == "DEF" -> "DFE"
            else -> null
        }
    }
}
