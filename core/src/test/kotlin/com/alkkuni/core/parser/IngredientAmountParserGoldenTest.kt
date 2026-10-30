package com.alkkuni.core.parser

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

// 스파이크에서 사람이 검수한 200건을 정답으로 두고, 맞히던 제품이 틀리게 되는 변경을 막는다.
// 정답 출처: golden/expected.tsv (scope=LLM인 제품은 규칙 파서 대상이 아니라 채점에서 뺀다)
class IngredientAmountParserGoldenTest {

    // 원문 표기 오류라 규칙으로 맞히지 않고 검수로 보내는 제품.
    // 분모 '1.600 mg'(1,600), '4, 100mg'(4,100), 소수점 쉼표 '5,5mg'·분모 '1.500mg'(1,500).
    // 이 목록 밖의 제품이 틀리거나, 이 제품이 맞게 되면 실패한다. 개수만 보면 하나가 고쳐지고 다른 하나가 깨져도 통과해서다.
    private val knownFailures = setOf("200400170211728", "20040020071505", "20230029284258")

    @Test
    fun `골든 데이터에서 틀리는 제품은 알려진 원문 표기 오류뿐이다`() {
        val inputs = readTsv("golden/inputs.tsv").associate { it[0] to unescape(it[1]) }
        val expected = readTsv("golden/expected.tsv").groupBy { it[0] }
        val ruleTargets = expected.filterValues { rows -> rows.none { it[1] == "LLM" } }

        val failures = ruleTargets.mapNotNull { (reportNo, rows) ->
            val want = rows.map { Amount(it[2], BigDecimal(it[3]), it[4], BigDecimal(it[5]), it[6]) }
            val got = IngredientAmountParser.parse(inputs.getValue(reportNo)).map {
                Amount(it.rawName, it.amount, it.unit, it.basisAmount, it.basisUnit)
            }
            if (sorted(want) == sorted(got)) null else reportNo to "$reportNo\n  기대: $want\n  실제: $got"
        }.toMap()

        assertEquals(
            knownFailures,
            failures.keys,
            "맞힌 제품 ${ruleTargets.size - failures.size}/${ruleTargets.size}\n" + failures.values.joinToString("\n"),
        )
    }

    // 비교용 값. 공백 차이는 무시하고, 숫자는 0.5와 0.50을 같게 본다.
    private data class Amount(val name: String, val amount: String, val unit: String, val basis: String, val basisUnit: String) {
        constructor(name: String, amount: BigDecimal, unit: String, basis: BigDecimal, basisUnit: String) : this(
            name.replace(Regex("\\s+"), " ").trim(),
            amount.stripTrailingZeros().toPlainString(),
            unit.trim(),
            basis.stripTrailingZeros().toPlainString(),
            basisUnit.trim(),
        )
    }

    private fun sorted(amounts: List<Amount>) = amounts.sortedBy { it.toString() }

    private fun readTsv(path: String): List<List<String>> =
        javaClass.classLoader.getResource(path)!!.readText()
            .lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split('\t') }

    private fun unescape(s: String): String =
        Regex("""\\([\\nt])""").replace(s) {
            when (it.groupValues[1]) {
                "n" -> "\n"
                "t" -> "\t"
                else -> "\\"
            }
        }
}
