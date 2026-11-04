package com.alkkuni.core.parser

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 입력은 I0030 실제 섭취방법 원문(로컬 적재 46,178건에서 고른 변형).
class IntakeMethodParserTest {

    @Test
    fun `1일 횟수, 1회 개수와 단위를 읽는다`() {
        assertParsed("1일 2회, 1회 2캡슐을 물과 함께 섭취하십시오", 2, "2", "캡슐")
        assertParsed("1일 3회, 1회에 1포를 섭취", 3, "1", "포")
        assertParsed("1일 1회 1캡슐을 물과 함께 섭취하십시오.", 1, "1", "캡슐")
        assertParsed("1일 1회, 1회, 1캡슐을 충분한 물과 함께 섭취하십시오.", 1, "1", "캡슐")
        assertParsed("1일 2회, 1회시 10환을 물과 함께 섭취", 2, "10", "환")
        assertParsed("1일 1회 / 1회 1포 섭취", 1, "1", "포")
        assertParsed("1일, 3회 1회 2캡슐을 물과 함께 섭취하십시오", 3, "2", "캡슐")
        assertParsed("1일 1회，1회 1정을 충분한 물과 함께 섭취하십시오.", 1, "1", "정")
        assertParsed("1일 2회, 1회 2 캡슐 섭취", 2, "2", "캡슐")
    }

    @Test
    fun `횟수가 없으면 하루 한 번으로 본다`() {
        assertParsed("1일 2캡슐을 물과 함께 섭취.", 1, "2", "캡슐")
    }

    @Test
    fun `한글 수사로 적은 개수를 읽는다`() {
        assertParsed("1일3회 1회에 한포(10g)를 그대로 섭취한다", 3, "1", "포", "10000", "mg")
        assertParsed("1일 3회, 1회 한 스푼(5g)을 그대로 섭취하십시오.", 3, "1", "스푼", "5000", "mg")
    }

    @Test
    fun `괄호 속 1회 중량을 무게는 mg, 부피는 ml로 읽는다`() {
        assertParsed("1일 2회, 1회 3캡슐(1,500mg)을 충분한 물과 함께", 2, "3", "캡슐", "1500", "mg")
        assertParsed("1일 2회, 1회 2정(1.4 g)을 충분한 물과 함께", 2, "2", "정", "1400", "mg")
        assertParsed("1일 1회 1포(60ml) 섭취하세요", 1, "1", "포", "60", "ml")
        assertParsed("1일 1회, 1회 1Vial(병)(20mL)를 섭취전 흔들어서", 1, "1", "Vial", "20", "ml")
    }

    @Test
    fun `단위가 무게·부피면 괄호가 없어도 1회 중량을 구한다`() {
        assertParsed("1일 1회, 1.5mL(3스포이드)를 구강에 직접섭취", 1, "1.5", "mL", "1.5", "ml")
        assertParsed("1일 2회,1회에 1ml(20방울)를 음용수에 혼합하여", 2, "1", "ml", "1", "ml")
    }

    @Test
    fun `곱셈 표기는 배수를 1회 개수로, 곱한 값을 1회 중량으로 읽는다`() {
        assertParsed("1일 2회, 1회 3g×2포(6g)를 충분한 음용수와 함께", 2, "2", "포", "6000", "mg")
        // 괄호가 없으면 3g × 2를 1회 중량으로 쓴다
        assertParsed("1일 2회, 1회 3g×2포를 충분한 음용수와 함께", 2, "2", "포", "6000", "mg")
    }

    @Test
    fun `괄호가 중량이 아니면 1회 중량은 없다`() {
        assertParsed("1일 1회, 1회 1정(1정당 1000mg)을 물과 함께", 1, "1", "정")
    }

    @Test
    fun `물 양의 범위는 섭취량이 아니라 읽는다`() {
        assertParsed("1일 1회, 1회 1포를 물 200~300 mL에 타서 섭취", 1, "1", "포")
    }

    @Test
    fun `범위·선택 표기와 섭취량이 없는 문장은 읽지 않는다`() {
        assertNull(IntakeMethodParser.parse("1일 1~2회, 1회 1정을 섭취"))
        assertNull(IntakeMethodParser.parse("1일 2회, 1회에 3~4스푼(5g)을 섭취하십시오."))
        assertNull(IntakeMethodParser.parse("1일 2회, 1회 1정을 물과 함께 섭취하십시오. 또는 1일 1회, 1회 2정을 물과 함께"))
        assertNull(IntakeMethodParser.parse("1일 1회, 1회 1포 또는 1앰플(그대로 섭취하거나 희석하여 섭취할 수 있음)"))
        assertNull(IntakeMethodParser.parse("① 건강기능식품 제조 시 일일 섭취량에 적합한 양을 사용"))
        assertNull(IntakeMethodParser.parse("물과 함께 섭취"))
        assertNull(IntakeMethodParser.parse(null))
    }

    @Test
    fun `직접 또는 물과 함께의 또는은 선택 표기가 아니다`() {
        assertParsed("1일 1회, 1회 1포를 직접 또는 물과 함께 섭취하십시오.", 1, "1", "포")
    }

    private fun assertParsed(
        text: String,
        times: Int,
        units: String,
        unit: String,
        amount: String? = null,
        amountUnit: String? = null,
    ) {
        val got = IntakeMethodParser.parse(text) ?: error("못 읽음: $text")
        assertEquals(times, got.timesPerDay, text)
        assertEquals(BigDecimal(units).stripTrailingZeros(), got.unitsPerTime.stripTrailingZeros(), text)
        assertEquals(unit, got.unit, text)
        assertEquals(amount?.let { BigDecimal(it).stripTrailingZeros() }, got.amountPerTime?.stripTrailingZeros(), text)
        assertEquals(amountUnit, got.amountUnit, text)
    }
}
