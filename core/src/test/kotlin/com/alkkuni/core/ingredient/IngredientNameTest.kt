package com.alkkuni.core.ingredient

import kotlin.test.Test
import kotlin.test.assertEquals

// 입력은 로컬 적재(I0030, 2026-11-04) product_ingredient.raw_name의 실제 변형이다.
class IngredientNameTest {

    @Test
    fun `비타민C 표기 변형은 한 키가 된다`() {
        val variants = listOf(
            "비타민C", "비타민 C", "비타민C(%)", "비타민C 함량", "14) 비타민C", "1-2) 비타민C", "비타민 C(%)",
            "비타민C 함량(%)", "비타민c", ".비타민C", "㉒ 비타민 C", "ㆍ비타민C", "비타민C (%)", "비타민C(정제)", "[비타민 C]", "비타민C(mg/g)", "비타민C로서",
        )
        for (v in variants) assertEquals("비타민C", IngredientName.normalize(v), v)
    }

    @Test
    fun `괄호 안 동의어는 지우지 않는다 - 사전에서 묶는다`() {
        assertEquals("셀레늄(또는셀렌)", IngredientName.normalize("셀레늄(또는 셀렌)"))
        assertEquals("엠에스엠(MSM)", IngredientName.normalize("엠에스엠(MSM)"))
        assertEquals("MSM", IngredientName.normalize("msm"))
    }

    @Test
    fun `성분 구분에 필요한 표기는 남긴다`() {
        // 기준 성분이 다르면 다른 키여야 한다
        assertEquals("진세노사이드RG1,RB1및RG3의합", IngredientName.normalize("진세노사이드 Rg1, Rb1 및 Rg3의 합"))
        assertEquals("진세노사이드RG1과RB1의합", IngredientName.normalize("진세노사이드 Rg1과 Rb1의 합"))
        assertEquals("총(-)-HYDROXYCITRICACID", IngredientName.normalize("총(-)-Hydroxycitric acid"))
        assertEquals("대두이소플라본(비배당체로서)", IngredientName.normalize("대두이소플라본(비배당체로서)"))
        assertEquals("플라보놀배당체", IngredientName.normalize("플라보놀 배당체"))
        // 괄호 안 환산 기준은 남긴다. 끝의 "으로서"만 지운다
        assertEquals("실리마린(SILYBIN으로서)", IngredientName.normalize("실리마린(silybin으로서)"))
        assertEquals("비타민B6", IngredientName.normalize("④ 비타민B6으로서"))
        assertEquals("쉬잔드린", IngredientName.normalize("쉬잔드린 함량으로서"))
    }
}
