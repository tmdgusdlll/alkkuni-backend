package com.alkkuni.core.product

/**
 * 기능성 원문(I0030 PRIMARY_FNCLTY)에서 판정하는 기능성 카테고리. 한 제품이 여러 카테고리에 들 수 있다.
 * 원문은 고시 문구를 이어 붙인 것이라([원료]·①②·가운뎃점), 문구 단위로 쪼갠 뒤 어간을 찾는다. 원문 전체에서 찾으면 "간"이 "간혹"에 걸린다.
 * - 영양소 기능 문구(비타민·무기질의 "…에 필요", 칼슘·비타민 D의 "골다공증 발생 위험 감소")는 쓰지 않는다.
 *   쓰면 종합비타민이 눈·면역·항산화·뼈에 모두 들어간다. 비타민·무기질은 성분으로 고른다.
 * - 어간(stems) 중 하나가 있고 제외 어간(excludes)이 없으면 해당한다.
 * 로컬(2026-11-13) 원료 제외 43,449개 중 31,140개에 하나 이상. 조사·결정은 PROGRESS.md 작업 로그(2026-11-13).
 * 규칙을 바꾸면 batch의 alkkuni.job=link-categories로 기존 제품을 다시 판정한다.
 * DB(product_category.category)에는 enum 이름을 저장한다. 이름을 바꾸면 기존 행이 깨진다.
 */
enum class FunctionalCategory(
    val label: String,
    private val stems: List<String>,
    private val excludes: List<String> = emptyList(),
) {
    EYE("눈", listOf("눈건강", "눈의피로", "황반")),
    LIVER("간", listOf("간건강", "간기능", "간손상", "간을보호")),
    BLOOD_SUGAR("혈당", listOf("혈당")),
    BLOOD_PRESSURE("혈압", listOf("혈압")),
    CHOLESTEROL("콜레스테롤", listOf("콜레스테롤")),
    TRIGLYCERIDE("중성지질", listOf("중성지질", "중성지방")),
    CIRCULATION("혈행", listOf("혈행", "혈액흐름", "혈소판응집")),
    BODY_FAT("체지방", listOf("체지방")),
    GUT("장", listOf("배변", "유산균", "유해균", "유익균", "장건강", "장내"), listOf("요로", "질내")),
    STOMACH("위", listOf("위건강", "위점막", "헬리코박터", "위불편감")),
    IMMUNE("면역", listOf("면역력", "면역기능"), listOf("면역과민")),
    FATIGUE("피로", listOf("피로"), listOf("눈의피로")),
    ANTIOXIDANT("항산화", listOf("항산화", "활성산소")),
    MEMORY("기억력·인지", listOf("기억력", "인지력", "인지기능")),
    RELAX_SLEEP("긴장·수면", listOf("긴장완화", "수면")),
    BONE("뼈", listOf("뼈건강", "칼슘흡수")),
    JOINT("관절", listOf("관절", "연골")),
    MUSCLE("근력·운동", listOf("근력", "지구력", "운동수행", "운동능력")),
    SKIN("피부", listOf("피부")),
    HAIR("모발", listOf("모발")),
    ORAL("구강", listOf("구강", "잇몸", "치주", "충치")),
    MEN("남성 건강", listOf("전립선", "갱년기남성", "남성의갱년기", "남성건강")),
    WOMEN("여성 건강", listOf("갱년기여성", "여성의갱년기", "여성건강", "월경", "요로", "질건강", "질내")),
    GROWTH("키 성장", listOf("키성장")),
    ;

    private fun matches(claim: String) = stems.any(claim::contains) && excludes.none(claim::contains)

    companion object {
        // [원료명] 머리표와 ①·⑴·(가)·1) 번호. 문구 경계로 바꾼다.
        private val header = Regex("""\[[^\]]*\]""")
        private val numbering = Regex("""[①-⑳⑴-⒇]|\([가-하\d]\)|(?<![\d.])\d+[).]""")

        // 영문 병기("(영문) May help …")는 지운다. 남겨 두면 한 문구에 섞여 끝말 판정이 틀린다.
        // 두 단어 이상인 영문만 지운다. "(FGO)"·"B1" 같은 한 단어를 지우면 그 자리가 문구 경계가 돼 원료명이 따로 떨어진다.
        private val english = Regex("""\(영문\)[^가-힣]*|[A-Za-z]+(?:[\s,.()-]+[A-Za-z]+)+[^가-힣]*""")

        // 문구 구분자. 가운뎃점이 원문마다 다른 문자다(U+0387은 U+00B7로 정규화돼 보이므로 이스케이프로 둔다).
        // 하이픈은 앞뒤가 띄어진 것만("… 있음 - 비타민 E : …"). "제2024-26호"·"N-아세틸"은 나누지 않는다.
        private val separator = Regex("""[\n\r·․•ㆍ･‧∙▪\u0387;,/]|\.(?!\d)|\s[-–]\s""")
        private val space = Regex("""\s+""")

        // "원료명 : 문구"에서 콜론 앞이 문구가 아니면(도움이 없으면) 원료명으로 보고 지운다.
        // 원료명의 어간이 걸리지 않게 한다(루테인지아잔틴 → 인지, 유산균발효굴추출물 → 장). 로컬에서 39개가 이것으로 걸렸다.
        private val colonPrefix = Regex("""^[^:：]*[:：]""")

        // 영양소 기능 문구. 끝의 따옴표·괄호는 무시한다.
        private val nutrient = Regex("""(필요함?|구성성분|관여)[^가-힣]*$|골다공증""")

        fun of(functionality: String?): Set<FunctionalCategory> {
            if (functionality.isNullOrBlank()) return emptySet()
            val text = functionality
                .replace(header, "\n")
                .replace(numbering, "\n")
                .replace(english, "\n")
            return text.split(separator)
                .map { it.replace(space, "").stripMaterialName() }
                .filterNot { it.isEmpty() || nutrient.containsMatchIn(it) }
                .flatMapTo(sortedSetOf()) { claim -> entries.filter { it.matches(claim) } }
        }

        private fun String.stripMaterialName(): String {
            val prefix = colonPrefix.find(this)?.value ?: return this
            return if ("도움" in prefix) this else removePrefix(prefix)
        }
    }
}
