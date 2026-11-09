package com.alkkuni.core.ingredient

import com.alkkuni.core.ingredient.entity.Ingredient
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IngredientTest {

    // 엽산: 상한 1.7mg DFE(보충제 엽산 1mg)
    private val folate = Ingredient("엽산", referenceAmount = BigDecimal("0.4"), referenceUnit = "mg DFE", upperLimit = BigDecimal("1.7"))

    @Test
    fun `상한의 2배를 넘으면 초과다`() {
        // 로컬 데이터의 실제 오류 값(엽산 120mg DFE)
        assertTrue(folate.exceedsUpperLimit(BigDecimal("120"), "mg DFE"))
    }

    @Test
    fun `상한은 넘지만 2배 이하면 초과가 아니다`() {
        // 고함량 정상 제품 구간. 상한만 넘어도 잡으면 790개 제품이 걸린다
        assertFalse(folate.exceedsUpperLimit(BigDecimal("2.0"), "mg DFE"))
        assertFalse(folate.exceedsUpperLimit(BigDecimal("3.4"), "mg DFE"))
    }

    @Test
    fun `단위가 다르면 비교하지 않는다`() {
        // 엽산 mg 행(DFE 표기 없음)은 기준 단위가 달라 판정하지 않는다
        assertFalse(folate.exceedsUpperLimit(BigDecimal("120"), "mg"))
    }

    @Test
    fun `상한이나 1일 함량이 없으면 초과가 아니다`() {
        val noLimit = Ingredient("비타민 B1", referenceAmount = BigDecimal("1.2"), referenceUnit = "mg")
        assertFalse(noLimit.exceedsUpperLimit(BigDecimal("1000"), "mg"))
        assertFalse(folate.exceedsUpperLimit(null, null))
    }
}
