package com.alkkuni.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals

// 전체 품목(I0030 46,057건)을 돌려 찾은 오추출 사례. 원문은 실제 기준규격에서 해당 줄만 가져왔다.
class IngredientAmountParserTest {

    private fun parse(text: String) =
        IngredientAmountParser.parse(text).map { listOf(it.rawName, it.amount.toPlainString(), it.unit, it.basisAmount.toPlainString(), it.basisUnit) }

    @Test
    fun `IU 병기는 단위에 넣지 않는다`() {
        // 20120017002220
        assertEquals(
            listOf(listOf("비타민D", "3", "㎍", "2", "g")),
            parse("4. 비타민D : 표시량(3 ㎍, 120 IU/ 2 g)의 80~180%"),
        )
    }

    @Test
    fun `항목 번호를 함량으로 읽지 않는다`() {
        // 20040015104143
        assertEquals(
            listOf(listOf("MSM", "1.5", "g", "3200", "mg")),
            parse("1. MSM 1.5g/3,200mg (80~120%)"),
        )
    }

    @Test
    fun `천 단위가 아닌 쉼표와 마침표 숫자는 추측하지 않고 버린다`() {
        // 20040020046312 (2,25g), 20040020020363 (10.000.000.000cfu)
        assertEquals(emptyList(), parse("3. 식이섬유 : 표시량(1.5g/2,25g)의 80%~120%"))
        assertEquals(emptyList(), parse("②프로바이오틱스: 10.000.000.000cfu/450mg이상"))
    }

    @Test
    fun `범위의 위쪽 값은 함량이 아니다`() {
        // 20040020020259
        assertEquals(
            listOf(listOf("베타클루칸", "50", "mg", "2.4", "g")),
            parse("베타클루칸 :  표시량(50mg/2.4g)의 80~120mg/g이어야 한다."),
        )
    }

    @Test
    fun `기타원료 유래 다당체는 함량이 아니다`() {
        // 20040020004520
        assertEquals(
            listOf(listOf("총 다당체", "250", "mg", "20", "g")),
            parse("2) 총 다당체 : 250mg/20g (표시량의 80% 이상) (기타유래다당체 : 687.40mg/20g)"),
        )
        // 2013001515195
        assertEquals(
            listOf(listOf("총 다당체", "120", "mg", "100", "mL")),
            parse("2. 총 다당체 : 표시량(120 mg/100 mL)의 80% 이상 / 기타유래다당체 500 mg/100 mL"),
        )
    }

    @Test
    fun `한 줄의 두 번째 성분명은 앞 성분의 꼬리를 포함하지 않는다`() {
        // 2009001504710
        assertEquals(
            listOf("프로바이오틱스 수", "유산균", "비피더스균"),
            parse("2)프로바이오틱스 수: 5,000,000,000 cfu/g 이상\n (유산균 4,000,000,000 cfu/g, 비피더스균 1,000,000,000 cfu/g) 이상").map { it[0] },
        )
    }

    @Test
    fun `(-)로 시작하는 성분명은 괄호를 지우지 않는다`() {
        // 20040020020572
        assertEquals(
            listOf("(-)-Hydroxycitric acid", "카테킨"),
            parse("② (-)-Hydroxycitric acid : 750mg/2000mg (표시량의 80-120%)\n③ 카테킨 : 300mg/2000mg (표시량의 80-120%)").map { it[0] },
        )
    }

    @Test
    fun `세균수는 함량이 아니다`() {
        // 2011002012122
        assertEquals(emptyList(), parse("3.세균수:3,000cfu/㎖"))
    }

    @Test
    fun `성분명이 없는 최종제품 줄은 성상이 아니라 위 성분명을 쓴다`() {
        // 2011002000115, 201900102972, 2004001801152
        val heading = listOf("진세노사드Rg1+Rb1+Rg3의 합(%)", "7", "mg", "1", "g")
        assertEquals(
            listOf(heading),
            parse("(1)성상 : 암갈색의 액상\n(2)진세노사드Rg1+Rb1+Rg3의 합(%) : 원료성 제품 - 표시량(10mg/g)이상\n최종제품 - 표시량(7mg/g)의 80% 이상"),
        )
        assertEquals(
            listOf(listOf("진세노사이드 Rg1, Rb1 및 Rg3의 합", "7", "mg", "1", "g")),
            parse("1. 성상 : 암갈색의 점조성 액체\n2. 진세노사이드 Rg1, Rb1 및 Rg3의 합 : 원료-표시량 (7mg/g) 이상\n최종제품-표시량 (7mg/g)의 80%이상"),
        )
        assertEquals(
            listOf(listOf("진세노사이드 Rb1, Rg1 및 Rg3 의 합", "7", "mg", "10", "ml")),
            parse("1.성상:흑갈색의 액상의 제품\n2.최종제품:진세노사이드 Rb1, Rg1 및 Rg3 의 합 표시량(7mg/10ml)의 80%이상."),
        )
    }


    @Test
    fun `같은 성분을 다시 적은 괄호 표기는 버린다`() {
        // 20110020099212, 2017278201618
        assertEquals(
            listOf(listOf("비타민 D", "50", "μg", "500", "mg")),
            parse("① 성상 : 노랑 하양색의 경질 캡슐\n② 비타민 D : 표시량(50μg/500mg)(2000IU/500mg)의 80~180%"),
        )
        assertEquals(
            listOf(listOf("프로바이오틱스 수", "1.0", "x 10^11 cfu", "1", "g")),
            parse("1. 성상고유의 색택과 향미를 가진 분말\n2. 프로바이오틱스 수 : 1.0 x 10^11 cfu/g 이상(100,000,000,000 cfu/g 이상)"),
        )
    }

    @Test
    fun `지수 표기의 지수를 함량으로 읽지 않는다`() {
        // 20160006007101
        assertEquals(emptyList(), parse("2. 프로바이오틱스 수 : 표시량(5.0.E+09 CFU/2 g) 이상"))
    }

    @Test
    fun `함량이 범위로 적혀 있으면 아래값을 쓴다`() {
        // 201900043736, 201000190041193
        assertEquals(
            listOf(listOf("L-주석산", "823.2", "mg", "6000", "mg")),
            parse("③ L-주석산 : 823.2mg~1,029mg/6,000mg "),
        )
        assertEquals(
            listOf(listOf("L-주석산", "823.53040", "mg", "10000", "mg")),
            parse("4) L-주석산: 823.53040mg~1029.413mg/10,000mg"),
        )
    }
}
