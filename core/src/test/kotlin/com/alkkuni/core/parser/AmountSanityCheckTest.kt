package com.alkkuni.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals

// 전체 품목(I0030 46,057건)에서 걸린 실제 원문으로 만든 사례.
class AmountSanityCheckTest {

    private fun issues(text: String) = AmountSanityCheck.issues(IngredientAmountParser.parse(text).amounts)

    @Test
    fun `함량이 분모보다 크면 잡는다`() {
        // 2004002000338 (분모 오기), 200400151071335 (㎍을 g으로)
        assertEquals(
            listOf("함량이 분모보다 큼: 마그네슘 850mg/800mg"),
            issues("2. 마그네슘 : 표시량(850mg/800mg)의 80~150%\n3. 철 : 표시량(7.8mg/800mg)의 80~150%"),
        )
        assertEquals(
            listOf("함량이 분모보다 큼: 비타민B12 2.4g/200mg"),
            issues("3. 비타민B12 : 표시량(2.4g/200mg)의 80~180%"),
        )
    }

    @Test
    fun `같은 제품에서 분모가 1000배 차이 나면 잡는다`() {
        // 200400170211728 (마침표 오기), 2016001213761 (mg을 g으로)
        assertEquals(
            listOf("분모 불일치: 1.6mg / 1600mg"),
            issues("②마그네슘 : 표시량(94.5 mg/1,600 mg)의 80~150%\n③비타민B6 : 표시량(0.45 mg/1.600 mg)의 80~150%"),
        )
        assertEquals(
            listOf("분모 불일치: 500mg / 500g"),
            issues("② 비타민C : 표시량[100mg/500mg]의 80~150%\n③ 아연  : 표시량[8.5mg/500g]의 80~150%"),
        )
    }

    @Test
    fun `분모가 1로 읽히는 마침표 표기와 환산 기준이 붙은 단위도 본다`() {
        // 200400200073301 ("1.000mg"은 1로 읽힌다), 2015001000276 ("gμ"는 g으로 본다)
        assertEquals(
            listOf("분모 불일치: 1mg / 1000mg"),
            issues("3) 비타민B1 : (표시량 : 0.45mg / 1,000mg)\n4) 비타민B2 : (표시량 : 0.4mg / 1.000mg)"),
        )
        assertEquals(
            listOf("함량이 분모보다 큼: 비타민E 850mg α-TE/800mg"),
            issues("3. 비타민E : 표시량(850mg α-TE/800mg)의 80~150%"),
        )
    }

    @Test
    fun `정상 제품과 질량이 아닌 단위는 잡지 않는다`() {
        // 20040016034114 (소수점 3자리 함량 0.504mg 류 포함), 농도 표기, IU·CFU
        assertEquals(
            emptyList(),
            issues("2. 비타민 B1 함량 : 1.05mg/1,000mg의 80~180%\n5. 비오틴 함량 : 105㎍/1,000mg의 80~180%\n9. 셀레늄 : 0.504mg/1,000mg"),
        )
        assertEquals(emptyList(), issues("2. 총 다당체 : 4mg/g 이상\n3. 베타글루칸 : 표시량(300mg/2,000mg)"))
        assertEquals(emptyList(), issues("2. 프로바이오틱스 수 : 5,000,000,000 CFU/2g 이상\n3. 비타민D : 표시량(400 IU/1g)"))
    }
}
