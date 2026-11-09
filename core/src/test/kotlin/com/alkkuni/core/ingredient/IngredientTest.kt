package com.alkkuni.core.ingredient

import com.alkkuni.core.ingredient.entity.Ingredient
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IngredientTest {

    // 엽산: 상한 1.7mg DFE(보충제 엽산 1mg)
    private val folate = Ingredient("엽산", referenceAmount = BigDecimal("0.4"), referenceUnit = "mg DFE", upperLimit = BigDecimal("1.7"))

    @Test
    fun `상한의 2배를 넘으면 초과다`() {
        assertTrue(folate.exceedsUpperLimit(BigDecimal("120"), "mg DFE"))
    }

    @Test
    fun `상한은 넘지만 2배 이하면 초과가 아니다`() {
        // 고함량 정상 제품 구간. 상한만 넘어도 잡으면 790개 제품이 걸린다
        assertFalse(folate.exceedsUpperLimit(BigDecimal("2.0"), "mg DFE"))
        assertFalse(folate.exceedsUpperLimit(BigDecimal("3.4"), "mg DFE"))
    }

    @Test
    fun `환산 표기만 빠진 mg 행도 비교한다`() {
        // 실제 오류 값 "리아나 생기 플러스" 엽산 120mg은 DFE 표기 없이 mg로 적혀 있다
        assertTrue(folate.exceedsUpperLimit(BigDecimal("120"), "mg"))
        assertFalse(folate.exceedsUpperLimit(BigDecimal("0.8"), "mg"))
    }

    @Test
    fun `다른 환산 표기나 다른 단위는 비교하지 않는다`() {
        val vitaminA = Ingredient("비타민 A", referenceAmount = BigDecimal("0.7"), referenceUnit = "mg RAE", upperLimit = BigDecimal("3.0"))
        assertFalse(vitaminA.exceedsUpperLimit(BigDecimal("420"), "mg RE"))
        assertFalse(folate.exceedsUpperLimit(BigDecimal("120"), "CFU"))
    }

    @Test
    fun `상한이나 1일 함량이 없으면 초과가 아니다`() {
        val noLimit = Ingredient("비타민 B1", referenceAmount = BigDecimal("1.2"), referenceUnit = "mg")
        assertFalse(noLimit.exceedsUpperLimit(BigDecimal("1000"), "mg"))
        assertFalse(folate.exceedsUpperLimit(null, null))
    }

    @Test
    fun `기준값 이상이면 충족이고 퍼센트는 소수점을 버린다`() {
        assertEquals(true, folate.meetsReference(BigDecimal("0.4"), "mg DFE"))
        assertEquals(100, folate.referencePercent(BigDecimal("0.4"), "mg DFE"))
        // 99.75%를 100%로 올리면 미충족인데 100%로 보인다
        assertEquals(false, folate.meetsReference(BigDecimal("0.399"), "mg DFE"))
        assertEquals(99, folate.referencePercent(BigDecimal("0.399"), "mg DFE"))
    }

    @Test
    fun `상한 2배를 넘으면 충족을 판정하지 않는다`() {
        // 오류 의심 값을 false로 주면 30000%가 "기준 미달"로 보인다. 필터는 이 행을 충족 근거로 쓰지 않는다(true가 아님)
        assertNull(folate.meetsReference(BigDecimal("120"), "mg"))
        assertEquals(30000, folate.referencePercent(BigDecimal("120"), "mg"))
        assertEquals(true, folate.meetsReference(BigDecimal("3.4"), "mg DFE"))
    }

    @Test
    fun `기준값이 없거나 단위를 비교할 수 없으면 판정하지 않는다`() {
        val noReference = Ingredient("조단백질")
        assertNull(noReference.meetsReference(BigDecimal("20000"), "mg"))
        assertNull(noReference.referencePercent(BigDecimal("20000"), "mg"))
        assertNull(folate.meetsReference(BigDecimal("1"), "CFU"))
        assertNull(folate.referencePercent(null, null))
    }

    @Test
    fun `퍼센트가 Int 범위를 넘으면 최댓값으로 자른다`() {
        // 파싱 오류로 기준값의 수천만 배가 들어와도 음수로 넘치지 않는다
        val b12 = Ingredient("비타민 B12", referenceAmount = BigDecimal("0.0024"), referenceUnit = "mg")
        assertEquals(Int.MAX_VALUE, b12.referencePercent(BigDecimal("60000"), "mg"))
    }
}
