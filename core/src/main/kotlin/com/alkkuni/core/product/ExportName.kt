package com.alkkuni.core.product

/**
 * 제품명 표기로 수출용(국내에서 살 수 없는 제품)인지 판정한다. 식약처 데이터에 수출 여부 칸이 없다.
 * 로컬(2026-11-13) 원료 제외 43,449개 중 2,608개.
 *  1) 이름에 "수출"이 있다(수출용·전량수출용·수출전용·(수출) 등). "수출겸용"(국내도 판매)과 "수출명: …"(국내 제품의 수출 이름)은 뺀다.
 *  2) 괄호 안이 국가명만으로 되어 있다. 예: (태국), (영국, 독일), (태국용). 대부분 애터미 국가별 제품이다.
 *     "(호주산)"처럼 다른 글자가 붙은 원산지 표기는 걸리지 않는다.
 * 규칙을 바꾸면 batch의 alkkuni.job=mark-export로 기존 제품을 다시 판정한다.
 */
object ExportName {
    // 국가 목록은 실제 표기에서 모은 것이다. 새 국가가 나오면 여기에만 더한다.
    private val countries = listOf(
        "미국", "캐나다", "일본", "중국", "대만", "홍콩", "태국", "베트남", "말레이시아", "인도네시아", "싱가폴", "싱가포르",
        "필리핀", "러시아", "멕시코", "브라질", "호주", "뉴질랜드", "영국", "독일", "프랑스", "터키", "튀르키예", "인도", "몽골",
        "카자흐스탄", "캄보디아", "타지키스탄", "우즈베키스탄", "키르기스스탄", "미얀마", "콜롬비아", "페루", "칠레", "두바이",
        "사우디", "이스라엘",
    ).joinToString("|")

    // (?U): \s가 NBSP·전각 공백도 잡게 한다(BrandName과 같음).
    private val exportWord = Regex("""(?U)수출(?!\s*(겸용|명))""")
    private val countryOnlyBracket = Regex("""(?U)[(\[<]\s*(?:$countries)(?:[\s,/·]*(?:$countries))*\s*용?\s*[)\]>]""")

    fun isExportOnly(productName: String): Boolean =
        exportWord.containsMatchIn(productName) || countryOnlyBracket.containsMatchIn(productName)
}
