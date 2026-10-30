package com.alkkuni.core.parser

import java.math.BigDecimal

// 기준규격(STDR_STND) 한 줄에서 뽑은 성분 함량. 표기는 원문 그대로 두고, 단위·성분명 통일은 정규화 단계에서 한다.
data class ParsedAmount(
    val rawName: String,
    val amount: BigDecimal,
    val unit: String,
    val basisAmount: BigDecimal,
    val basisUnit: String,
)

// 기준규격 문장에서 "함량 단위/분모 단위" 모양을 찾아 성분별 함량을 뽑는다.
// "표시량" 글자나 괄호 모양(( ) [ ] { } ｛ ｝)에 기대지 않는다. 표기가 제품마다 달라서다.
// 정확도는 골든 테스트(IngredientAmountParserGoldenTest)로 지킨다.
object IngredientAmountParser {

    // (?U): 원문의 전각 공백·숫자도 \s·\d로 잡는다.
    // 쉼표는 세 자리 구분일 때만 받는다. "1,5g", "2,25g"은 소수점인지 오타인지 알 수 없어 추측하지 않고 버린다.
    private const val NUMBER = """(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?"""

    // 단위는 숫자·괄호·한글·마침표로 시작하지 않고 쉼표를 담지 않는다. 단, "억 CFU", "100,000,000개"처럼 억·개는 받는다.
    // 마침표로 시작하지 않아야 "1. MSM 1.5g"의 항목 번호나 "10.000.000.000cfu" 같은 표기를 함량으로 읽지 않는다.
    private const val UNIT = """(?:[억개]|[^\d\s/()\[\]{}｛｝:：,.~～%가-힣])[^/()\[\]{}｛｝:：,~～%가-힣]{0,15}?"""
    private const val BASIS_UNIT = """[A-Za-zμµ㎍㎎㎖]+"""

    // 1.4mg/7g, 100,000,000(1억) CFU / 2 g, 5,000,000,000 CFU/g(분모 생략), 50mgNE[800mg](분모가 대괄호), 3 ㎍, 120 IU/2 g(IU 병기는 버림)
    // "823.2mg~1,029mg/6,000mg"처럼 함량이 범위면 아래값을 함량으로 쓴다(보장되는 최소량). 위쪽 값은 건너뛴다.
    // "80~120mg/g"처럼 물결표 뒤 숫자는 범위의 위쪽 값이라 함량이 아니다.
    private val amountPattern = Regex(
        """(?U)(?<![A-Za-z\d.,+])(?<![~～]\s{0,3})(?<amt>$NUMBER)(?:\s*\(\s*$NUMBER\s*[만억조]\s*\))?\s*(?<unit>$UNIT)""" +
            """(?:\s*,\s*$NUMBER\s*IU)?(?:\s*[~～]\s*$NUMBER\s*$UNIT)?\s*""" +
            """(?:/\s*(?<den>$NUMBER)?\s*(?<denunit>$BASIS_UNIT)|\[\s*(?<den2>$NUMBER)\s*(?<denunit2>$BASIS_UNIT)\s*])""",
    )

    // 성분명은 콜론, " / ", "표시량" 중 먼저 나오는 곳 앞까지다.
    private val nameEnd = Regex("""(?U)[:：]|\s/\s|표시량""")

    // "가르시니아캄보지아 추출물 : 총(-)-Hydroxycitric acid로서 750mg/7g"은 지표성분 이름을 성분명으로 쓴다.
    private val indicator = Regex("""(?U)[:：]\s*(\S.*?)로서\s*$""")
    private val upperLimit = Regex("""(?U)^\s*\)?\s*이하""")
    // 세균수·대장균군 기준(3,000cfu/㎖)과 총다당체의 "기타원료 유래 다당체" 참고값은 함량이 아니다.
    private val notAmountName = Regex("""세균|대장균|기타.*유래""")
    // "원료성 제품 - 표시량(…)", "원료-표시량(…)" 줄. 원료 값은 소비자 제품 비교에 쓰지 않는다.
    private val rawMaterial = Regex("""원료성|원료\s*-""")
    // "최종제품 : 7mg/g", "2.최종제품:진세노사이드 … 표시량(…)"에서 표시를 지우고 나머지로 성분명을 찾는다.
    private val finalProduct = Regex("""(?U)\*?최종제품\s*[:：-]?\s*""")
    // 한 줄에 함량이 여럿이면 두 번째부터는 마지막 괄호·쉼표·빗금 뒤가 성분명이다. "(유산균 4,000,000,000 cfu/g, 비피더스균 …"
    private val nameSeparator = Regex("""[(),\[\]/]""")
    private val linePrefix =Regex("""(?U)^\s*(?:\d+\s*[.)]|[①-⑳]|[⑴-⒇]|\(\s*[가-힣\d]\s*\)|[가-힣]\))\s*""")

    // 줄바꿈이 없는 기준규격도 항목 번호(1. / 2) / ① / ⑴ / (가)) 앞에서 나눈다.
    // "1.0 mg/kg"처럼 소수점 뒤에 공백이 없는 숫자는 번호로 보지 않는다. 단, "음성, 3.아연"처럼 쉼표 뒤 번호는 붙어 있어도 나눈다.
    private val segmentSplit =
        Regex("""(?U)\s+(?=\d{1,2}\s*[.)]\s|[①-⑳]|[⑴-⒇]|\(\s*[가-힣]\s*\)\s)|,\s*(?=\d{1,2}\.[^\d\s])""")
    private val spaces = Regex("""(?U)\s+""")
    private val lineBreak = Regex("\r?\n")

    fun parse(standardText: String): List<ParsedAmount> {
        val result = mutableListOf<ParsedAmount>()
        // "2. 진세노사이드 합 / 1) 원료성제품 : … / 2) 최종제품 : …"처럼 성분명이 위 줄에만 있는 경우에 쓴다.
        var heading = ""
        for (segment in segments(standardText).map { finalProduct.replace(it, "") }) {
            if (!rawMaterial.containsMatchIn(segment)) result += parseSegment(segment, heading)
            cleanName(segment.cutName()).let { if (it.isNotEmpty() && !rawMaterial.containsMatchIn(it)) heading = it }
        }
        return result
    }

    private fun segments(text: String): List<String> =
        text.split(lineBreak)
            .flatMap { it.split(segmentSplit) }
            .filter { it.isNotBlank() }

    private fun parseSegment(segment: String, heading: String): List<ParsedAmount> {
        var prevEnd = 0
        return amountPattern.findAll(segment).mapNotNull { m ->
            val before = segment.substring(prevEnd, m.range.first)
            prevEnd = m.range.last + 1
            val basisUnit = (m.groups["denunit"] ?: m.groups["denunit2"])!!.value
            // 납·카드뮴 같은 오염물질 기준(mg/kg)과 "300mg/2,000mg 이하", "세균수 100cfu/mL이하" 같은 상한은 함량이 아니다.
            if (basisUnit.equals("kg", ignoreCase = true) || upperLimit.containsMatchIn(segment.substring(prevEnd))) {
                return@mapNotNull null
            }
            val first = m.range.first == before.length
            val nameText = if (first) before else before.split(nameSeparator).last()
            val name = cleanName(indicator.find(nameText)?.groupValues?.get(1) ?: nameText.cutName())
            // 두 번째부터 이름이 없으면 "(2000IU/500mg)", "(100,000,000,000 cfu/g 이상)"처럼 앞 성분을 다시 적은 것이다.
            if (notAmountName.containsMatchIn(name) || (!first && name.isEmpty())) return@mapNotNull null
            ParsedAmount(
                rawName = name.ifEmpty { heading },
                amount = number(m.groups["amt"]!!.value),
                unit = m.groups["unit"]!!.value.trim(),
                // 분모에 단위만 있으면(4mg/g) 단위 1개당 함량으로 본다.
                basisAmount = (m.groups["den"] ?: m.groups["den2"])?.value?.let(::number) ?: BigDecimal.ONE,
                basisUnit = basisUnit,
            )
        }.toList()
    }

    private fun String.cutName(): String = nameEnd.find(this)?.let { substring(0, it.range.first) } ?: this

    private fun cleanName(name: String): String =
        linePrefix.replaceFirst(name, "")
            .trim(' ', '-', ':', '：', '*')
            // "(-)-Hydroxycitric acid"의 여는 괄호는 성분명이다.
            .let { if (it.startsWith("(-)")) it else it.trim('(', ' ') }
            .replace(spaces, " ")

    private fun number(s: String) = BigDecimal(s.replace(",", ""))
}
