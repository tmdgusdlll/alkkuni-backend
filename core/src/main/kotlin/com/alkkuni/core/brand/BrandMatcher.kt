package com.alkkuni.core.brand

// 브랜드 표기의 사전 키. 공백을 지우고 대문자로 바꾼다("Coleology Cut" → "COLEOLOGYCUT").
// data/brand-tools의 파이썬 key와 같은 규칙이다. 바꾸면 V13 표기 키도 다시 만든다.
object BrandName {
    private val leadingBracket = Regex("""^[\[(<【]\s*""")
    private val wordEnd = Regex("""[\s()\[\]<>【】,/+&_·]""")
    private val spaces = Regex("""\s+""")

    fun key(s: String): String = s.replace(spaces, "").uppercase()

    // 제품명 전체 키. 앞 괄호("[애터미] …")만 지운다.
    fun nameKey(productName: String): String = key(leadingBracket.replace(productName.trim(), ""))

    // 제품명 첫 단어의 키. 공백이나 괄호·구분 기호 앞까지다.
    fun firstWordKey(productName: String): String =
        key(leadingBracket.replace(productName.trim(), "").split(wordEnd, limit = 2).first())
}

/**
 * 제품명 → 브랜드 id. 제품명이 표기로 시작하면 그 브랜드다. 여러 표기가 맞으면 가장 긴 표기를 쓴다
 * ("종근당(鍾根堂)바이오 …"는 "종근당"이 아니라 "종근당바이오"). wholeWord 표기는 첫 단어가 표기와 같을 때만 맞는다.
 * 이름 중간에 나오는 표기는 보지 않는다(제조사·원료명과 겹친다).
 * brandId가 null인 표기는 검토에서 보류한 것이다. 맞으면 브랜드 없음으로 끝내고 더 짧은 표기로 내려가지 않는다.
 */
class BrandMatcher(aliases: List<Alias>) {
    data class Alias(val key: String, val brandId: Long?, val wholeWord: Boolean)

    private val longestFirst = aliases.sortedByDescending { it.key.length }

    fun find(productName: String): Long? {
        val name = BrandName.nameKey(productName)
        val firstWord = BrandName.firstWordKey(productName)
        return longestFirst.firstOrNull { if (it.wholeWord) firstWord == it.key else name.startsWith(it.key) }?.brandId
    }
}
