package com.alkkuni.core.parser

import java.math.BigDecimal

// 섭취방법(NTK_MTHD) 한 문장에서 읽은 값. "1일 2회, 1회 2캡슐(1,200mg)" → 2회, 2, 캡슐, 1200, mg
// amountPerTime: 1회 섭취 중량·부피. 괄호 병기가 있으면 그 값, 없으면 단위가 g·ml일 때만 개수 × 단위로 구한다.
// amountUnit: 무게는 mg, 부피는 ml로 통일한다. 밀도를 몰라 둘 사이는 바꾸지 않는다.
data class IntakeMethod(
    val timesPerDay: Int,
    val unitsPerTime: BigDecimal,
    val unit: String,
    val amountPerTime: BigDecimal?,
    val amountUnit: String?,
)

// 섭취방법 문장에서 1일 횟수, 1회 개수·단위, 1회 중량을 읽는다. 못 읽거나 범위·선택 표기면 null.
// 규칙을 바꾸면 IngredientAmountParser.VERSION을 올린다. 제품 재판정이 그 버전 하나로 움직인다.
object IntakeMethodParser {

    private const val NUMBER = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?"""
    private const val UNIT =
        """정|캡슐|캅셀|포|병|알|환|스틱|봉|방울|드롭|개|구미|스푼|티스푼|매|팩|앰플|본|vial|Vial|mg|㎎|g|ml|mL|㎖"""
    // 구분 표기: 공백, 쉼표(전각 포함), 마침표, 빗금
    private const val SEP = """[\s,，./]*"""
    private const val AMOUNT_UNIT = """mg|㎎|g|ml|mL|㎖|L"""

    // "한포", "세 스푼"처럼 한글 수사로 적은 개수. 단위 바로 앞에서만 읽는다.
    private val koreanNumbers = mapOf("한" to 1, "두" to 2, "세" to 3, "네" to 4)
    private const val COUNT = """$NUMBER|[한두세네](?=\s?(?:$UNIT))"""

    // "1일 2회, 1회 2캡슐", "1일 1회 1캡슐", "1일 3회, 1회에 1포", "1일, 3회 1회시 1포", "1일 1회 / 1회 1포", "1일 2정"(횟수 없음 → 1회로 봄)
    // 단위 뒤 괄호: "1Vial(병)(20mL)"처럼 단위 설명 괄호를 건너뛴다.
    // 곱셈 표기 "3g×2포(6g)"는 1회 2포로 읽고, 중량 괄호가 없으면 3g × 2 = 6g을 1회 중량으로 쓴다.
    private val pattern = Regex(
        """(?U)1\s*일$SEP(?:(?<times>\d+)\s*회$SEP(?:1\s*회\s*(?:에|당|분|시)?$SEP)?)?""" +
            """(?<units>$COUNT)\s*(?<unit>$UNIT)(?![A-Za-z])""" +
            """(?:\s*\([가-힣]+\))?(?:\s*[×xX*]\s*(?<mult>\d+)\s*(?<multunit>$UNIT))?""" +
            """(?:\s*\(\s*(?<amt>$NUMBER)\s*(?<amtunit>$AMOUNT_UNIT)\s*\))?""",
    )

    // "1일 1~2회", "1회 1-2정", "1일 1회 1포 또는 1일 2회 …"는 한 값으로 정할 수 없어 읽지 않는다.
    // "직접 또는 물과 함께"의 "또는"은 숫자가 뒤따르지 않아 걸리지 않는다.
    private val range = Regex("""(?U)\d\s*[~～\-－]\s*\d""")
    private val alternative = Regex("""(?U)또는\s*(?:1\s*일|\d)""")

    fun parse(text: String?): IntakeMethod? {
        if (text == null) return null
        val m = pattern.find(text) ?: return null
        // 범위는 읽은 부분 안에서만 본다. 뒤쪽 "물 200~250ml에 타서"는 섭취량이 아니다.
        if (range.containsMatchIn(m.value) || alternative.containsMatchIn(text)) return null
        val count = m.groups["units"]!!.value.let { koreanNumbers[it]?.toBigDecimal() ?: number(it) }
        val countUnit = m.groups["unit"]!!.value
        val mult = m.groups["mult"]?.value?.toBigDecimal()
        val (amount, amountUnit) = m.groups["amt"]?.let { normalize(number(it.value), m.groups["amtunit"]!!.value) }
            ?: normalize(count.multiply(mult ?: BigDecimal.ONE), countUnit)
            ?: (null to null)
        return IntakeMethod(
            timesPerDay = m.groups["times"]?.value?.toInt() ?: 1,
            unitsPerTime = mult ?: count,
            unit = m.groups["multunit"]?.value ?: countUnit,
            amountPerTime = amount,
            amountUnit = amountUnit,
        )
    }

    // 무게는 mg, 부피는 ml로 바꾼다. 개수 단위(정·포 등)나 모르는 단위면 null.
    fun normalize(value: BigDecimal, unit: String): Pair<BigDecimal, String>? = when (unit.lowercase()) {
        "mg", "㎎" -> value to "mg"
        "g" -> value.multiply(THOUSAND) to "mg"
        "ml", "㎖" -> value to "ml"
        "l" -> value.multiply(THOUSAND) to "ml"
        else -> null
    }

    private val THOUSAND = BigDecimal(1000)

    private fun number(s: String) = BigDecimal(s.replace(",", ""))
}
