package com.alkkuni.core.ingredient

// 성분명 원문(raw_name)을 사전 조회 키로 바꾼다. 원문은 그대로 두고 키로만 쓴다.
// 사람이 판단할 필요 없는 표기 차이(번호, 감싼 괄호, (%)·함량 같은 꼬리, 띄어쓰기, 대소문자)만 지운다.
// 동의어("셀렌"="셀레늄")는 여기서 합치지 않고 사전(ingredient_alias)이 묶는다.
object IngredientName {

    // "14) ", "1-2) ", "1-14) ", ". ", "㉑ ", "ㆍ"
    private val leadingNumber = Regex("""^\s*(?:\d+(?:-\d+)?\s*\)|[.ㆍ]|[①-⑳㉑-㉟㊱-㊿])\s*""")
    // 뒤에 붙은 표시: "(%)", " 함량", "(mg/g)", "(정제)", "으로서"·"로서". 함량(%)·함량으로서처럼 겹쳐도 반복해서 지운다.
    // "비타민B6으로서"는 "비타민 B6으로 환산한 양"이라 같은 성분이다. 괄호 안 환산 표시("실리마린(SILYBIN으로서)")는 ")"로 끝나 남는다.
    private val trailing = Regex("""\s*(?:\(\s*%\s*\)|함량|\(\s*mg\s*/\s*g\s*\)|\(\s*정제\s*\)|으로서|로서)\s*$""")
    private val wrapped = Regex("""^\[(.*)]$""")
    private val spaces = Regex("""\s+""")

    fun normalize(rawName: String): String {
        var s = leadingNumber.replace(rawName.trim(), "")
        wrapped.find(s)?.let { s = it.groupValues[1] }
        while (true) {
            val next = trailing.replace(s, "")
            if (next == s) break
            s = next
        }
        return s.replace(spaces, "").uppercase()
    }
}
