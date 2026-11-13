package com.alkkuni.core.product

import com.alkkuni.core.product.FunctionalCategory.ANTIOXIDANT
import com.alkkuni.core.product.FunctionalCategory.BODY_FAT
import com.alkkuni.core.product.FunctionalCategory.BONE
import com.alkkuni.core.product.FunctionalCategory.CIRCULATION
import com.alkkuni.core.product.FunctionalCategory.EYE
import com.alkkuni.core.product.FunctionalCategory.FATIGUE
import com.alkkuni.core.product.FunctionalCategory.GUT
import com.alkkuni.core.product.FunctionalCategory.IMMUNE
import com.alkkuni.core.product.FunctionalCategory.LIVER
import com.alkkuni.core.product.FunctionalCategory.MEMORY
import com.alkkuni.core.product.FunctionalCategory.MEN
import com.alkkuni.core.product.FunctionalCategory.SKIN
import com.alkkuni.core.product.FunctionalCategory.WOMEN
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// 표기 예는 로컬 실제 기능성 원문(2026-11-13)에서 가져왔다.
class FunctionalCategoryTest {

    @Test
    fun `고시 문구를 쪼개 카테고리를 찾는다`() {
        val cases = mapOf(
            "[프로바이오틱스] ①유산균 증식 및 유해균 억제에 도움을 줄 수 있음 ②배변활동 원활에 도움을 줄 수 있음" to setOf(GUT),
            "면역력 증진·피로개선·혈소판 응집억제를 통한 혈액흐름·기억력 개선·항산화에 도움을 줄 수 있음" to
                setOf(IMMUNE, FATIGUE, CIRCULATION, MEMORY, ANTIOXIDANT),
            "(국문) 체지방 감소에 도움을 줄 수 있음 (영문) May help to reduce body fat" to setOf(BODY_FAT),
            "노화로 인해 감소될 수 있는 황반색소밀도를 유지하여 눈 건강에 도움을 줄 수 있음" to setOf(EYE),
            "알콜성 손상으로부터 간을 보호하는데 도움을 줄 수 있음(생리활성기능2등급)" to setOf(LIVER),
            "자외선에 의한 피부손상으로부터 피부건강을 유지하는데 도움을 줄 수 있음" to setOf(SKIN),
            "체내 칼슘 흡수 촉진에 도움을 줄 수 있음" to setOf(BONE),
            "[홍삼] ①면역증진ㆍ피로개선에 도움을 줄 수 있음" to setOf(IMMUNE, FATIGUE),
            "인터루킨4 감소를 통한 면역조절에 도움을 줄 수 있음" to setOf(IMMUNE),
            // U+0387(그리스 문자 가운뎃점)도 구분자다
            "식후 혈당상승 억제·배변활동 원활에 도움을 줄 수 있음" to setOf(FunctionalCategory.BLOOD_SUGAR, GUT),
        )
        assertEquals(cases, cases.mapValues { (text, _) -> FunctionalCategory.of(text) })
    }

    @Test
    fun `영양소 기능 문구는 카테고리에 쓰지 않는다`() {
        // 종합비타민이 눈(비타민 A)·면역(아연)·항산화(비타민 E)·뼈(비타민 D)에 모두 들어가지 않게 한다.
        val texts = listOf(
            "[비타민A] ①어두운 곳에서 시각 적응을 위해 필요 ②피부와 점막을 형성하고 기능을 유지하는데 필요",
            "아연: 정상적인 면역기능에 필요",
            "비타민E: 항산화 작용을 하여 유해산소로부터 세포를 보호하는데 필요",
            "①칼슘과 인이 흡수되고 이용되는데 필요②뼈의 형성과 유지에 필요③골다공증발생 위험 감소에 도움을 줌",
            "단백질 및 아미노산 이용에 필요”",
            "",
        )
        assertEquals(texts.map { emptySet<FunctionalCategory>() }, texts.map(FunctionalCategory::of))
        assertEquals(emptySet(), FunctionalCategory.of(null))
    }

    @Test
    fun `어간이 겹치는 문구는 예외로 가른다`() {
        val cases = mapOf(
            "눈의 피로도 개선에 도움을 줄 수 있음" to setOf(EYE), // 피로 아님
            "갱년기 남성의 건강에 도움을 줄 수 있음" to setOf(MEN), // 여성 아님
            "갱년기 여성의 건강에 도움을 줄 수 있음" to setOf(WOMEN),
            "요로의 유해균 흡착억제로 요로건강에 도움을 줄 수 있음" to setOf(WOMEN), // 장 아님
            "질내 유익균 증식 및 유해균 억제에 도움을 줄 수 있음" to setOf(WOMEN),
            "면역과민반응에 의한 피부상태 개선에 도움을 줄 수 있음" to setOf(SKIN), // 면역 아님
            // 콜론 앞 원료명의 어간은 보지 않는다
            "루테인지아잔틴복합추출물 : (국문) 건조한 눈을 개선하여 눈 건강에 도움을 줄 수 있음" to setOf(EYE), // 기억력·인지 아님
            "유산균발효굴추출물(FGO)(제2024-26호) : (국문) 어린이 키성장에 도움을 줄 수 있음" to setOf(FunctionalCategory.GROWTH), // 장 아님
            // 콜론 앞이 문구면 남긴다
            "관절 및 연골건강에 도움을 줄 수 있음 : N-아세틸글루코사민으로서 0.5~1 g" to setOf(FunctionalCategory.JOINT),
            // 띄어 쓴 하이픈 뒤의 영양소 문구가 앞 문구를 영양소 문구로 만들지 않는다
            "건조한 눈을 개선하여 눈 건강에 도움을 줄 수 있음 - 비타민 E : 유해산소로부터 세포를 보호하는데 필요" to setOf(EYE),
        )
        assertEquals(cases, cases.mapValues { (text, _) -> FunctionalCategory.of(text) })
    }
}
